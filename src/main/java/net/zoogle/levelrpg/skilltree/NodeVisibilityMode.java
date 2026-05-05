package net.zoogle.levelrpg.skilltree;

public enum NodeVisibilityMode {
    VISIBLE("visible"),
    HIDDEN("hidden"),
    OBFUSCATED("obfuscated");

    private final String jsonName;

    NodeVisibilityMode(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    public static NodeVisibilityMode fromJson(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        for (NodeVisibilityMode mode : values()) {
            if (mode.jsonName.equals(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return VISIBLE;
    }
}
