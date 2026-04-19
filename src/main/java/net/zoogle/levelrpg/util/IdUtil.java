package net.zoogle.levelrpg.util;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.data.SkillRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Utility for flexible parsing of IDs coming from user/datapack input.
 * Supports:
 * - Plain path without namespace (defaults to our mod id)
 * - Case-insensitive matches to known skill paths
 * - Fuzzy autocorrect using Levenshtein distance (threshold 2)
 */
public final class IdUtil {
    private IdUtil() {}

    public record ResolveResult(ResourceLocation id, String correctedFrom) {}

    public static ResolveResult resolveSkill(String input) {
        if (input == null) return null;
        String raw = input.trim();
        if (raw.isEmpty()) return null;

        // If it looks like a full id, try parsing directly
        if (raw.indexOf(':') >= 0) {
            try {
                ResourceLocation id = ResourceLocation.parse(raw);
                return new ResolveResult(id, null);
            } catch (Exception ignored) {}
        }

        // Try as our namespace
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, raw);
            // If it exists among known skills, accept without correction note
            if (isKnownSkill(id)) return new ResolveResult(id, null);
        } catch (Exception ignored) {}

        // Collect all known skill ids (from registry only)
        var known = new ArrayList<ResourceLocation>();
        for (ResourceLocation id : SkillRegistry.ids()) known.add(id);

        // 1) Case-insensitive path match
        String pathLower = raw.toLowerCase(Locale.ROOT);
        for (ResourceLocation id : known) {
            if (id.getPath().equalsIgnoreCase(raw)) {
                return new ResolveResult(id, raw);
            }
        }

        // 2) Fuzzy match on path within distance 2
        ResourceLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (ResourceLocation id : known) {
            int d = levenshtein(pathLower, id.getPath().toLowerCase(Locale.ROOT));
            if (d < bestDist) {
                bestDist = d;
                best = id;
            }
        }
        int threshold = Math.max(0, net.zoogle.levelrpg.Config.autocorrectMaxDistance);
        if (best != null && bestDist <= threshold) {
            return new ResolveResult(best, raw);
        }

        // Fallback to our namespace even if unknown
        try {
            return new ResolveResult(ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, raw), raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static ResourceLocation parseWithDefaultNamespace(String input, String defaultNs) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        if (s.indexOf(':') >= 0) {
            return ResourceLocation.parse(s);
        }
        return ResourceLocation.fromNamespaceAndPath(defaultNs, s);
    }

    private static boolean isKnownSkill(ResourceLocation id) {
        for (ResourceLocation known : SkillRegistry.ids()) {
            if (known.equals(id)) return true;
        }
        return false;
    }

    // Simple Levenshtein distance
    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[m];
    }
}
