package net.zoogle.levelrpg.client.skilltree;

public final class SkillTreeInputState {
    public static final float MIN_ZOOM = 0.55f;
    public static final float MAX_ZOOM = 2.25f;

    private double panX;
    private double panY;
    private float zoom = 1.0f;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    public double panX() {
        return panX;
    }

    public double panY() {
        return panY;
    }

    public float zoom() {
        return zoom;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void centerOn(int viewportWidth, int viewportHeight, int minX, int minY, int maxX, int maxY) {
        int contentCenterX = (minX + maxX) / 2;
        int contentCenterY = (minY + maxY) / 2;
        this.panX = viewportWidth / 2.0 - contentCenterX * zoom;
        this.panY = Math.max(54.0, viewportHeight / 2.0 - contentCenterY * zoom);
    }

    public void beginDrag(double mouseX, double mouseY) {
        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public void dragTo(double mouseX, double mouseY) {
        if (!dragging) {
            return;
        }
        panX += mouseX - lastMouseX;
        panY += mouseY - lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    public void panVertically(double amount) {
        panY += amount;
    }

    public void zoomAround(double mouseX, double mouseY, int viewportX, int viewportY, double scrollDelta) {
        float oldZoom = zoom;
        float nextZoom = clamp((float) (zoom + scrollDelta * 0.12f), MIN_ZOOM, MAX_ZOOM);
        if (Math.abs(nextZoom - oldZoom) < 0.0001f) {
            return;
        }

        double localX = mouseX - viewportX;
        double localY = mouseY - viewportY;
        double graphX = (localX - panX) / oldZoom;
        double graphY = (localY - panY) / oldZoom;
        zoom = nextZoom;
        panX = localX - graphX * zoom;
        panY = localY - graphY * zoom;
    }

    public void endDrag() {
        dragging = false;
    }

    public SkillTreeCameraTransform transform(int viewportX, int viewportY) {
        return new SkillTreeCameraTransform(viewportX, viewportY, panX, panY, zoom);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
