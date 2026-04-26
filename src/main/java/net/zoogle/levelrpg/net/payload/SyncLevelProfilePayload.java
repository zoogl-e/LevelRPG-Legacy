package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import org.jetbrains.annotations.Nullable;
import net.zoogle.levelrpg.profile.LevelProfile;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SyncLevelProfilePayload(
        List<Entry> skills,
        List<TreeEntry> trees,
        int availableSkillPoints,
        int spentSkillPoints,
        @Nullable ResourceLocation archetypeId,
        boolean archetypeApplied
)
        implements CustomPacketPayload {

    public static final Type<SyncLevelProfilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "sync_profile"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLevelProfilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(64)), SyncLevelProfilePayload::skills,
                    TreeEntry.STREAM_CODEC.apply(ByteBufCodecs.list(64)), SyncLevelProfilePayload::trees,
                    ByteBufCodecs.VAR_INT, SyncLevelProfilePayload::availableSkillPoints,
                    ByteBufCodecs.VAR_INT, SyncLevelProfilePayload::spentSkillPoints,
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), payload -> java.util.Optional.ofNullable(payload.archetypeId()),
                    ByteBufCodecs.BOOL, SyncLevelProfilePayload::archetypeApplied,
                    (skills, trees, availablePoints, spentPoints, archetypeId, archetypeApplied) ->
                            new SyncLevelProfilePayload(skills, trees, availablePoints, spentPoints, archetypeId.orElse(null), archetypeApplied)
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncLevelProfilePayload from(LevelProfile profile) {
        List<Entry> list = new ArrayList<>(ProgressionSkill.values().length);
        for (Map.Entry<ResourceLocation, SkillState> e : profile.canonicalSkillsView().entrySet()) {
            list.add(new Entry(e.getKey(), e.getValue().level, e.getValue().masteryLevel, e.getValue().masteryXp));
        }
        List<TreeEntry> treeEntries = new ArrayList<>(ProgressionSkill.values().length);
        for (ProgressionSkill skill : ProgressionSkill.values()) {
            ResourceLocation treeId = skill.id();
            Set<String> unlocked = profile.getUnlockedTreeNodes(treeId);
            treeEntries.add(new TreeEntry(treeId, profile.getTreePointsSpent(treeId), List.copyOf(unlocked)));
        }
        return new SyncLevelProfilePayload(
                list,
                treeEntries,
                profile.availableSkillPoints,
                profile.spentSkillPoints,
                profile.archetype.id,
                profile.hasAppliedStartingArchetype()
        );
    }

    public Map<ResourceLocation, SkillState> toMap() {
        LinkedHashMap<ResourceLocation, SkillState> map = new LinkedHashMap<>();
        for (Entry e : skills) {
            SkillState sp = new SkillState();
            sp.level = e.level();
            sp.masteryLevel = e.masteryLevel();
            sp.masteryXp = e.masteryXp();
            map.put(e.id(), sp);
        }
        return map;
    }

    public Map<ResourceLocation, Integer> toTreeSpentMap() {
        LinkedHashMap<ResourceLocation, Integer> map = new LinkedHashMap<>();
        for (TreeEntry tree : trees) {
            map.put(tree.id(), Math.max(0, tree.spentPoints()));
        }
        return map;
    }

    public Map<ResourceLocation, Set<String>> toTreeUnlockedMap() {
        LinkedHashMap<ResourceLocation, Set<String>> map = new LinkedHashMap<>();
        for (TreeEntry tree : trees) {
            map.put(tree.id(), Set.copyOf(tree.unlockedNodes()));
        }
        return map;
    }

    public record Entry(ResourceLocation id, int level, int masteryLevel, long masteryXp) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, Entry::id,
                        ByteBufCodecs.VAR_INT, Entry::level,
                        ByteBufCodecs.VAR_INT, Entry::masteryLevel,
                        ByteBufCodecs.VAR_LONG, Entry::masteryXp,
                        Entry::new
                );
    }

    public record TreeEntry(ResourceLocation id, int spentPoints, List<String> unlockedNodes) {
        public TreeEntry {
            unlockedNodes = unlockedNodes == null ? List.of() : List.copyOf(unlockedNodes);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, TreeEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, TreeEntry::id,
                        ByteBufCodecs.VAR_INT, TreeEntry::spentPoints,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), TreeEntry::unlockedNodes,
                        TreeEntry::new
                );
    }
}
