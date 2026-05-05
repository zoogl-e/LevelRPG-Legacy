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
        int bonusSpecializationPoints,
        @Nullable ResourceLocation archetypeId,
        boolean archetypeApplied
)
        implements CustomPacketPayload {

    public static final Type<SyncLevelProfilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "sync_profile"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLevelProfilePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.skills().size());
                        for (Entry entry : payload.skills()) {
                            Entry.STREAM_CODEC.encode(buf, entry);
                        }
                        buf.writeVarInt(payload.trees().size());
                        for (TreeEntry tree : payload.trees()) {
                            TreeEntry.STREAM_CODEC.encode(buf, tree);
                        }
                        buf.writeVarInt(payload.availableSkillPoints());
                        buf.writeVarInt(payload.spentSkillPoints());
                        buf.writeVarInt(payload.bonusSpecializationPoints());
                        buf.writeBoolean(payload.archetypeId() != null);
                        if (payload.archetypeId() != null) {
                            ResourceLocation.STREAM_CODEC.encode(buf, payload.archetypeId());
                        }
                        buf.writeBoolean(payload.archetypeApplied());
                    },
                    buf -> {
                        int skillCount = Math.min(64, Math.max(0, buf.readVarInt()));
                        ArrayList<Entry> skills = new ArrayList<>(skillCount);
                        for (int i = 0; i < skillCount; i++) {
                            skills.add(Entry.STREAM_CODEC.decode(buf));
                        }
                        int treeCount = Math.min(64, Math.max(0, buf.readVarInt()));
                        ArrayList<TreeEntry> trees = new ArrayList<>(treeCount);
                        for (int i = 0; i < treeCount; i++) {
                            trees.add(TreeEntry.STREAM_CODEC.decode(buf));
                        }
                        int availableSkillPoints = buf.readVarInt();
                        int spentSkillPoints = buf.readVarInt();
                        int bonusSpecializationPoints = buf.readVarInt();
                        ResourceLocation archetypeId = buf.readBoolean() ? ResourceLocation.STREAM_CODEC.decode(buf) : null;
                        boolean archetypeApplied = buf.readBoolean();
                        return new SyncLevelProfilePayload(
                                List.copyOf(skills),
                                List.copyOf(trees),
                                availableSkillPoints,
                                spentSkillPoints,
                                bonusSpecializationPoints,
                                archetypeId,
                                archetypeApplied
                        );
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncLevelProfilePayload from(LevelProfile profile) {
        List<Entry> list = new ArrayList<>(ProgressionSkill.values().length);
        for (Map.Entry<ResourceLocation, SkillState> e : profile.canonicalSkillsView().entrySet()) {
            list.add(new Entry(e.getKey(), e.getValue().level, e.getValue().rank, e.getValue().proficiency));
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
                profile.bonusSpecializationPoints,
                profile.archetype.id,
                profile.hasAppliedStartingArchetype()
        );
    }

    public Map<ResourceLocation, SkillState> toMap() {
        LinkedHashMap<ResourceLocation, SkillState> map = new LinkedHashMap<>();
        for (Entry e : skills) {
            SkillState sp = new SkillState();
            sp.level = e.level();
            sp.rank = e.rank();
            sp.proficiency = e.proficiency();
            map.put(e.id(), sp);
        }
        return map;
    }

    public Map<ResourceLocation, Integer> toTreeSpentMap() {
        LinkedHashMap<ResourceLocation, Integer> map = new LinkedHashMap<>();
        for (TreeEntry tree : trees) {
            map.put(tree.id(), Math.max(0, tree.inscribedPoints()));
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

    public record Entry(ResourceLocation id, int level, int rank, long proficiency) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, Entry::id,
                        ByteBufCodecs.VAR_INT, Entry::level,
                        ByteBufCodecs.VAR_INT, Entry::rank,
                        ByteBufCodecs.VAR_LONG, Entry::proficiency,
                        Entry::new
                );
    }

    public record TreeEntry(ResourceLocation id, int inscribedPoints, List<String> unlockedNodes) {
        public TreeEntry {
            unlockedNodes = unlockedNodes == null ? List.of() : List.copyOf(unlockedNodes);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, TreeEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, TreeEntry::id,
                        ByteBufCodecs.VAR_INT, TreeEntry::inscribedPoints,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), TreeEntry::unlockedNodes,
                        TreeEntry::new
                );
    }
}
