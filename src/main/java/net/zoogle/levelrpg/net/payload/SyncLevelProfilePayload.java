package net.zoogle.levelrpg.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.profile.LevelProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SyncLevelProfilePayload(int playerLevel, int unspentSkillPoints, List<Entry> skills)
        implements CustomPacketPayload {

    public static final Type<SyncLevelProfilePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "sync_profile"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLevelProfilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncLevelProfilePayload::playerLevel,
                    ByteBufCodecs.VAR_INT, SyncLevelProfilePayload::unspentSkillPoints,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(64)), SyncLevelProfilePayload::skills,
                    SyncLevelProfilePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncLevelProfilePayload from(LevelProfile profile) {
        List<Entry> list = new ArrayList<>(profile.skills.size());
        for (Map.Entry<ResourceLocation, LevelProfile.SkillProgress> e : profile.skills.entrySet()) {
            list.add(new Entry(e.getKey(), e.getValue().level, e.getValue().xp));
        }
        return new SyncLevelProfilePayload(profile.playerLevel, profile.unspentSkillPoints, list);
    }

    public Map<ResourceLocation, LevelProfile.SkillProgress> toMap() {
        LinkedHashMap<ResourceLocation, LevelProfile.SkillProgress> map = new LinkedHashMap<>();
        for (Entry e : skills) {
            LevelProfile.SkillProgress sp = new LevelProfile.SkillProgress();
            sp.level = e.level();
            sp.xp = e.xp();
            map.put(e.id(), sp);
        }
        return map;
    }

    public record Entry(ResourceLocation id, int level, long xp) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ResourceLocation.STREAM_CODEC, Entry::id,
                        ByteBufCodecs.VAR_INT, Entry::level,
                        ByteBufCodecs.VAR_LONG, Entry::xp,
                        Entry::new
                );
    }
}
