package net.zoogle.levelrpg.client.ui.skilltree.editor;

import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Editor-only presentation state for skill tree navigation.</b>
 *
 * <p>Holds the runtime rotation/animation state for the "revolving chamber" wheel shown inside
 * {@link net.zoogle.levelrpg.client.ui.SkillTreeEditorScreen}. The wheel arranges skill-tree labels
 * around an off-screen hub and lets the developer spin between skills with A/D.
 *
 * <p>This class is strictly part of the developer/editor tool and is <em>not</em> used by the
 * player-facing Enchiridion projection pipeline. It does not own the canonical skill tree data;
 * it only manages the visual selection and transition state for the editor's navigation wheel.
 *
 * @see SkillChamberRenderer
 * @see net.zoogle.levelrpg.client.ui.SkillTreeEditorScreen
 */
public final class SkillChamberViewState {
    private static final List<ResourceLocation> DEFAULT_SKILL_ORDER = List.of(
            skill("valor"),
            skill("finesse"),
            skill("arcana"),
            skill("delving"),
            skill("forging"),
            skill("artificing"),
            skill("hearth")
    );
    private static final double FULL_TURN = Math.PI * 2.0;
    private static final double ROTATION_SETTLE_EPSILON = 0.0025;

    private final List<ResourceLocation> orderedSkills;
    private int focusedSkillIndex;
    private double currentRotationAngle;
    private double targetRotationAngle;

    public SkillChamberViewState(ResourceLocation initialSkill) {
        this(DEFAULT_SKILL_ORDER, initialSkill);
    }

    public SkillChamberViewState(List<ResourceLocation> orderedSkills, ResourceLocation initialSkill) {
        ArrayList<ResourceLocation> order = new ArrayList<>(orderedSkills);
        int idx = order.indexOf(initialSkill);
        if (idx < 0) {
            order.add(initialSkill);
            idx = order.size() - 1;
        }
        this.orderedSkills = List.copyOf(order);
        this.focusedSkillIndex = idx;
        this.currentRotationAngle = angleForIndex(focusedSkillIndex);
        this.targetRotationAngle = currentRotationAngle;
    }

    public List<ResourceLocation> orderedSkills() {
        return orderedSkills;
    }

    public ResourceLocation getFocusedSkill() {
        return orderedSkills.get(focusedSkillIndex);
    }

    public ResourceLocation getPreviousSkill() {
        return orderedSkills.get(wrapIndex(focusedSkillIndex - 1));
    }

    public ResourceLocation getNextSkill() {
        return orderedSkills.get(wrapIndex(focusedSkillIndex + 1));
    }

    public List<ResourceLocation> visibleTreeSkillIds() {
        return List.of(getPreviousSkill(), getFocusedSkill(), getNextSkill());
    }

    public double currentRotationAngle() {
        return currentRotationAngle;
    }

    public double targetRotationAngle() {
        return targetRotationAngle;
    }

    public boolean isRotating() {
        return Math.abs(targetRotationAngle - currentRotationAngle) > ROTATION_SETTLE_EPSILON;
    }

    /**
     * 0 -> idle / just started rotating, 1 -> settled at target.
     * Uses one-sector travel as the normalized distance for chamber step rotations.
     */
    public float rotationProgress() {
        double sector = Math.max(0.0001, sectorAngle());
        double remaining = Math.abs(targetRotationAngle - currentRotationAngle);
        double normalized = 1.0 - Math.min(1.0, remaining / sector);
        return (float) Math.max(0.0, Math.min(1.0, normalized));
    }

    public boolean rotateNext() {
        return rotateTo(wrapIndex(focusedSkillIndex + 1));
    }

    public boolean rotatePrevious() {
        return rotateTo(wrapIndex(focusedSkillIndex - 1));
    }

    public void updateAnimation(float tickDelta) {
        double difference = targetRotationAngle - currentRotationAngle;
        if (Math.abs(difference) <= ROTATION_SETTLE_EPSILON) {
            currentRotationAngle = targetRotationAngle;
            return;
        }
        double step = Math.min(1.0, Math.max(0.0, tickDelta) * 0.24);
        currentRotationAngle += difference * step;
    }

    public double getSkillAngle(ResourceLocation skillId) {
        int index = orderedSkills.indexOf(skillId);
        if (index < 0) {
            return currentRotationAngle;
        }
        return index * sectorAngle() - currentRotationAngle;
    }

    public ScreenPosition getSkillScreenPosition(ResourceLocation skillId, Viewport viewport) {
        double angle = getSkillAngle(skillId);
        double radius = wheelRadius(viewport);
        int cx = wheelCenterScreenX(viewport);
        int cy = wheelCenterScreenY(viewport);
        int x = cx + (int) Math.round(Math.sin(angle) * radius);
        int y = cy + (int) Math.round(Math.cos(angle) * radius);
        return new ScreenPosition(x, y, angle);
    }

    public ScreenPosition getSkillTreeScreenPosition(ResourceLocation skillId, Viewport viewport) {
        return getSkillScreenPosition(skillId, viewport);
    }

    public double sectorAngle() {
        return FULL_TURN / orderedSkills.size();
    }

    private boolean rotateTo(int nextIndex) {
        if (nextIndex == focusedSkillIndex) {
            return false;
        }
        focusedSkillIndex = nextIndex;
        targetRotationAngle = closestEquivalentAngle(currentRotationAngle, angleForIndex(focusedSkillIndex));
        return true;
    }

    private double angleForIndex(int index) {
        return index * sectorAngle();
    }

    /** Wheel arc radius in pixels; shared by labels, spokes, tree offsets, and chamber decoration. */
    public static double wheelRadius(Viewport viewport) {
        return Math.max(viewport.width() * 0.68, viewport.height() * 0.86);
    }

    public static int wheelCenterScreenX(Viewport viewport) {
        return viewport.x() + viewport.width() / 2;
    }

    /** Screen Y of the wheel hub (above the viewport); spoke endpoints and labels use this. */
    public static int wheelCenterScreenY(Viewport viewport) {
        return viewport.y() + viewport.height() / 2 - (int) Math.round(wheelRadius(viewport));
    }

    public static int wheelRadiusPixels(Viewport viewport) {
        return (int) Math.round(wheelRadius(viewport));
    }

    private int wrapIndex(int index) {
        return Math.floorMod(index, orderedSkills.size());
    }

    private static double closestEquivalentAngle(double current, double target) {
        while (target - current > Math.PI) {
            target -= FULL_TURN;
        }
        while (target - current < -Math.PI) {
            target += FULL_TURN;
        }
        return target;
    }

    private static ResourceLocation skill(String path) {
        return ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, path);
    }

    public record Viewport(int x, int y, int width, int height) {
    }

    public record ScreenPosition(int x, int y, double angle) {
    }
}
