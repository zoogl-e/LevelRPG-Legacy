package net.zoogle.levelrpg.events;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.zoogle.levelrpg.data.GateRules;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Enforces item gating rules and presents UX to players.
 * - Client: adds tooltip line if locked.
 * - Server: denies right-click item use/place if locked.
 * - Server: denies crafting gated results if locked.
 */
public class GateEvents {

    // Reentrancy guard: avoid handling the same player-slot multiple times within the same tick
    private static final Map<String, Long> LAST_EQUIP_TICK = new HashMap<>();

    // ----------------- Client tooltip -----------------
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        GateRules.ItemGate gate = findGateFor(stack);
        if (gate == null) return;

        // Read client cache via reflection-free approach: rely on name from registry for now
        String skillName = displayNameForSkill(gate.skill);
        int playerLevel = net.zoogle.levelrpg.client.data.ClientProfileCache
                .getSkillsView()
                .getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
        if (playerLevel < gate.minLevel) {
            event.getToolTip().add(
                    Component.translatable("tooltip.levelrpg.locked", gate.minLevel, skillName)
                            .withStyle(ChatFormatting.RED)
            );
        }
    }

    // ----------------- Server denial -----------------
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        GateRules.ItemGate gate = findGateFor(stack);
        if (gate == null) return;

        // Client-side pre-cancel to prevent local auto-equip animations and ghost equips
        if (!(event.getEntity() instanceof ServerPlayer)) {
            if (net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) {
                int lvl = net.zoogle.levelrpg.client.data.ClientProfileCache
                        .getSkillsView()
                        .getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                if (lvl < gate.minLevel) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                }
            }
            return;
        }

        // Server-side enforcement
        ServerPlayer sp = (ServerPlayer) event.getEntity();
        LevelProfile profile = LevelProfile.get(sp);
        LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
        int level = spSkill != null ? spSkill.level : 0;
        if (level < gate.minLevel) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            String skillName = displayNameForSkill(gate.skill);
            sp.displayClientMessage(
                    Component.translatable("msg.levelrpg.locked", gate.minLevel, skillName).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    // ----------------- Server denial (block use/place) -----------------
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        GateRules.ItemGate gate = findGateFor(stack);
        if (gate == null) return;

        // Client-side pre-cancel to prevent local auto-equip or block-use
        if (!(event.getEntity() instanceof ServerPlayer)) {
            if (net.zoogle.levelrpg.client.data.ClientProfileCache.isReady()) {
                int lvl = net.zoogle.levelrpg.client.data.ClientProfileCache
                        .getSkillsView()
                        .getOrDefault(gate.skill, new LevelProfile.SkillProgress()).level;
                if (lvl < gate.minLevel) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                }
            }
            return;
        }

        // Server-side enforcement
        ServerPlayer sp = (ServerPlayer) event.getEntity();
        LevelProfile profile = LevelProfile.get(sp);
        LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
        int level = spSkill != null ? spSkill.level : 0;
        if (level < gate.minLevel) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            String skillName = displayNameForSkill(gate.skill);
            sp.displayClientMessage(
                    Component.translatable("msg.levelrpg.locked", gate.minLevel, skillName).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    // ----------------- Server denial (crafting result) -----------------
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack result = event.getCrafting();
        if (result == null || result.isEmpty()) return;
        GateRules.ItemGate gate = findGateFor(result);
        if (gate == null) return;

        LevelProfile profile = LevelProfile.get(sp);
        LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
        int level = spSkill != null ? spSkill.level : 0;
        if (level < gate.minLevel) {
            // Deny by clearing crafted result and notifying. Resync container to fix ghost outputs.
            result.setCount(0);
            sp.displayClientMessage(
                    Component.translatable("msg.levelrpg.locked", gate.minLevel, displayNameForSkill(gate.skill)).withStyle(ChatFormatting.RED),
                    true
            );
            if (sp.containerMenu != null) {
                sp.containerMenu.broadcastChanges();
            }
        }
    }

    // ----------------- Server denial (break block with gated tool) -----------------
    @SubscribeEvent
    public void onBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return; // server only
        ItemStack held = sp.getMainHandItem();
        if (held == null || held.isEmpty()) return;
        GateRules.ItemGate gate = findGateFor(held);
        if (gate == null) return; // no gate on the held tool

        LevelProfile profile = LevelProfile.get(sp);
        LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
        int level = spSkill != null ? spSkill.level : 0;
        if (level < gate.minLevel) {
            event.setCanceled(true);
            String skillName = displayNameForSkill(gate.skill);
            sp.displayClientMessage(
                    Component.translatable("msg.levelrpg.locked", gate.minLevel, skillName).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    // ----------------- Helpers -----------------
    private static GateRules.ItemGate findGateFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        // Exact item id first
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        GateRules.ItemGate gate = GateRules.itemIdRules().get(id);
        if (gate != null) return gate;
        // Tags next (first matching defined order)
        for (Map.Entry<ResourceLocation, GateRules.ItemGate> e : GateRules.itemTagRules().entrySet()) {
            ResourceLocation tagId = e.getKey();
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            if (stack.is(tag)) return e.getValue();
        }
        return null;
    }

    private static String displayNameForSkill(ResourceLocation skillId) {
        var def = net.zoogle.levelrpg.data.SkillRegistry.get(skillId);
        if (def != null && def.display() != null && def.display().name() != null && !def.display().name().isEmpty()) {
            return def.display().name();
        }
        // Fallback to prettified path
        String s = skillId.getPath().replace('_', ' ');
        return s.isEmpty() ? skillId.toString() : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ----------------- Server denial (attack with gated item) -----------------
    @SubscribeEvent
    public void onAttackEntity(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack stack = sp.getMainHandItem();
        if (stack == null || stack.isEmpty()) return;
        GateRules.ItemGate gate = findGateFor(stack);
        if (gate == null) return;
        LevelProfile profile = LevelProfile.get(sp);
        LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
        int level = spSkill != null ? spSkill.level : 0;
        if (level < gate.minLevel) {
            event.setCanceled(true);
            sp.displayClientMessage(
                    Component.translatable("msg.levelrpg.locked", gate.minLevel, displayNameForSkill(gate.skill)).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    // ----------------- Server denial (equipping gated items) -----------------
    @SubscribeEvent
    public void onEquipmentChange(net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Decide whether to enforce for this slot:
        // - Always ignore MAINHAND (tools/weapons allowed to shift-click around)
        // - Process OFFHAND only for shields
        // - Process all other equipment slots (armor)
        var slot = event.getSlot();
        if (slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND) return;
        ItemStack to = event.getTo();
        if (to == null || to.isEmpty()) return;
        boolean isShieldOffhand = (slot == net.minecraft.world.entity.EquipmentSlot.OFFHAND)
                && (to.getItem() instanceof net.minecraft.world.item.ShieldItem);
        boolean isArmorSlot = (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND
                && slot != net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if (!(isShieldOffhand || isArmorSlot)) return; // allow non-shield offhand items to move freely

        // Ignore durability-only or metadata-only updates where the Item type didn't change
        ItemStack from = event.getFrom();
        if (from != null && !from.isEmpty()) {
            if (from.getItem() == to.getItem()) {
                return; // same item type (likely damage/durability change), not an equip attempt
            }
        }

        // Per-tick reentrancy guard (player + slot)
        long tick = sp.level().getGameTime();
        String key = sp.getUUID() + "|" + slot.getName();
        Long last = LAST_EQUIP_TICK.get(key);
        if (last != null && last == tick) {
            return; // already handled this tick
        }

        GateRules.ItemGate gate = findGateFor(to);
        if (gate == null) return;
        LevelProfile profile = LevelProfile.get(sp);
        LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
        int level = spSkill != null ? spSkill.level : 0;
        if (level < gate.minLevel) {
            LAST_EQUIP_TICK.put(key, tick);
            // Revert equipment and return a copy of the blocked stack to inventory to avoid glitches/dupes
            ItemStack blocked = to.copy();

            // Restore previous stack in that slot
            sp.setItemSlot(slot, from == null ? ItemStack.EMPTY : from.copy());

            // Return the blocked stack back to the player's inventory (or drop leftovers)
            // Prefer Inventory#placeItemBackInInventory for safe insertion
            if (!blocked.isEmpty()) {
                try {
                    sp.getInventory().placeItemBackInInventory(blocked);
                } catch (Throwable t) {
                    // Fallback: try standard add; drop if it doesn't fit
                    if (!sp.getInventory().add(blocked)) {
                        sp.drop(blocked, false);
                    }
                }
            }

            // Force client/server inventory sync after revert
            sp.getInventory().setChanged();
            if (sp.containerMenu != null) {
                sp.containerMenu.broadcastChanges();
            }

            sp.displayClientMessage(
                    Component.translatable("msg.levelrpg.locked", gate.minLevel, displayNameForSkill(gate.skill)).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    // Continuous enforcement to prevent shift-click bypass and clean illegal gear
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        long tick = sp.level().getGameTime();

        // Check armor slots and shield offhand
        EquipmentSlot[] slots = new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
        };
        LevelProfile profile = null; // lazy load
        for (EquipmentSlot slot : slots) {
            ItemStack stack = sp.getItemBySlot(slot);
            if (stack == null || stack.isEmpty()) continue;
            // Only enforce offhand if it's a shield
            if (slot == EquipmentSlot.OFFHAND && !(stack.getItem() instanceof net.minecraft.world.item.ShieldItem)) {
                continue;
            }
            GateRules.ItemGate gate = findGateFor(stack);
            if (gate == null) continue;
            if (profile == null) profile = LevelProfile.get(sp);
            LevelProfile.SkillProgress spSkill = profile.skills.get(gate.skill);
            int level = spSkill != null ? spSkill.level : 0;
            if (level >= gate.minLevel) continue;

            // Avoid multiple actions within the same tick per slot
            String key = sp.getUUID() + "|" + slot.getName();
            Long last = LAST_EQUIP_TICK.get(key);
            if (last != null && last == tick) {
                continue;
            }
            LAST_EQUIP_TICK.put(key, tick);

            // Remove from slot and return to inventory safely
            ItemStack blocked = stack.copy();
            sp.setItemSlot(slot, ItemStack.EMPTY);
            if (!blocked.isEmpty()) {
                try {
                    sp.getInventory().placeItemBackInInventory(blocked);
                } catch (Throwable t) {
                    if (!sp.getInventory().add(blocked)) {
                        sp.drop(blocked, false);
                    }
                }
            }
            sp.getInventory().setChanged();
            if (sp.containerMenu != null) sp.containerMenu.broadcastChanges();

            sp.displayClientMessage(
                    Component.translatable("msg.levelrpg.locked", gate.minLevel, displayNameForSkill(gate.skill)).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }
}
