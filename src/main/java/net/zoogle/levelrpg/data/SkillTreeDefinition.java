package net.zoogle.levelrpg.data;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;

public record SkillTreeDefinition(
        ResourceLocation skill,
        int minSkillLevel,
        LinkedHashMap<String, Node> nodes
) {
    public record Node(String id, int cost, List<String> requires) {}
}
