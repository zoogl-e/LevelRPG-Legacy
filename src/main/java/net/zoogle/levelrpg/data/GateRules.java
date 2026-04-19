package net.zoogle.levelrpg.data;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads gate rules from datapacks.
 * Folder: data/<ns>/gates/items.json
 * Schema example (object map):
 * {
 *   "minecraft:diamond_sword": { "skill": "levelrpg:swords", "minLevel": 10 },
 *   "#minecraft:tool": { "skill": "levelrpg:mining", "minLevel": 5 }
 * }
 *
 * Tag entries (prefixed with '#') are expanded into concrete item entries using Minecraft's
 * built-in tag system during reload. No custom tag jsons are required for this to function.
 */
public class GateRules extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public GateRules() {
        super(GSON, "gates");
    }

    public static final class ItemGate {
        public final ResourceLocation skill;
        public final int minLevel;
        public ItemGate(ResourceLocation skill, int minLevel) {
            this.skill = skill; this.minLevel = minLevel;
        }
    }

    // For items, we keep two maps: exact ids and tag ids (prefixed with '#')
    private static final LinkedHashMap<ResourceLocation, ItemGate> ITEM_RULES = new LinkedHashMap<>();
    private static final LinkedHashMap<ResourceLocation, ItemGate> ITEM_TAG_RULES = new LinkedHashMap<>();

    public static Map<ResourceLocation, ItemGate> itemIdRules() { return ITEM_RULES; }
    public static Map<ResourceLocation, ItemGate> itemTagRules() { return ITEM_TAG_RULES; }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        ITEM_RULES.clear();
        ITEM_TAG_RULES.clear();
        int expandedFromTags = 0;
        // We only care about files whose path ends with "items" (e.g., levelrpg:gates/items)
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            if (!fileId.getPath().endsWith("items")) continue;
            try {
                JsonElement el = entry.getValue();
                if (!el.isJsonObject()) continue;
                JsonObject root = el.getAsJsonObject();
                for (Map.Entry<String, JsonElement> ruleEntry : root.entrySet()) {
                    String key = ruleEntry.getKey();
                    JsonObject val = ruleEntry.getValue().getAsJsonObject();
                    ResourceLocation skill = net.zoogle.levelrpg.util.IdUtil.parseWithDefaultNamespace(val.get("skill").getAsString(), net.zoogle.levelrpg.LevelRPG.MODID);
                    int minLevel = Math.max(0, val.get("minLevel").getAsInt());
                    ItemGate gate = new ItemGate(skill, minLevel);
                    if (key.startsWith("#")) {
                        ResourceLocation tagId = ResourceLocation.parse(key.substring(1));
                        ITEM_TAG_RULES.put(tagId, gate); // keep for debugging/fallback
                        // Expand tag members into concrete items now
                        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                        var opt = BuiltInRegistries.ITEM.getTag(tagKey);
                        if (opt.isPresent()) {
                            var holderSet = opt.get();
                            for (var holder : holderSet) {
                                Item item = holder.value();
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                                // Do not override explicit item rules defined elsewhere
                                ITEM_RULES.putIfAbsent(itemId, gate);
                                expandedFromTags++;
                            }
                        } else {
                            System.err.println("[LevelRPG] GateRules: Tag not found: " + tagId + " while expanding gates.");
                        }
                    } else {
                        ResourceLocation itemId = ResourceLocation.parse(key);
                        ITEM_RULES.put(itemId, gate);
                    }
                }
            } catch (Exception ex) {
                System.err.println("[LevelRPG] Failed to parse gate json " + fileId + ": " + ex);
            }
        }
        System.out.println("[LevelRPG] Loaded gate rules: items ids=" + ITEM_RULES.size() + ", item tags=" + ITEM_TAG_RULES.size() + ", expandedItems=" + expandedFromTags);
    }
}
