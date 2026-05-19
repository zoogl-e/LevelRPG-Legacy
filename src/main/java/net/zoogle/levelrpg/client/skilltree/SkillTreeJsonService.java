package net.zoogle.levelrpg.client.skilltree;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * File IO boundary for the developer skill-tree editor.
 *
 * <p>The editor preserves non-node metadata while writing the current node schema. New saves prefer
 * node {@code "requirement"} objects; legacy {@code "requires"} arrays are compatibility output only
 * when they are equivalent to the richer requirement.
 */
public final class SkillTreeJsonService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public JsonObject readSourceJson(ResourceLocation skillId) {
        Path path = sourceJsonPath(skillId);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            LOGGER.warn("Failed to read existing skill tree json {}", path, ex);
            return null;
        }
    }

    public Path writeDraft(ResourceLocation skillId, String json) throws IOException {
        Path output = sourceJsonPath(skillId);
        if (Files.exists(output)) {
            Files.copy(output, Path.of(output.toString() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(output, json, StandardCharsets.UTF_8);
        return output;
    }

    public Path sourceJsonPath(ResourceLocation skillId) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path projectRoot = cwd;
        if (!Files.exists(projectRoot.resolve("src/main/resources")) && cwd.getParent() != null) {
            Path parent = cwd.getParent();
            if (Files.exists(parent.resolve("src/main/resources"))) {
                projectRoot = parent;
            }
        }
        return projectRoot.resolve(Path.of("src", "main", "resources", "data", LevelRPG.MODID, "skill_trees", skillId.getPath() + ".json"));
    }
}
