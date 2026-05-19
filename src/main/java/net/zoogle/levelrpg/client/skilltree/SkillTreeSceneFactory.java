package net.zoogle.levelrpg.client.skilltree;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.SpecializationProgression;
import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreePresentationDefinition;
import net.zoogle.levelrpg.skilltree.SkillTreeRegistry;
import net.zoogle.levelrpg.skilltree.SkillTreeState;
import net.zoogle.levelrpg.skilltree.SkillTreeStateResolver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds active LevelRPG skill tree scenes from registry/editor data and current client profile state.
 *
 * <p>This factory does not know about screen widgets, input, rendering, networking, selection,
 * chamber animation, or JSON saving.
 */
public final class SkillTreeSceneFactory {
    private final int nodeSize;

    public SkillTreeSceneFactory(int nodeSize) {
        this.nodeSize = nodeSize;
    }

    public SkillTreePresentationDefinition resolveDefinition(
            ResourceLocation skillId,
            SkillTreePresentationDefinition exportedPreviewDefinition,
            Map<ResourceLocation, SkillTreePresentationDefinition> savedPreviewDefinitions
    ) {
        if (exportedPreviewDefinition != null) {
            return exportedPreviewDefinition;
        }
        SkillTreePresentationDefinition savedPreview = savedPreviewDefinitions == null ? null : savedPreviewDefinitions.get(skillId);
        return savedPreview != null ? savedPreview : SkillTreeRegistry.get(skillId);
    }

    public SkillTreeScene createPlayerScene(ResourceLocation skillId) {
        return createPlayerScene(skillId, SkillTreeRegistry.get(skillId));
    }

    public SkillTreeScene createPlayerScene(ResourceLocation skillId, SkillTreePresentationDefinition definition) {
        return SkillTreeScene.create(skillId, definition, resolvePlayerState(skillId, definition), nodeSize);
    }

    public SkillTreeScene createEditorScene(ResourceLocation skillId, SkillTreeEditorDraft draft) {
        if (draft == null) {
            return null;
        }
        return createEditorScene(skillId, draft.toDefinition());
    }

    public SkillTreeScene createEditorScene(ResourceLocation skillId, SkillTreePresentationDefinition definition) {
        return SkillTreeScene.create(skillId, definition, createEditorState(skillId, definition), nodeSize);
    }

    private SkillTreeState resolvePlayerState(ResourceLocation skillId, SkillTreePresentationDefinition definition) {
        if (definition == null) {
            return null;
        }
        int investedDisciplineLevel = 0;
        SkillState skillState = ClientProfileCache.getSkillsView().get(skillId);
        if (skillState != null) {
            investedDisciplineLevel = Math.max(0, skillState.level);
        }
        int earned = SpecializationProgression.gainedInsightForTotalLevels(ClientProfileCache.totalInvestedLevelsAcrossSkills())
                + ClientProfileCache.getBonusSpecializationPoints();
        int spent = ClientProfileCache.totalSpecializationSpentAcrossTrees();
        int available = Math.max(0, earned - spent);
        Set<String> unlocked = ClientProfileCache.getTreeUnlockedNodes(skillId);
        return SkillTreeStateResolver.resolve(skillId, definition, investedDisciplineLevel, available, unlocked);
    }

    private SkillTreeState createEditorState(ResourceLocation skillId, SkillTreePresentationDefinition definition) {
        if (definition == null) {
            return null;
        }
        LinkedHashMap<String, SkillNodeStatus> statuses = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> revealed = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> rendered = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> obfuscated = new LinkedHashMap<>();
        for (String nodeId : definition.nodes().keySet()) {
            statuses.put(nodeId, SkillNodeStatus.AVAILABLE);
            revealed.put(nodeId, true);
            rendered.put(nodeId, true);
            obfuscated.put(nodeId, false);
        }
        return new SkillTreeState(skillId, 0, 0, Set.of(), statuses, revealed, rendered, obfuscated);
    }
}
