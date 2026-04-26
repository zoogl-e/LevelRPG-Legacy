package net.zoogle.levelrpg.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.zoogle.levelrpg.LevelRPG;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

public class XpCurves extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LinkedHashMap<ResourceLocation, XpCurveDefinition> REGISTRY = new LinkedHashMap<>();

    public XpCurves() {
        super(GSON, "xp_curves");
    }

    public static XpCurveDefinition get(ResourceLocation id) { return REGISTRY.get(id); }
    public static int size() { return REGISTRY.size(); }

    public static long computeNeeded(ResourceLocation curveId, int level) {
        // Default polynomial: needed = 28 + 12 * L
        XpCurveDefinition def = curveId != null ? REGISTRY.get(curveId) : null;
        if (def == null) {
            // Try default key
            def = REGISTRY.get(ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "default"));
            if (def == null) {
                double b = 12.0;
                double c = 28.0;
                return Math.max(1L, Math.round(b * level + c));
            }
        }
        if ("poly".equalsIgnoreCase(def.type())) {
            double needed = def.b() * level + def.c();
            return Math.max(1L, Math.round(needed));
        }
        // Unknown types fall back to default poly-like behavior
        double b = 12.0;
        double c = 28.0;
        return Math.max(1L, Math.round(b * level + c));
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<ResourceLocation, XpCurveDefinition> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = entry.getValue().getAsJsonObject();
                String idStr = root.has("id") ? root.get("id").getAsString() : (LevelRPG.MODID + ":" + fileId.getPath());
                ResourceLocation id = ResourceLocation.parse(idStr);
                String type = root.has("type") ? root.get("type").getAsString() : "poly";
                double b = root.has("b") ? root.get("b").getAsDouble() : 12.0;
                double c = root.has("c") ? root.get("c").getAsDouble() : 28.0;
                loaded.put(id, new XpCurveDefinition(id, type, b, c));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse xp curve json {}", fileId, ex);
            }
        }
        REGISTRY.clear();
        REGISTRY.putAll(loaded);
        // Ensure a sensible default exists
        ResourceLocation defId = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "default");
        REGISTRY.putIfAbsent(defId, new XpCurveDefinition(defId, "poly", 12.0, 28.0));
        LOGGER.info("Loaded {} XP curves from datapacks (including defaults).", REGISTRY.size());
    }
}
