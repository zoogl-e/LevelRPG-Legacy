package net.zoogle.levelrpg.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.skilltree.SkillUnlockQuery;
import net.zoogle.levelrpg.skilltree.ValorNodeIds;
import net.zoogle.levelrpg.valor.ValorMeleeWeapons;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vertical slice: {@link ValorNodeIds#MELEE_COMBATANT} → {@link ValorNodeIds#COMBO_BREAK} → {@link ValorNodeIds#SEASONED_FIGHTER}.
 */
public final class ValorDuelistSliceEvents {
    private static final ResourceLocation MELEE_ATTACK_SPEED_ID = id("valor_melee_combatant_attack_speed");
    /** {@link AttributeModifier.Operation#ADD_MULTIPLIED_TOTAL} bonus while wielding a melee weapon. */
    private static final double MELEE_COMBATANT_ATTACK_SPEED_MULT = 0.10;
    private static final int COMBO_BREAK_STACK_CAP = 5;
    private static final double COMBO_BREAK_DAMAGE_PER_STACK = 0.065;
    private static final int COMBO_BREAK_CRIT_TIMEOUT_TICKS = 90;
    private static final double SEASONED_FIGHTER_RADIUS = 10.0;
    private static final int SEASONED_FIGHTER_EFFECT_TICKS = 35;
    private static final int SEASONED_FIGHTER_RECHECK_INTERVAL = 15;

    private static final Map<UUID, ComboBreakState> COMBO_STATE = new ConcurrentHashMap<>();

    private record ComboBreakState(int stacks, int lastCritTick) {
        ComboBreakState withCrit(int tick) {
            int next = Math.min(COMBO_BREAK_STACK_CAP, stacks + 1);
            return new ComboBreakState(next, tick);
        }

        ComboBreakState decayIfStale(int tickNow) {
            if (tickNow - lastCritTick > COMBO_BREAK_CRIT_TIMEOUT_TICKS) {
                return new ComboBreakState(0, lastCritTick);
            }
            return this;
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            COMBO_STATE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onCriticalHitCombo(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer sp)) {
            return;
        }
        if (!event.isCriticalHit()) {
            return;
        }
        if (!ValorMeleeWeapons.isValorMelee(sp.getMainHandItem())) {
            return;
        }
        LevelProfile profile = LevelProfile.get(sp);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.COMBO_BREAK)) {
            return;
        }
        int tick = sp.tickCount;
        COMBO_STATE.compute(sp.getUUID(), (id, prev) -> {
            ComboBreakState base = prev == null ? new ComboBreakState(0, tick) : prev.decayIfStale(tick);
            return base.withCrit(tick);
        });
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onOutgoingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        if (attacker.isSpectator() || attacker.isCreative()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() == attacker) {
            return;
        }
        if (!ValorMeleeWeapons.isValorMelee(attacker.getMainHandItem())) {
            return;
        }
        LevelProfile profile = LevelProfile.get(attacker);
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.COMBO_BREAK)) {
            return;
        }
        ComboBreakState state = COMBO_STATE.get(attacker.getUUID());
        if (state == null) {
            return;
        }
        ComboBreakState fresh = state.decayIfStale(attacker.tickCount);
        if (!fresh.equals(state)) {
            COMBO_STATE.put(attacker.getUUID(), fresh);
        }
        if (fresh.stacks <= 0) {
            return;
        }
        float mult = (float) (1.0 + COMBO_BREAK_DAMAGE_PER_STACK * fresh.stacks);
        event.setNewDamage(event.getNewDamage() * mult);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        LevelProfile profile = LevelProfile.get(player);
        int tick = player.tickCount;

        applyMeleeCombatantAttackSpeed(player, profile);

        if (tick % SEASONED_FIGHTER_RECHECK_INTERVAL == 0) {
            applySeasonedFighterRegen(player, profile);
        }

    }

    private static void applyMeleeCombatantAttackSpeed(ServerPlayer player, LevelProfile profile) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_SPEED);
        if (instance == null) {
            return;
        }
        boolean want = SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.MELEE_COMBATANT)
                && ValorMeleeWeapons.isValorMelee(player.getMainHandItem());
        if (!want) {
            instance.removeModifier(MELEE_ATTACK_SPEED_ID);
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(
                MELEE_ATTACK_SPEED_ID,
                MELEE_COMBATANT_ATTACK_SPEED_MULT,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        ));
    }

    private static void applySeasonedFighterRegen(ServerPlayer player, LevelProfile profile) {
        if (!SkillUnlockQuery.hasNode(profile, ValorNodeIds.SKILL, ValorNodeIds.SEASONED_FIGHTER)) {
            return;
        }
        int monsters = countHostileMonstersNearby(player);
        if (monsters <= 0) {
            return;
        }
        int amplifier = Math.min(3, Math.max(0, (monsters - 1) / 2));
        player.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION,
                SEASONED_FIGHTER_EFFECT_TICKS,
                amplifier,
                false,
                false,
                true
        ));
    }

    private static int countHostileMonstersNearby(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(SEASONED_FIGHTER_RADIUS);
        return player.level().getEntities(player, box, e -> e instanceof Monster m && m.isAlive() && !m.isRemoved()).size();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }
}
