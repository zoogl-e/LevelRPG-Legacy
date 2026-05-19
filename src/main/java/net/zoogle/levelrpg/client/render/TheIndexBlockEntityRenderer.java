package net.zoogle.levelrpg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.zoogle.levelrpg.block.entity.TheIndexBlockEntity;
import org.joml.Quaternionf;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class TheIndexBlockEntityRenderer extends GeoBlockRenderer<TheIndexBlockEntity> {
    private static final ResourceLocation END_CRYSTAL_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final RenderType CRYSTAL_RENDER_TYPE = RenderType.entityCutoutNoCull(END_CRYSTAL_TEXTURE);
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4.0D);
    private static final float IDLE_CRYSTAL_SCALE = 1.0F;
    private static final float MIN_ENGAGED_CRYSTAL_SCALE = 0.3F;
    private static final float CRYSTAL_BASE_Y = 1.25F;
    private static final float BOB_AMPLITUDE = 0.026F;
    private static final float ROTATION_DEGREES_PER_TICK = 0.95F;
    private static final float CRYSTAL_WAKE_TICKS = 36.0F;
    private static final float ENGAGED_BOB_MULTIPLIER = 1.15F;
    private static final double PLAYER_REACT_RADIUS = 4.0D;
    private static final double MAX_PLAYER_OFFSET = 0.36D;
    private static final float PARTICLE_ENGAGEMENT_THRESHOLD = 0.03F;
    private static final int WAKE_BURST_PARTICLES = 24;
    private static final int ORBIT_RING_COUNT = 3;
    private static final int ORBIT_COMET_TRAIL_PARTICLES = 3;
    private static final double ORBIT_COMET_TRAIL_SPACING = 0.12D;
    private static final double ORBIT_MAJOR_RADIUS = 3.35D;
    private static final double ORBIT_MAJOR_RADIUS_STEP = 0.95D;
    private static final double ORBIT_MINOR_RADIUS = 1.05D;
    private static final double ORBIT_MINOR_RADIUS_STEP = 0.38D;
    private static final double ENCHANT_PARTICLE_FALL_COMPENSATION = 1.2D;
    private static final double CRYSTAL_PARTICLE_TARGET_Y_OFFSET = 0.72D;
    private static final int MIN_IDLE_CHIME_DELAY_TICKS = 80;
    private static final int IDLE_CHIME_DELAY_VARIANCE_TICKS = 100;

    private final ModelPart glass;
    private final ModelPart cube;

    public TheIndexBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(new TheIndexGeoModel());
        ModelPart root = context.bakeLayer(ModelLayers.END_CRYSTAL);
        this.glass = root.getChild("glass");
        this.cube = root.getChild("cube");
    }

    @Override
    public void render(TheIndexBlockEntity animatable, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        Player focusPlayer = updateCrystalReactiveState(animatable, animatable.getLevel(), partialTick);
        emitWakeBurst(animatable, animatable.getLevel());
        playDisengageSound(animatable, animatable.getLevel());
        if (focusPlayer == null) {
            emitOrbitingEnchantRings(animatable, animatable.getLevel(), partialTick);
            playIdleChime(animatable, animatable.getLevel());
        }
        renderFloatingCrystal(animatable, partialTick, poseStack, bufferSource, packedLight);
        emitEngagedParticles(animatable, animatable.getLevel(), focusPlayer);
    }

    private void renderFloatingCrystal(TheIndexBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Level level = blockEntity.getLevel();
        float time = (level == null ? 0.0F : (float) level.getGameTime()) + partialTick;
        float engagement = blockEntity.crystalEngagement();
        float bobAmplitude = Mth.lerp(engagement, BOB_AMPLITUDE, BOB_AMPLITUDE * ENGAGED_BOB_MULTIPLIER);
        float bob = Mth.sin(time * 0.16F) * bobAmplitude;
        float rotation = time * ROTATION_DEGREES_PER_TICK + blockEntity.crystalSpinAngle();
        float crystalScale = Mth.lerp(engagement, IDLE_CRYSTAL_SCALE, MIN_ENGAGED_CRYSTAL_SCALE);
        blockEntity.tickCrystalEngagedSpin();

        poseStack.pushPose();
        poseStack.translate(0.5D + blockEntity.crystalOffsetX(), CRYSTAL_BASE_Y + bob, 0.5D + blockEntity.crystalOffsetZ());
        poseStack.scale(crystalScale, crystalScale, crystalScale);

        VertexConsumer vertexConsumer = bufferSource.getBuffer(CRYSTAL_RENDER_TYPE);
        int overlay = OverlayTexture.NO_OVERLAY;

        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.translate(0.0F, 0.70F, 0.0F);
        poseStack.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3.0D), SIN_45, 0.0F, SIN_45));
        this.glass.render(poseStack, vertexConsumer, packedLight, overlay);
        poseStack.scale(0.875F, 0.875F, 0.875F);
        poseStack.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3.0D), SIN_45, 0.0F, SIN_45));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        this.glass.render(poseStack, vertexConsumer, packedLight, overlay);
        poseStack.scale(0.875F, 0.875F, 0.875F);
        poseStack.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3.0D), SIN_45, 0.0F, SIN_45));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        this.cube.render(poseStack, vertexConsumer, packedLight, overlay);

        poseStack.popPose();
    }

    private static Player updateCrystalReactiveState(TheIndexBlockEntity blockEntity, Level level, float partialTick) {
        if (level == null) {
            blockEntity.updateCrystalRenderState(0.0F, 0.0F, 0.0F, false);
            return null;
        }
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 anchor = new Vec3(pos.getX() + 0.5D, pos.getY() + CRYSTAL_BASE_Y, pos.getZ() + 0.5D);
        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        Player nearest = null;
        double nearestHorizontalDistance = PLAYER_REACT_RADIUS;
        for (Player player : level.players()) {
            Vec3 playerPos = player.getPosition(partialTick);
            double dx = playerPos.x - center.x;
            double dz = playerPos.z - center.z;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance < nearestHorizontalDistance) {
                nearest = player;
                nearestHorizontalDistance = horizontalDistance;
            }
        }
        if (nearest == null) {
            blockEntity.updateCrystalRenderState(0.0F, 0.0F, 0.0F, false);
            return null;
        }
        Vec3 playerPos = nearest.getPosition(partialTick);
        double dx = playerPos.x - center.x;
        double dz = playerPos.z - center.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < 1.0E-4D) {
            blockEntity.updateCrystalRenderState(0.0F, 0.0F, 1.0F, true);
            return nearest;
        }
        float targetOffsetX = (float) (dx / horizontalDistance * MAX_PLAYER_OFFSET);
        float targetOffsetZ = (float) (dz / horizontalDistance * MAX_PLAYER_OFFSET);
        float targetEngagement = (float) Mth.clamp((PLAYER_REACT_RADIUS - nearestHorizontalDistance) / PLAYER_REACT_RADIUS, 0.0D, 1.0D);
        blockEntity.updateCrystalRenderState(targetOffsetX, targetOffsetZ, targetEngagement, true);
        return nearest;
    }

    private static void emitWakeBurst(TheIndexBlockEntity blockEntity, Level level) {
        if (level == null || !blockEntity.consumeCrystalWakeBurst()) {
            return;
        }
        RandomSource random = level.random;
        BlockPos pos = blockEntity.getBlockPos();
        double crystalX = pos.getX() + 0.5D + blockEntity.crystalOffsetX();
        double crystalY = pos.getY() + CRYSTAL_BASE_Y + 0.28D;
        double particleTargetY = crystalY + ENCHANT_PARTICLE_FALL_COMPENSATION;
        double crystalZ = pos.getZ() + 0.5D + blockEntity.crystalOffsetZ();
        level.playLocalSound(crystalX, crystalY, crystalZ, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.36F, 1.12F, false);
        level.playLocalSound(crystalX, crystalY, crystalZ, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.42F, 1.42F + random.nextFloat() * 0.16F, false);

        for (int i = 0; i < WAKE_BURST_PARTICLES; i++) {
            double angle = i * Math.PI * 2.0D / WAKE_BURST_PARTICLES + random.nextDouble() * 0.28D;
            double radius = 0.18D + random.nextDouble() * 0.34D;
            double spawnX = crystalX + Math.cos(angle) * radius;
            double spawnY = crystalY + (random.nextDouble() - 0.5D) * 0.42D;
            double spawnZ = crystalZ + Math.sin(angle) * radius;
            level.addParticle(
                    ParticleTypes.ENCHANT,
                    crystalX,
                    particleTargetY,
                    crystalZ,
                    spawnX - crystalX,
                    spawnY - particleTargetY,
                    spawnZ - crystalZ
            );
        }
    }

    private static void playDisengageSound(TheIndexBlockEntity blockEntity, Level level) {
        if (level == null || !blockEntity.consumeCrystalDisengageSound()) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        double crystalX = pos.getX() + 0.5D + blockEntity.crystalOffsetX();
        double crystalY = pos.getY() + CRYSTAL_BASE_Y + CRYSTAL_PARTICLE_TARGET_Y_OFFSET;
        double crystalZ = pos.getZ() + 0.5D + blockEntity.crystalOffsetZ();
        level.playLocalSound(crystalX, crystalY, crystalZ, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.48F, 0.78F, false);
        level.playLocalSound(crystalX, crystalY, crystalZ, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.16F, 0.82F, false);
    }

    private static void playIdleChime(TheIndexBlockEntity blockEntity, Level level) {
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        long gameTime = level.getGameTime();
        int delay = MIN_IDLE_CHIME_DELAY_TICKS + random.nextInt(IDLE_CHIME_DELAY_VARIANCE_TICKS + 1);
        if (!blockEntity.shouldPlayCrystalIdleChime(gameTime, delay)) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos();
        double crystalX = pos.getX() + 0.5D + blockEntity.crystalOffsetX();
        double crystalY = pos.getY() + CRYSTAL_BASE_Y + CRYSTAL_PARTICLE_TARGET_Y_OFFSET;
        double crystalZ = pos.getZ() + 0.5D + blockEntity.crystalOffsetZ();
        level.playLocalSound(crystalX, crystalY, crystalZ, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.34F, 1.48F + random.nextFloat() * 0.18F, false);
    }

    private static void emitOrbitingEnchantRings(TheIndexBlockEntity blockEntity, Level level, float partialTick) {
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (!blockEntity.shouldEmitCrystalOrbitParticles(gameTime)) {
            return;
        }

        RandomSource random = level.random;
        BlockPos pos = blockEntity.getBlockPos();
        double crystalX = pos.getX() + 0.5D + blockEntity.crystalOffsetX();
        double crystalY = pos.getY() + CRYSTAL_BASE_Y + CRYSTAL_PARTICLE_TARGET_Y_OFFSET;
        double particleTargetY = crystalY + ENCHANT_PARTICLE_FALL_COMPENSATION;
        double crystalZ = pos.getZ() + 0.5D + blockEntity.crystalOffsetZ();
        double time = gameTime + partialTick;

        for (int ring = 0; ring < ORBIT_RING_COUNT; ring++) {
            double yaw = switch (ring) {
                case 0 -> 0.0D;
                case 1 -> Math.toRadians(34.0D);
                default -> Math.toRadians(-34.0D);
            };
            double tilt = switch (ring) {
                case 0 -> Math.toRadians(3.0D);
                case 1 -> Math.toRadians(58.0D);
                default -> Math.toRadians(-58.0D);
            };
            double direction = ring % 2 == 0 ? 1.0D : -1.0D;
            double rotation = time * (0.026D + ring * 0.007D) * direction + ring * 2.65D;
            for (int trail = 0; trail < ORBIT_COMET_TRAIL_PARTICLES; trail++) {
                double trailOffset = trail * ORBIT_COMET_TRAIL_SPACING * direction;
                double angle = rotation - trailOffset;
                double trailScale = 1.0D - trail * 0.018D;
                double wobble = Math.sin(angle * 2.0D + time * 0.12D) * 0.025D;
                double majorRadius = (ORBIT_MAJOR_RADIUS + ring * ORBIT_MAJOR_RADIUS_STEP) * trailScale;
                double minorRadius = (ORBIT_MINOR_RADIUS + ring * ORBIT_MINOR_RADIUS_STEP) * trailScale;
                Vec3 offset = orbitOffset(yaw, tilt, angle, majorRadius, minorRadius);
                double sourceX = crystalX + offset.x;
                double sourceY = crystalY + offset.y + wobble + (random.nextDouble() - 0.5D) * 0.025D;
                double sourceZ = crystalZ + offset.z;
                level.addParticle(
                        ParticleTypes.ENCHANT,
                        crystalX,
                        particleTargetY,
                        crystalZ,
                        sourceX - crystalX,
                        sourceY - particleTargetY,
                        sourceZ - crystalZ
                );
            }
        }
    }

    private static Vec3 orbitOffset(double yaw, double tilt, double angle, double majorRadius, double minorRadius) {
        double ux = Math.cos(yaw);
        double uz = Math.sin(yaw);
        double hx = -Math.sin(yaw);
        double hz = Math.cos(yaw);
        double vx = hx * Math.cos(tilt);
        double vy = Math.sin(tilt);
        double vz = hz * Math.cos(tilt);
        double major = Math.cos(angle) * majorRadius;
        double minor = Math.sin(angle) * minorRadius;
        return new Vec3(
                ux * major + vx * minor,
                vy * minor,
                uz * major + vz * minor
        );
    }

    private static void emitEngagedParticles(TheIndexBlockEntity blockEntity, Level level, Player focusPlayer) {
        if (level == null || focusPlayer == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime % 2L != 0L || !blockEntity.shouldEmitCrystalParticles(gameTime)) {
            return;
        }
        RandomSource random = level.random;
        BlockPos pos = blockEntity.getBlockPos();
        double crystalX = pos.getX() + 0.5D + blockEntity.crystalOffsetX();
        double crystalY = pos.getY() + CRYSTAL_BASE_Y + CRYSTAL_PARTICLE_TARGET_Y_OFFSET;
        double particleTargetY = crystalY + ENCHANT_PARTICLE_FALL_COMPENSATION;
        double crystalZ = pos.getZ() + 0.5D + blockEntity.crystalOffsetZ();
        Vec3 source = focusPlayer.getEyePosition();
        double sourceX = source.x + (random.nextDouble() - 0.5D) * 0.45D;
        double sourceY = source.y - 0.25D + (random.nextDouble() - 0.5D) * 0.35D;
        double sourceZ = source.z + (random.nextDouble() - 0.5D) * 0.45D;
        level.addParticle(
                ParticleTypes.ENCHANT,
                crystalX,
                particleTargetY,
                crystalZ,
                sourceX - crystalX,
                sourceY - particleTargetY,
                sourceZ - crystalZ
        );
    }
}
