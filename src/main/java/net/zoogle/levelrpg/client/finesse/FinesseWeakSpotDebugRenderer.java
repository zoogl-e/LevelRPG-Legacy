package net.zoogle.levelrpg.client.finesse;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.finesse.FinesseUnarmedCombat;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class FinesseWeakSpotDebugRenderer {
    private static final double DEBUG_RANGE = 16.0D;
    private static final double COMBAT_RANGE = 8.0D;
    private static final float MIN_MARKER_ALPHA = 0.18F;
    private static final float MAX_MARKER_ALPHA = 0.72F;
    private static final float AIMED_MARKER_ALPHA = 0.95F;
    private static final Map<Integer, Integer> LAST_ENCHANT_PARTICLE_TICK = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> ENCHANT_ORBIT_INDEX = new ConcurrentHashMap<>();

    private FinesseWeakSpotDebugRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        boolean debug = minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes() && !minecraft.showOnlyReducedInfo();
        boolean combatHighlight = shouldRenderCombatWeakSpots(minecraft);
        if (!debug && !combatHighlight) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = debug ? bufferSource.getBuffer(RenderType.lines()) : null;
        AABB searchArea = minecraft.player.getBoundingBox().inflate(debug ? DEBUG_RANGE : COMBAT_RANGE);
        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        Vec3 look = minecraft.player.getViewVector(1.0F);

        for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class, searchArea, FinesseWeakSpotDebugRenderer::shouldRenderFor)) {
            AABB worldWeakSpotBox = FinesseUnarmedCombat.weakSpotDebugBox(entity);
            Vec3 worldCenter = FinesseUnarmedCombat.weakSpotDebugCenter(entity);
            FinesseUnarmedCombat.WeakSpotZone zone = FinesseUnarmedCombat.activeWeakSpotZone(entity);
            if (worldWeakSpotBox == null || worldCenter == null || zone == null) {
                continue;
            }
            AABB weakSpotBox = compactHighlightBox(worldWeakSpotBox).move(-camera.x, -camera.y, -camera.z);
            boolean aimedAtWeakSpot = FinesseUnarmedCombat.isRayInWeakSpot(eye, look, entity);
            float[] color = aimedAtWeakSpot ? new float[]{1.0F, 0.9F, 0.1F} : zoneColor(zone);
            float markerAlpha = aimedAtWeakSpot ? AIMED_MARKER_ALPHA : markerAlpha(minecraft.player.distanceTo(entity));
            if (aimedAtWeakSpot) {
                if (debug) {
                    LevelRenderer.renderLineBox(poseStack, lines, weakSpotBox, 1.0F, 0.9F, 0.1F, 0.45F);
                }
            } else if (debug) {
                LevelRenderer.renderLineBox(poseStack, lines, weakSpotBox, color[0], color[1], color[2], 0.35F);
            }
            if (!debug) {
                spawnWeakSpotParticles(minecraft, entity, worldCenter, markerAlpha, aimedAtWeakSpot);
            }
        }

        if (debug) {
            bufferSource.endBatch(RenderType.lines());
        }
    }

    private static boolean shouldRenderFor(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return entity != minecraft.player && entity.isAlive() && !entity.isInvisible();
    }

    private static boolean shouldRenderCombatWeakSpots(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            return false;
        }
        if (!minecraft.player.getMainHandItem().isEmpty() || !minecraft.player.getOffhandItem().isEmpty()) {
            return false;
        }
        Set<String> unlocked = ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
        return unlocked.contains(FinesseNodeIds.HAND_TO_HAND_COMBAT)
                || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + FinesseNodeIds.HAND_TO_HAND_COMBAT);
    }

    private static float[] zoneColor(FinesseUnarmedCombat.WeakSpotZone zone) {
        return switch (zone) {
            case HEAD -> new float[]{0.95F, 0.25F, 0.18F};
            case CHEST -> new float[]{0.2F, 0.75F, 1.0F};
            case LEGS -> new float[]{0.45F, 1.0F, 0.35F};
        };
    }

    private static float markerAlpha(double distance) {
        double near = 2.0D;
        double far = COMBAT_RANGE;
        double t = 1.0D - Math.max(0.0D, Math.min(1.0D, (distance - near) / (far - near)));
        return (float) (MIN_MARKER_ALPHA + (MAX_MARKER_ALPHA - MIN_MARKER_ALPHA) * t);
    }

    private static AABB compactHighlightBox(AABB box) {
        Vec3 center = box.getCenter();
        double sx = box.getXsize() * 0.34D;
        double sy = box.getYsize() * 0.34D;
        double sz = box.getZsize() * 0.34D;
        return new AABB(center.x - sx, center.y - sy, center.z - sz, center.x + sx, center.y + sy, center.z + sz);
    }

    private static void spawnWeakSpotParticles(Minecraft minecraft, LivingEntity entity, Vec3 center, float alpha, boolean aimed) {
        if (minecraft.level == null) {
            return;
        }
        int tick = minecraft.player == null ? 0 : minecraft.player.tickCount;
        int interval = aimed ? 1 : alpha > 0.55F ? 1 : alpha > 0.32F ? 2 : 4;
        Integer last = LAST_ENCHANT_PARTICLE_TICK.get(entity.getId());
        if (last != null && tick - last < interval) {
            return;
        }
        LAST_ENCHANT_PARTICLE_TICK.put(entity.getId(), tick);
        int count = aimed ? 3 : alpha > 0.55F ? 2 : 1;
        int orbit = ENCHANT_ORBIT_INDEX.merge(entity.getId(), 1, Integer::sum);
        double radius = aimed ? 0.22D : 0.14D;
        double verticalRadius = aimed ? 0.12D : 0.08D;
        double phaseBase = orbit * 0.72D + entity.getId() * 0.37D;
        for (int i = 0; i < count; i++) {
            double phase = phaseBase + i * (Math.PI * 2.0D / Math.max(1, count));
            double tilt = phase * 1.7D;
            double ox = Math.cos(phase) * radius;
            double oz = Math.sin(phase) * radius;
            double oy = Math.sin(tilt) * verticalRadius;
            double dx = Math.cos(phase) * 0.02D;
            double dz = Math.sin(phase) * 0.02D;
            double dy = Math.sin(tilt) * 0.01D;
            minecraft.level.addParticle(
                    ParticleTypes.ELECTRIC_SPARK,
                    center.x + ox,
                    center.y + oy,
                    center.z + oz,
                    dx,
                    dy,
                    dz
            );
        }
    }
}
