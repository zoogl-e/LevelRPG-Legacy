package net.zoogle.levelrpg.profile;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical skills. Seven distinct archetypes — Valor absorbs the former
 * Vitality skill as its Grit branch.
 */
public enum ProgressionSkill {
    VALOR("valor"),
    FINESSE("finesse"),
    ARCANA("arcana"),
    DELVING("delving"),
    FORGING("forging"),
    ARTIFICING("artificing"),
    HEARTH("hearth");

    private final ResourceLocation id;

    ProgressionSkill(String path) {
        this.id = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }

    public ResourceLocation id() {
        return id;
    }

    public String displayName() {
        String path = id.getPath();
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    public static Optional<ProgressionSkill> fromId(ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        for (ProgressionSkill skill : values()) {
            if (skill.id.equals(id)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    public static Optional<ProgressionSkill> fromPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String normalized = path.trim().toLowerCase(Locale.ROOT);
        for (ProgressionSkill skill : values()) {
            if (skill.id.getPath().equals(normalized)) {
                return Optional.of(skill);
            }
        }
        return Optional.empty();
    }

    public static Set<ResourceLocation> orderedIds() {
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        Arrays.stream(values()).map(ProgressionSkill::id).forEach(ids::add);
        return ids;
    }

    public static boolean isCanonicalId(ResourceLocation id) {
        return fromId(id).isPresent();
    }
}
