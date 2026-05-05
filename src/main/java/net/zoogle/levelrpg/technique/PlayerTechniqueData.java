package net.zoogle.levelrpg.technique;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerTechniqueData {
    public static final int SLOT_COUNT = 9;

    private final ResourceLocation[] slots = new ResourceLocation[SLOT_COUNT];
    private final LinkedHashMap<ResourceLocation, Integer> cooldowns = new LinkedHashMap<>();
    private int selectedSlot = 0;

    public ResourceLocation getSlot(int zeroBasedSlot) {
        return isValidSlot(zeroBasedSlot) ? slots[zeroBasedSlot] : null;
    }

    public void setSlot(int zeroBasedSlot, ResourceLocation techniqueId) {
        if (isValidSlot(zeroBasedSlot)) {
            slots[zeroBasedSlot] = techniqueId;
        }
    }

    public void clearSlot(int zeroBasedSlot) {
        if (isValidSlot(zeroBasedSlot)) {
            slots[zeroBasedSlot] = null;
        }
    }

    public ResourceLocation[] slotsCopy() {
        return Arrays.copyOf(slots, slots.length);
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int zeroBasedSlot) {
        if (isValidSlot(zeroBasedSlot)) {
            this.selectedSlot = zeroBasedSlot;
        }
    }

    public int cooldown(ResourceLocation techniqueId) {
        return Math.max(0, cooldowns.getOrDefault(techniqueId, 0));
    }

    public void setCooldown(ResourceLocation techniqueId, int ticks) {
        if (techniqueId == null) {
            return;
        }
        int clamped = Math.max(0, ticks);
        if (clamped <= 0) {
            cooldowns.remove(techniqueId);
        } else {
            cooldowns.put(techniqueId, clamped);
        }
    }

    public Map<ResourceLocation, Integer> cooldownsView() {
        return Map.copyOf(cooldowns);
    }

    public boolean tickCooldowns() {
        boolean changed = false;
        java.util.Iterator<Map.Entry<ResourceLocation, Integer>> iterator = cooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, Integer> entry = iterator.next();
            int next = Math.max(0, entry.getValue() - 1);
            if (next <= 0) {
                iterator.remove();
            } else {
                entry.setValue(next);
            }
            changed = true;
        }
        return changed;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        CompoundTag slotsTag = new CompoundTag();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                slotsTag.putString(Integer.toString(i), slots[i].toString());
            }
        }
        tag.put("slots", slotsTag);
        tag.putInt("selectedSlot", selectedSlot);
        CompoundTag cooldownsTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : cooldowns.entrySet()) {
            cooldownsTag.putInt(entry.getKey().toString(), Math.max(0, entry.getValue()));
        }
        tag.put("cooldowns", cooldownsTag);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        Arrays.fill(slots, null);
        cooldowns.clear();
        selectedSlot = 0;
        if (tag.contains("slots")) {
            CompoundTag slotsTag = tag.getCompound("slots");
            for (String key : slotsTag.getAllKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    if (isValidSlot(slot)) {
                        ResourceLocation id = ResourceLocation.parse(slotsTag.getString(key));
                        if (TechniqueRegistry.get(id) != null) {
                            slots[slot] = id;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (tag.contains("cooldowns")) {
            CompoundTag cooldownsTag = tag.getCompound("cooldowns");
            for (String key : cooldownsTag.getAllKeys()) {
                try {
                    ResourceLocation id = ResourceLocation.parse(key);
                    int ticks = Math.max(0, cooldownsTag.getInt(key));
                    if (TechniqueRegistry.get(id) != null && ticks > 0) {
                        cooldowns.put(id, ticks);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (tag.contains("selectedSlot")) {
            int slot = tag.getInt("selectedSlot");
            if (isValidSlot(slot)) {
                selectedSlot = slot;
            }
        }
    }

    public static boolean isValidSlot(int zeroBasedSlot) {
        return zeroBasedSlot >= 0 && zeroBasedSlot < SLOT_COUNT;
    }
}
