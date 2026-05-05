package net.zoogle.levelrpg.client.finesse;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class FinesseSegmentedUnarmedArmRenderer {
    private static final float SKIN_SIZE = 64.0F;
    private static final float SLEEVE_WIDTH_SCALE = 1.04F;
    private static final float SLEEVE_HEIGHT_SCALE = 1.00F;
    private static final float SLEEVE_DEPTH_SCALE = 1.04F;
    private static final float UV_EDGE_INSET = 0.04F;
    private static final float WIDE_ARM_WIDTH = 0.28F;
    private static final float SLIM_ARM_WIDTH = 0.21F;
    private static final float WIDE_ARM_DEPTH = 0.26F;
    private static final float SLIM_ARM_DEPTH = 0.24F;

    private FinesseSegmentedUnarmedArmRenderer() {
    }

    public static void renderGuardArm(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            HumanoidArm arm,
            float equippedProgress,
            float readyProgress,
            float guardProgress,
            float swingProgress,
            float chargeProgress
    ) {
        boolean right = arm != HumanoidArm.LEFT;
        float side = right ? 1.0F : -1.0F;
        float punchProgress = punchProgress(swingProgress);
        float punchRecover = Mth.sin(swingProgress * (float) Math.PI);
        boolean slim = isSlim(player);
        float armWidth = slim ? SLIM_ARM_WIDTH : WIDE_ARM_WIDTH;
        float armDepth = slim ? SLIM_ARM_DEPTH : WIDE_ARM_DEPTH;
        SkinBox baseUvs = armUvs(right, slim, false);
        SkinBox overlayUvs = armUvs(right, slim, true);

        float chargeY = right ? chargeProgress * -0.65F : 0.0F;
        float chargeZ = right ? chargeProgress * 0.20F : 0.0F;

        poseStack.pushPose();
        poseStack.translate(
                layeredLerp(readyProgress, guardProgress, side * 0.64F, side * 0.58F, side * 0.54F)
                        + side * lerp(guardProgress, -0.18F, -0.06F) * punchProgress,
                layeredLerp(readyProgress, guardProgress, -1.18F, -0.86F, -0.50F)
                        + equippedProgress * -0.08F
                        + lerp(guardProgress, 0.04F, -0.03F) * punchRecover
                        + chargeY,
                layeredLerp(readyProgress, guardProgress, -0.82F, -0.78F, -0.72F)
                        + lerp(guardProgress, -0.48F, -0.22F) * punchProgress
                        + chargeZ
        );
        rotate(
                poseStack,
                0.0F,
                1.0F,
                0.0F,
                layeredLerp(readyProgress, guardProgress, side * -3.0F, side * -9.0F, side * -18.0F)
                        + side * lerp(guardProgress, 10.0F, 4.0F) * punchProgress
                        + (right ? chargeProgress * side * -15.0F : 0.0F)
        );
        rotate(
                poseStack,
                0.0F,
                0.0F,
                1.0F,
                layeredLerp(readyProgress, guardProgress, side * 2.0F, side * 8.0F, side * 18.0F)
                        + side * -3.0F * punchRecover
                        + (right ? chargeProgress * side * 25.0F : 0.0F)
        );

        renderSegment(
                poseStack,
                buffer,
                packedLight,
                player,
                armWidth,
                1.18F,
                armDepth,
                new SegmentPose(
                        layeredLerp(readyProgress, guardProgress, side * -0.02F, side * -0.05F, side * -0.10F),
                        layeredLerp(readyProgress, guardProgress, 0.06F, 0.02F, -0.02F)
                                + lerp(guardProgress, 0.02F, -0.02F) * punchProgress,
                        layeredLerp(readyProgress, guardProgress, 0.04F, 0.02F, -0.02F)
                                + lerp(guardProgress, -0.14F, -0.06F) * punchProgress,
                        layeredLerp(readyProgress, guardProgress, -2.0F, -10.0F, -26.0F)
                                + lerp(guardProgress, 82.0F, 48.0F) * punchProgress,
                        layeredLerp(readyProgress, guardProgress, 180.0F + side * -2.0F, 180.0F + side * -4.0F, 180.0F + side * -10.0F)
                                + side * lerp(guardProgress, 6.0F, 3.0F) * punchProgress,
                        layeredLerp(readyProgress, guardProgress, side * 3.0F, side * 7.0F, side * 14.0F)
                                + side * -3.0F * punchRecover
                ),
                baseUvs,
                overlayUvs
        );
        poseStack.popPose();
    }

    private static float lerp(float progress, float from, float to) {
        return Mth.lerp(progress, from, to);
    }

    private static float layeredLerp(float readyProgress, float guardProgress, float idle, float ready, float guard) {
        return lerp(guardProgress, lerp(readyProgress, idle, ready), guard);
    }

    private static float punchProgress(float swingProgress) {
        if (swingProgress <= 0.0F) {
            return 0.0F;
        }
        return Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
    }

    private static boolean isSlim(AbstractClientPlayer player) {
        return player.getSkin().model() == PlayerSkin.Model.SLIM;
    }

    private static void renderSegment(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float width,
            float height,
            float depth,
            SegmentPose pose,
            SkinBox baseUvs,
            SkinBox overlayUvs
    ) {
        poseStack.pushPose();
        poseStack.translate(pose.x(), pose.y(), pose.z());
        rotate(poseStack, 0.0F, 0.0F, 1.0F, pose.zRot());
        rotate(poseStack, 0.0F, 1.0F, 0.0F, pose.yRot());
        rotate(poseStack, 1.0F, 0.0F, 0.0F, pose.xRot());
        poseStack.translate(0.0F, height * 0.5F, 0.0F);

        VertexConsumer base = buffer.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        drawBox(poseStack, base, packedLight, width, height, depth, baseUvs);
        VertexConsumer overlay = buffer.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        drawBox(
                poseStack,
                overlay,
                packedLight,
                width * SLEEVE_WIDTH_SCALE,
                height * SLEEVE_HEIGHT_SCALE,
                depth * SLEEVE_DEPTH_SCALE,
                overlayUvs
        );
        poseStack.popPose();
    }

    private static void drawBox(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            float width,
            float height,
            float depth,
            SkinBox uvs
    ) {
        float minX = -width * 0.5F;
        float maxX = width * 0.5F;
        float minY = -height;
        float maxY = 0.0F;
        float minZ = -depth * 0.5F;
        float maxZ = depth * 0.5F;
        Matrix4f matrix = poseStack.last().pose();

        quad(consumer, matrix, packedLight, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, uvs.front(), 0.0F, 0.0F, 1.0F);
        quad(consumer, matrix, packedLight, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, uvs.back(), 0.0F, 0.0F, -1.0F);
        quad(consumer, matrix, packedLight, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, uvs.left(), -1.0F, 0.0F, 0.0F);
        quad(consumer, matrix, packedLight, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, uvs.right(), 1.0F, 0.0F, 0.0F);
        quad(consumer, matrix, packedLight, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, uvs.top(), 0.0F, 1.0F, 0.0F);
        quad(consumer, matrix, packedLight, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, uvs.bottom(), 0.0F, -1.0F, 0.0F);
    }

    private static SkinBox armUvs(boolean right, boolean slim, boolean overlay) {
        float u = right ? 40.0F : (overlay ? 48.0F : 32.0F);
        float v = right ? (overlay ? 32.0F : 16.0F) : 48.0F;
        float width = slim ? 3.0F : 4.0F;
        return boxUvs(u, v, width, 12.0F, 4.0F);
    }

    private static SkinBox boxUvs(float u, float v, float width, float height, float depth) {
        return new SkinBox(
                verticalFace(u + depth, v + depth, u + depth + width, v + depth + height),
                verticalFace(u + (depth * 2.0F) + width, v + depth, u + (depth * 2.0F) + (width * 2.0F), v + depth + height),
                verticalFace(u, v + depth, u + depth, v + depth + height),
                verticalFace(u + depth + width, v + depth, u + (depth * 2.0F) + width, v + depth + height),
                face(u + depth + width, v, u + depth + (width * 2.0F), v + depth),
                face(u + depth, v, u + depth + width, v + depth)
        );
    }

    private static UvFace face(float u0, float v0, float u1, float v1) {
        u0 += UV_EDGE_INSET;
        v0 += UV_EDGE_INSET;
        u1 -= UV_EDGE_INSET;
        v1 -= UV_EDGE_INSET;
        return new UvFace(u0 / SKIN_SIZE, v0 / SKIN_SIZE, u1 / SKIN_SIZE, v1 / SKIN_SIZE);
    }

    private static UvFace verticalFace(float u0, float v0, float u1, float v1) {
        u0 += UV_EDGE_INSET;
        v0 += UV_EDGE_INSET;
        u1 -= UV_EDGE_INSET;
        v1 -= UV_EDGE_INSET;
        return new UvFace(u0 / SKIN_SIZE, v1 / SKIN_SIZE, u1 / SKIN_SIZE, v0 / SKIN_SIZE);
    }

    private static void quad(
            VertexConsumer consumer,
            Matrix4f matrix,
            int packedLight,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            UvFace uv,
            float normalX,
            float normalY,
            float normalZ
    ) {
        vertex(consumer, matrix, packedLight, x1, y1, z1, uv.u0(), uv.v1(), normalX, normalY, normalZ);
        vertex(consumer, matrix, packedLight, x2, y2, z2, uv.u1(), uv.v1(), normalX, normalY, normalZ);
        vertex(consumer, matrix, packedLight, x3, y3, z3, uv.u1(), uv.v0(), normalX, normalY, normalZ);
        vertex(consumer, matrix, packedLight, x4, y4, z4, uv.u0(), uv.v0(), normalX, normalY, normalZ);
    }

    private static void vertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            int packedLight,
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ
    ) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(normalX, normalY, normalZ);
    }

    private static void rotate(PoseStack poseStack, float x, float y, float z, float degrees) {
        poseStack.mulPose(new Quaternionf(new AxisAngle4f(degrees * ((float) Math.PI / 180.0F), x, y, z)));
    }

    private record SegmentPose(float x, float y, float z, float xRot, float yRot, float zRot) {
    }

    private record SkinBox(UvFace front, UvFace back, UvFace left, UvFace right, UvFace top, UvFace bottom) {
    }

    private record UvFace(float u0, float v0, float u1, float v1) {
    }
}
