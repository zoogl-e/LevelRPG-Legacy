package net.zoogle.levelrpg.profile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Reserved profile state for the future archetype-based starting distribution.
 * This is persisted now so the profile model can evolve without another schema
 * rewrite when archetypes become active gameplay.
 */
public class ArchetypeState {
    public ResourceLocation id;
    public boolean startingDistributionApplied;

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        if (id != null) {
            tag.putString("id", id.toString());
        }
        tag.putBoolean("startingDistributionApplied", startingDistributionApplied);
        return tag;
    }

    public void deserialize(CompoundTag tag) {
        this.id = null;
        if (tag.contains("id")) {
            try {
                this.id = ResourceLocation.parse(tag.getString("id"));
            } catch (Exception ignored) {
                this.id = null;
            }
        }
        this.startingDistributionApplied = tag.getBoolean("startingDistributionApplied");
    }
}
