package net.zoogle.levelrpg.client.skilltree;

public record SkillTreeCameraTransform(int viewportX, int viewportY, double panX, double panY, float zoom) {
    public int graphToScreenX(double graphX) {
        return (int) Math.round(viewportX + panX + graphX * zoom);
    }

    public int graphToScreenY(double graphY) {
        return (int) Math.round(viewportY + panY + graphY * zoom);
    }

    public double screenToGraphX(double screenX) {
        return (screenX - viewportX - panX) / zoom;
    }

    public double screenToGraphY(double screenY) {
        return (screenY - viewportY - panY) / zoom;
    }

    public int scaledSize(int graphSize) {
        return Math.max(1, (int) Math.round(graphSize * zoom));
    }
}
