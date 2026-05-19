package net.zoogle.levelrpg.client.skilltree;

import net.zoogle.levelrpg.skilltree.SkillNodeStatus;

import java.util.List;

/**
 * Neutral viewport controller for skill tree camera math, pan/zoom state, and hit testing.
 *
 * <p>This class deliberately does not know about player-vs-editor rules, unlocking,
 * editor widgets, networking, or chamber animation. Callers decide what a click or drag means.
 */
public final class SkillTreeViewportController {
    private final SkillTreeInputState input = new SkillTreeInputState();
    private static final double CAMERA_SNAP_EPSILON = 0.35;
    private static final double CAMERA_ACCELERATION = 0.16;
    private static final double CAMERA_VELOCITY_RETAIN = 0.55;
    private static final double CAMERA_MAX_STEP = 72.0;
    private static final double ZOOM_SNAP_EPSILON = 0.002;
    private static final double ZOOM_ACCELERATION = 0.18;
    private static final double ZOOM_VELOCITY_RETAIN = 0.45;
    private static final double ZOOM_MAX_STEP = 0.10;

    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
    private boolean dragExceededClickThreshold;
    private boolean cameraAnimating;
    private double targetPanX;
    private double targetPanY;
    private float targetZoom;
    private double velocityPanX;
    private double velocityPanY;
    private double velocityZoom;

    public void setViewport(int x, int y, int width, int height) {
        viewportX = x;
        viewportY = y;
        viewportW = width;
        viewportH = height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= viewportX
                && mouseX < viewportX + viewportW
                && mouseY >= viewportY
                && mouseY < viewportY + viewportH;
    }

    public void press(double mouseX, double mouseY) {
        dragExceededClickThreshold = false;
        input.onPress(mouseX, mouseY);
    }

    public boolean drag(double mouseX, double mouseY) {
        boolean dragging = input.onDrag(mouseX, mouseY);
        if (dragging) {
            cancelCameraAnimation();
            dragExceededClickThreshold = true;
        }
        return dragging;
    }

    public void release() {
        input.onRelease();
    }

    public boolean isDragging() {
        return input.isDragging();
    }

    public boolean didDragBeyondClickThreshold() {
        return dragExceededClickThreshold;
    }

    public void panVertically(double amount) {
        cancelCameraAnimation();
        input.panVertically(amount);
    }

    public void zoomAround(double mouseX, double mouseY, double scrollDelta) {
        cancelCameraAnimation();
        input.zoomAround(mouseX, mouseY, viewportX, viewportY, scrollDelta);
    }

    public void fitToScene(SkillTreeScene scene) {
        if (scene == null || !scene.hasNodes()) {
            return;
        }
        fitToScene(scene, 72, 48, 82, 48);
    }

    public void fitToScene(SkillTreeScene scene, int topPadding, int rightPadding, int bottomPadding, int leftPadding) {
        if (scene == null || !scene.hasNodes()) {
            return;
        }
        fitToBounds(visualBounds(scene), topPadding, rightPadding, bottomPadding, leftPadding, SkillTreeInputState.MIN_ZOOM, SkillTreeInputState.MAX_ZOOM);
    }

    public void fitToScene(SkillTreeScene scene, int topPadding, int rightPadding, int bottomPadding, int leftPadding, float maxZoom) {
        if (scene == null || !scene.hasNodes()) {
            return;
        }
        fitToBounds(visualBounds(scene), topPadding, rightPadding, bottomPadding, leftPadding, SkillTreeInputState.MIN_ZOOM, maxZoom);
    }

    public void animateToNodes(List<SkillTreeNodeView> nodes, int topPadding, int rightPadding, int bottomPadding, int leftPadding, float minZoom, float maxZoom) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        CameraTarget target = targetForBounds(visualBounds(nodes), topPadding, rightPadding, bottomPadding, leftPadding, minZoom, maxZoom);
        targetPanX = target.panX();
        targetPanY = target.panY();
        targetZoom = target.zoom();
        double distance = Math.hypot(targetPanX - input.panX(), targetPanY - input.panY());
        if (distance <= CAMERA_SNAP_EPSILON && Math.abs(targetZoom - input.zoom()) <= ZOOM_SNAP_EPSILON) {
            input.setCamera(targetPanX, targetPanY, targetZoom);
            cameraAnimating = false;
            velocityPanX = 0.0;
            velocityPanY = 0.0;
            velocityZoom = 0.0;
            return;
        }
        cameraAnimating = true;
    }

    private void fitToBounds(VisualBounds bounds, int topPadding, int rightPadding, int bottomPadding, int leftPadding, float minZoom, float maxZoom) {
        CameraTarget target = targetForBounds(bounds, topPadding, rightPadding, bottomPadding, leftPadding, minZoom, maxZoom);
        cancelCameraAnimation();
        input.setCamera(target.panX(), target.panY(), target.zoom());
    }

    private CameraTarget targetForBounds(VisualBounds bounds, int topPadding, int rightPadding, int bottomPadding, int leftPadding, float minZoom, float maxZoom) {
        int availableW = Math.max(40, viewportW - leftPadding - rightPadding);
        int availableH = Math.max(40, viewportH - topPadding - bottomPadding);
        float fitZoom = (float) Math.min(
                availableW / Math.max(1.0, bounds.width()),
                availableH / Math.max(1.0, bounds.height())
        );
        fitZoom = clamp(fitZoom, minZoom, maxZoom);

        double targetLocalX = leftPadding + availableW / 2.0;
        double targetLocalY = topPadding + availableH / 2.0;
        double panX = targetLocalX - bounds.centerX() * fitZoom;
        double panY = targetLocalY - bounds.centerY() * fitZoom;
        return new CameraTarget(panX, panY, fitZoom);
    }

    public void focusOnNode(SkillTreeNodeView node) {
        focusOnNode(node, 72, 48, 82, 48);
    }

    public void focusOnNode(SkillTreeNodeView node, int topPadding, int rightPadding, int bottomPadding, int leftPadding) {
        if (node == null) {
            return;
        }
        int availableW = Math.max(40, viewportW - leftPadding - rightPadding);
        int availableH = Math.max(40, viewportH - topPadding - bottomPadding);
        double targetLocalX = leftPadding + availableW / 2.0;
        double targetLocalY = topPadding + availableH / 2.0;
        float zoom = input.zoom();
        targetPanX = targetLocalX - node.x() * zoom;
        targetPanY = targetLocalY - node.y() * zoom;
        targetZoom = zoom;
        double distance = Math.hypot(targetPanX - input.panX(), targetPanY - input.panY());
        if (distance <= CAMERA_SNAP_EPSILON) {
            input.setCamera(targetPanX, targetPanY, input.zoom());
            cameraAnimating = false;
            velocityPanX = 0.0;
            velocityPanY = 0.0;
            velocityZoom = 0.0;
            return;
        }
        cameraAnimating = true;
    }

    public void tickCameraAnimation() {
        if (!cameraAnimating) {
            return;
        }
        double dx = targetPanX - input.panX();
        double dy = targetPanY - input.panY();
        double dz = targetZoom - input.zoom();
        double distance = Math.hypot(dx, dy);
        if (distance <= CAMERA_SNAP_EPSILON && Math.abs(dz) <= ZOOM_SNAP_EPSILON) {
            input.setCamera(targetPanX, targetPanY, targetZoom);
            cameraAnimating = false;
            velocityPanX = 0.0;
            velocityPanY = 0.0;
            velocityZoom = 0.0;
            return;
        }

        velocityPanX = velocityPanX * CAMERA_VELOCITY_RETAIN + dx * CAMERA_ACCELERATION;
        velocityPanY = velocityPanY * CAMERA_VELOCITY_RETAIN + dy * CAMERA_ACCELERATION;
        velocityZoom = velocityZoom * ZOOM_VELOCITY_RETAIN + dz * ZOOM_ACCELERATION;

        double speed = Math.hypot(velocityPanX, velocityPanY);
        if (speed > 0.0) {
            double maxStep = Math.min(CAMERA_MAX_STEP, Math.max(1.0, distance * 0.65));
            maxStep = Math.min(maxStep, distance);
            if (speed > maxStep) {
                double scale = maxStep / speed;
                velocityPanX *= scale;
                velocityPanY *= scale;
            }
        }
        if (Math.abs(velocityZoom) > ZOOM_MAX_STEP) {
            velocityZoom = Math.copySign(ZOOM_MAX_STEP, velocityZoom);
        }
        if (Math.abs(velocityZoom) > Math.abs(dz)) {
            velocityZoom = dz;
        }

        double nextPanX = input.panX() + velocityPanX;
        double nextPanY = input.panY() + velocityPanY;
        float nextZoom = (float) (input.zoom() + velocityZoom);
        input.setCamera(nextPanX, nextPanY, nextZoom);
    }

    public void cancelCameraAnimation() {
        cameraAnimating = false;
        velocityPanX = 0.0;
        velocityPanY = 0.0;
        velocityZoom = 0.0;
    }

    public SkillTreeNodeView hoveredNode(SkillTreeScene scene, double mouseX, double mouseY) {
        if (scene == null || !contains(mouseX, mouseY)) {
            return null;
        }
        SkillTreeCameraTransform transform = transform();
        List<SkillTreeNodeView> nodeViews = scene.nodeViews();
        for (int i = nodeViews.size() - 1; i >= 0; i--) {
            SkillTreeNodeView view = nodeViews.get(i);
            if (view.status() != SkillNodeStatus.HIDDEN && view.contains(mouseX, mouseY, transform)) {
                return view;
            }
        }
        return null;
    }

    public SkillTreeCameraTransform transform() {
        return input.transform(viewportX, viewportY);
    }

    public double screenToGraphX(double screenX) {
        return transform().screenToGraphX(screenX);
    }

    public double screenToGraphY(double screenY) {
        return transform().screenToGraphY(screenY);
    }

    public int graphToScreenX(double graphX) {
        return transform().graphToScreenX(graphX);
    }

    public int graphToScreenY(double graphY) {
        return transform().graphToScreenY(graphY);
    }

    public float zoom() {
        return input.zoom();
    }

    private static VisualBounds visualBounds(SkillTreeScene scene) {
        return visualBounds(scene.nodeViews());
    }

    private static VisualBounds visualBounds(List<SkillTreeNodeView> nodes) {
        boolean found = false;
        double minX = 0.0;
        double minY = 0.0;
        double maxX = 0.0;
        double maxY = 0.0;
        for (SkillTreeNodeView view : nodes) {
            if (!view.rendered() || view.status() == SkillNodeStatus.HIDDEN) {
                continue;
            }
            double pad = view.visualPadding();
            double half = view.size() / 2.0 + pad;
            double left = view.x() - half;
            double top = view.y() - half;
            double right = view.x() + half;
            double bottom = view.y() + half;
            if (!found) {
                minX = left;
                minY = top;
                maxX = right;
                maxY = bottom;
                found = true;
            } else {
                minX = Math.min(minX, left);
                minY = Math.min(minY, top);
                maxX = Math.max(maxX, right);
                maxY = Math.max(maxY, bottom);
            }
        }
        if (!found) {
            return new VisualBounds(0.0, 0.0, 0.0, 0.0);
        }
        return new VisualBounds(minX, minY, maxX, maxY);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record VisualBounds(double minX, double minY, double maxX, double maxY) {
        double width() {
            return maxX - minX;
        }

        double height() {
            return maxY - minY;
        }

        double centerX() {
            return (minX + maxX) / 2.0;
        }

        double centerY() {
            return (minY + maxY) / 2.0;
        }
    }

    private record CameraTarget(double panX, double panY, float zoom) {
    }
}
