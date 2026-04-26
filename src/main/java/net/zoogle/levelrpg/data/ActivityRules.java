package net.zoogle.levelrpg.data;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import org.slf4j.Logger;

import java.util.*;

/**
 * Loads activity rules from datapacks to award mastery based on events.
 * Folder: data/<ns>/activity_rules/*.json
 * Schema supports either an object with a "rules" array, or a single rule object per file.
 *
 * Supported rule types:
 * - break_block: { "type":"break_block", "block_tag":"minecraft:logs", "skill":"levelrpg:woodcutting", "xp":3 }
 * - kill_entity: { "type":"kill_entity", "entity_tag":"minecraft:skeletons", "skill":"levelrpg:archery", "xp":10, "weapon_tag":"levelrpg:bows" }
 * - craft_item: { "type":"craft_item", "item_tag":"minecraft:planks", "skill":"levelrpg:woodcutting", "xp":1 }
 * - smelt_item: { "type":"smelt_item", "item_tag":"minecraft:ingots/iron", "skill":"levelrpg:smithing", "xp":2 }
 */
public class ActivityRules extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();

    public ActivityRules() {
        super(GSON, "activity_rules");
    }

    public static final class BreakBlockRule {
        public final ResourceLocation skill;
        public final int xp;
        public final ResourceLocation blockTag;
        public BreakBlockRule(ResourceLocation skill, int xp, ResourceLocation blockTag) {
            this.skill = skill; this.xp = xp; this.blockTag = blockTag;
        }
        public TagKey<net.minecraft.world.level.block.Block> tagKey() {
            return TagKey.create(Registries.BLOCK, blockTag);
        }
    }

    public static final class KillEntityRule {
        public final ResourceLocation skill;
        public final int xp;
        public final ResourceLocation entityTag;
        public final ResourceLocation weaponItemTag; // optional
        public KillEntityRule(ResourceLocation skill, int xp, ResourceLocation entityTag, ResourceLocation weaponItemTag) {
            this.skill = skill; this.xp = xp; this.entityTag = entityTag; this.weaponItemTag = weaponItemTag;
        }
        public TagKey<net.minecraft.world.entity.EntityType<?>> entityTagKey() {
            return TagKey.create(Registries.ENTITY_TYPE, entityTag);
        }
        public TagKey<net.minecraft.world.item.Item> weaponTagKey() {
            return weaponItemTag == null ? null : TagKey.create(Registries.ITEM, weaponItemTag);
        }
    }

    public static final class CraftItemRule {
        public final ResourceLocation skill;
        public final int xp;
        public final ResourceLocation itemTag;
        public CraftItemRule(ResourceLocation skill, int xp, ResourceLocation itemTag) {
            this.skill = skill; this.xp = xp; this.itemTag = itemTag;
        }
        public TagKey<net.minecraft.world.item.Item> tagKey() {
            return TagKey.create(Registries.ITEM, itemTag);
        }
    }

    public static final class SmeltItemRule {
        public final ResourceLocation skill;
        public final int xp;
        public final ResourceLocation itemTag;
        public SmeltItemRule(ResourceLocation skill, int xp, ResourceLocation itemTag) {
            this.skill = skill; this.xp = xp; this.itemTag = itemTag;
        }
        public TagKey<net.minecraft.world.item.Item> tagKey() {
            return TagKey.create(Registries.ITEM, itemTag);
        }
    }

    private static final List<BreakBlockRule> BREAK_BLOCK_RULES = new ArrayList<>();
    private static final List<KillEntityRule> KILL_ENTITY_RULES = new ArrayList<>();
    private static final List<CraftItemRule> CRAFT_ITEM_RULES = new ArrayList<>();
    private static final List<SmeltItemRule> SMELT_ITEM_RULES = new ArrayList<>();

    public static List<BreakBlockRule> breakBlockRules() { return Collections.unmodifiableList(BREAK_BLOCK_RULES); }
    public static List<KillEntityRule> killEntityRules() { return Collections.unmodifiableList(KILL_ENTITY_RULES); }
    public static List<CraftItemRule> craftItemRules() { return Collections.unmodifiableList(CRAFT_ITEM_RULES); }
    public static List<SmeltItemRule> smeltItemRules() { return Collections.unmodifiableList(SMELT_ITEM_RULES); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        BREAK_BLOCK_RULES.clear();
        KILL_ENTITY_RULES.clear();
        CRAFT_ITEM_RULES.clear();
        SMELT_ITEM_RULES.clear();
        int total = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            try {
                JsonElement el = entry.getValue();
                if (el.isJsonObject()) {
                    JsonObject root = el.getAsJsonObject();
                    if (root.has("rules") && root.get("rules").isJsonArray()) {
                        for (JsonElement re : root.getAsJsonArray("rules")) {
                            parseRule(re.getAsJsonObject());
                            total++;
                        }
                    } else {
                        parseRule(root);
                        total++;
                    }
                } else if (el.isJsonArray()) {
                    for (JsonElement re : el.getAsJsonArray()) {
                        parseRule(re.getAsJsonObject());
                        total++;
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse activity rule json {}", entry.getKey(), ex);
            }
        }
        LOGGER.info(
                "Loaded activity rules: break_block={}, kill_entity={}, craft_item={}, smelt_item={}, total={}",
                BREAK_BLOCK_RULES.size(),
                KILL_ENTITY_RULES.size(),
                CRAFT_ITEM_RULES.size(),
                SMELT_ITEM_RULES.size(),
                total
        );
    }

    private static void parseRule(JsonObject obj) {
        String type = obj.has("type") ? obj.get("type").getAsString() : "";
        if ("break_block".equalsIgnoreCase(type)) {
            ResourceLocation skill = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(obj.get("skill").getAsString(), net.zoogle.levelrpg.LevelRPG.MODID);
            if (!ProgressionSkill.isCanonicalId(skill)) return;
            int xp = obj.get("xp").getAsInt();
            ResourceLocation blockTag = ResourceLocation.parse(obj.get("block_tag").getAsString());
            BREAK_BLOCK_RULES.add(new BreakBlockRule(skill, xp, blockTag));
        } else if ("kill_entity".equalsIgnoreCase(type)) {
            ResourceLocation skill = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(obj.get("skill").getAsString(), net.zoogle.levelrpg.LevelRPG.MODID);
            if (!ProgressionSkill.isCanonicalId(skill)) return;
            int xp = obj.get("xp").getAsInt();
            ResourceLocation entityTag = ResourceLocation.parse(obj.get("entity_tag").getAsString());
            ResourceLocation weapon = obj.has("weapon_tag") ? ResourceLocation.parse(obj.get("weapon_tag").getAsString()) : null;
            KILL_ENTITY_RULES.add(new KillEntityRule(skill, xp, entityTag, weapon));
        } else if ("craft_item".equalsIgnoreCase(type)) {
            ResourceLocation skill = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(obj.get("skill").getAsString(), net.zoogle.levelrpg.LevelRPG.MODID);
            if (!ProgressionSkill.isCanonicalId(skill)) return;
            int xp = obj.get("xp").getAsInt();
            ResourceLocation itemTag = ResourceLocation.parse(obj.get("item_tag").getAsString());
            CRAFT_ITEM_RULES.add(new CraftItemRule(skill, xp, itemTag));
        } else if ("smelt_item".equalsIgnoreCase(type)) {
            ResourceLocation skill = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(obj.get("skill").getAsString(), net.zoogle.levelrpg.LevelRPG.MODID);
            if (!ProgressionSkill.isCanonicalId(skill)) return;
            int xp = obj.get("xp").getAsInt();
            ResourceLocation itemTag = ResourceLocation.parse(obj.get("item_tag").getAsString());
            SMELT_ITEM_RULES.add(new SmeltItemRule(skill, xp, itemTag));
        }
    }
}
