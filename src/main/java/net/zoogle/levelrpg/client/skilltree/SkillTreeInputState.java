package net.zoogle.levelrpg.client.skilltree;

public final class SkillTreeInputState {
    public static final float MIN_ZOOM = 0.35f;
    public static final float MAX_ZOOM = 2.25f;

    /** Pixels the mouse must move before a press becomes a drag. */
    private static final double DRAG_THRESHOLD = 4.0;

    private double panX;
    private double panY;
    private float zoom = 1.0f;

    // Drag state ─────────────────────────────────────────────────────────────
    /** True once the mouse has passed DRAG_THRESHOLD from the press origin. */
    private boolean dragActive;
    /** Set to true when a viewport press is registered; cleared on release. */
    private boolean pressActive;
    private double pressX;
    private double pressY;
    private double lastDragX;
    private double lastDragY;

    // ─────────────────────────────────────────────────────────────────────────

    public double panX() { return panX; }
    public double panY() { return panY; }
    public float zoom() { return zoom; }
    public boolean isDragging() { return dragActive; }

    // ── Camera ───────────────────────────────────────────────────────────────

    public void centerOn(int viewportWidth, int viewportHeight, int minX, int minY, int maxX, int maxY) {
        int contentCenterX = (minX + maxX) / 2;
        int contentCenterY = (minY + maxY) / 2;
        this.panX = viewportWidth / 2.0 - contentCenterX * zoom;
        this.panY = viewportHeight / 2.0 - contentCenterY * zoom;
    }

    public void setCamera(double panX, double panY, float zoom) {
        this.panX = panX;
        this.panY = panY;
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    // ── Press / drag / release ────────────────────────────────────────────────

    /**
     * Call this when the left mouse button goes down inside the viewport.
     * Does NOT start a drag immediately; drag begins lazily in {@link #onDrag}.
     */
    public void onPress(double mouseX, double mouseY) {
        pressActive = true;
        dragActive = false;
        pressX = mouseX;
        pressY = mouseY;
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    /**
     * Call this from {@code mouseDragged}.
     *
     * @return true if a drag is active (the caller should consume the event),
     *         false if the mouse has not yet moved past the threshold.
     */
    public boolean onDrag(double mouseX, double mouseY) {
        if (!pressActive) {
            return false;
        }
        if (!dragActive) {
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (dx * dx + dy * dy < DRAG_THRESHOLD * DRAG_THRESHOLD) {
                return false; // still within click slop — not a drag yet
            }
            // Threshold crossed: apply this event's movement immediately so
            // panning does not feel like it starts one frame late.
            dragActive = true;
        }
        panX += mouseX - lastDragX;
        panY += mouseY - lastDragY;
        lastDragX = mouseX;
        lastDragY = mouseY;
        return true;
    }

    /**
     * Call this when the left mouse button is released.
     */
    public void onRelease() {
        pressActive = false;
        dragActive = false;
    }

    // Legacy shims kept so editor-mode node-drag code compiles without changes.
    public void beginDrag(double mouseX, double mouseY) { onPress(mouseX, mouseY); }
    public void dragTo(double mouseX, double mouseY) { onDrag(mouseX, mouseY); }
    public void endDrag() { onRelease(); }

    // ─────────────────────────────────────────────────────────────────────────

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

    public SkillTreeCameraTransform transform(int viewportX, int viewportY) {
        return new SkillTreeCameraTransform(viewportX, viewportY, panX, panY, zoom);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
