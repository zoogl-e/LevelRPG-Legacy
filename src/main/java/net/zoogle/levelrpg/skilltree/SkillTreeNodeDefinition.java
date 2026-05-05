package net.zoogle.levelrpg.skilltree;

import net.zoogle.levelrpg.skilltree.effect.SkillNodeEffect;

import java.util.List;

public record SkillTreeNodeDefinition(
        String id,
        String title,
        String description,
        int x,
        int y,
        int requiredRank,
        int cost,
        RequirementSpec requirement,
        RequirementSpec revealRequirement,
        NodeVisibilityMode visibility,
        String type,
        String icon,
        List<SkillNodeEffect> effects,
        boolean hidden
) {
    public SkillTreeNodeDefinition(String id, String title, String description, int x, int y, int requiredRank, int cost, List<String> requires, boolean hidden) {
        this(id, title, description, x, y, requiredRank, cost, RequirementSpec.all(requires), null, hidden ? NodeVisibilityMode.HIDDEN : NodeVisibilityMode.VISIBLE, "trait", "", List.of(), hidden);
    }

    public SkillTreeNodeDefinition(String id, String title, String description, int x, int y, int requiredRank, int cost, List<String> requires, String type, String icon, boolean hidden) {
        this(id, title, description, x, y, requiredRank, cost, RequirementSpec.all(requires), null, hidden ? NodeVisibilityMode.HIDDEN : NodeVisibilityMode.VISIBLE, type, icon, List.of(), hidden);
    }

    public SkillTreeNodeDefinition {
        id = clean(id);
        title = title == null || title.isBlank() ? id : title;
        description = description == null ? "" : description;
        cost = Math.max(1, cost);
        requiredRank = Math.max(0, requiredRank);
        requirement = requirement == null ? RequirementSpec.EMPTY : requirement;
        visibility = visibility == null ? (hidden ? NodeVisibilityMode.HIDDEN : NodeVisibilityMode.VISIBLE) : visibility;
        if (revealRequirement == null && visibility != NodeVisibilityMode.VISIBLE && !requirement.isEmpty()) {
            revealRequirement = requirement;
        }
        type = clean(type).toLowerCase();
        if (type.isBlank()) {
            type = "trait";
        }
        icon = clean(icon);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public List<String> requires() {
        return requirement.nodes();
    }

    public boolean isHiddenBeforeReveal() {
        return visibility == NodeVisibilityMode.HIDDEN;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
