package net.zoogle.levelrpg.technique;

public record TechniqueResult(boolean success, String message) {
    public static TechniqueResult success(String message) {
        return new TechniqueResult(true, message == null ? "Activated" : message);
    }

    public static TechniqueResult failure(String message) {
        return new TechniqueResult(false, message == null ? "Technique failed" : message);
    }
}
