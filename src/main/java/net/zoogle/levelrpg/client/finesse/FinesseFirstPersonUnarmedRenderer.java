package net.zoogle.levelrpg.client.finesse;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.zoogle.levelrpg.LevelRPG;
import net.zoogle.levelrpg.client.Keybinds;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.skilltree.FinesseNodeIds;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import java.util.Set;

@EventBusSubscriber(modid = LevelRPG.MODID, value = Dist.CLIENT)
public final class FinesseFirstPersonUnarmedRenderer {
    private static final float IDLE_EQUIP_OFFSET = 0.34F;
    private static final float OFFHAND_Y_LIFT = 0.10F;
    private static final float SWING_REACH_SCALE = 0.66F;
    private static final float SWING_LIFT_SCALE = 0.62F;
    private static final float SWING_DEPTH_SCALE = 0.58F;
    private static final float ARM_SCALE = 0.93F;
    private static final float GUARD_RAISE_PER_TICK = 0.22F;
    private static final float GUARD_LOWER_PER_TICK = 0.18F;
    private static final float GUARD_RENDER_EPSILON = 0.001F;
    private static final float READY_RAISE_PER_TICK = 0.20F;
    private static final float READY_LOWER_PER_TICK = 0.08F;
    private static final float READY_RENDER_EPSILON = 0.001F;
    private static final float READY_IMMEDIATE_PROGRESS = 0.35F;
    private static final int COMBAT_READY_TIMEOUT_TICKS = 45;

    private static float previousGuardProgress;
    private static float guardProgress;
    private static float previousReadyProgress;
    private static float readyProgress;
    private static int lastCombatReadyTick = Integer.MIN_VALUE;

    private FinesseFirstPersonUnarmedRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        previousGuardProgress = guardProgress;
        previousReadyProgress = readyProgress;
        if (!(minecraft.player instanceof AbstractClientPlayer player)) {
            guardProgress = 0.0F;
            previousGuardProgress = 0.0F;
            readyProgress = 0.0F;
            previousReadyProgress = 0.0F;
            lastCombatReadyTick = Integer.MIN_VALUE;
            return;
        }

        boolean guarding = isHandsUpGuarding(minecraft, player);
        float delta = guarding ? GUARD_RAISE_PER_TICK : -GUARD_LOWER_PER_TICK;
        guardProgress = Mth.clamp(guardProgress + delta, 0.0F, 1.0F);

        boolean ready = shouldHoldCombatReady(minecraft, player, guarding);
        float readyDelta = ready ? READY_RAISE_PER_TICK : -READY_LOWER_PER_TICK;
        readyProgress = Mth.clamp(readyProgress + readyDelta, 0.0F, 1.0F);
    }

    public static void markUnarmedCombatUsed(int clientTick) {
        lastCombatReadyTick = clientTick;
        if (readyProgress < READY_IMMEDIATE_PROGRESS) {
            readyProgress = READY_IMMEDIATE_PROGRESS;
        }
    }

    public static boolean isFistCombatActive(AbstractClientPlayer player) {
        if (player == null || !canRenderEmptyHandNode(player, FinesseNodeIds.HAND_TO_HAND_COMBAT)) {
            return false;
        }
        int passed = player.tickCount - lastCombatReadyTick;
        return passed >= 0 && passed <= COMBAT_READY_TIMEOUT_TICKS;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player instanceof AbstractClientPlayer player)) {
            return;
        }
        float interpolatedReadyProgress = readyProgress(event.getPartialTick());
        float interpolatedGuardProgress = guardProgress(event.getPartialTick());
        float chargeProgress = Keybinds.getUppercutChargeProgress(player, event.getPartialTick());
        if (event.getItemStack().isEmpty()
                && interpolatedReadyProgress > READY_RENDER_EPSILON
                && canRenderCustomUnarmedArms(player)) {
            HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            FinesseSegmentedUnarmedArmRenderer.renderGuardArm(
                    event.getPoseStack(),
                    event.getMultiBufferSource(),
                    event.getPackedLight(),
                    player,
                    arm,
                    event.getEquipProgress(),
                    interpolatedReadyProgress,
                    interpolatedGuardProgress,
                    event.getSwingProgress(),
                    chargeProgress
            );
            event.setCanceled(true);
            return;
        }
        if (event.getHand() != InteractionHand.OFF_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        if (event.getSwingProgress() <= 0.001F || !canRenderFinesseOffhand(player)) {
            return;
        }

        HumanoidArm arm = player.getMainArm().getOpposite();
        renderEmptyArm(
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight(),
                player,
                arm,
                event.getEquipProgress(),
                event.getSwingProgress()
        );
        event.setCanceled(true);
    }

    private static boolean isHandsUpGuarding(Minecraft minecraft, AbstractClientPlayer player) {
        if (minecraft.options == null || !minecraft.options.keyUse.isDown()) {
            return false;
        }
        return isFistCombatActive(player) && canRenderEmptyHandNode(player, FinesseNodeIds.HANDS_UP);
    }

    private static boolean shouldHoldCombatReady(Minecraft minecraft, AbstractClientPlayer player, boolean guarding) {
        if (guarding) {
            markUnarmedCombatUsed(player.tickCount);
            return true;
        }
        if (!canRenderEmptyHandNode(player, FinesseNodeIds.HAND_TO_HAND_COMBAT)) {
            return false;
        }
        int passed = player.tickCount - lastCombatReadyTick;
        return passed >= 0 && passed <= COMBAT_READY_TIMEOUT_TICKS;
    }

    private static boolean canRenderCustomUnarmedArms(AbstractClientPlayer player) {
        return canRenderEmptyHandNode(player, FinesseNodeIds.HAND_TO_HAND_COMBAT)
                || canRenderEmptyHandNode(player, FinesseNodeIds.HANDS_UP);
    }

    private static float guardProgress(float partialTick) {
        float progress = Mth.lerp(partialTick, previousGuardProgress, guardProgress);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static float readyProgress(float partialTick) {
        float progress = Mth.lerp(partialTick, previousReadyProgress, readyProgress);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    private static boolean canRenderFinesseOffhand(AbstractClientPlayer player) {
        return canRenderEmptyHandNode(player, FinesseNodeIds.HAND_TO_HAND_COMBAT);
    }

    private static boolean canRenderEmptyHandNode(AbstractClientPlayer player, String nodeId) {
        if (player.isSpectator() || player.isInvisible()) {
            return false;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return false;
        }
        Set<String> unlocked = ClientProfileCache.getTreeUnlockedNodes(FinesseNodeIds.SKILL);
        return unlocked.contains(nodeId)
                || unlocked.contains(FinesseNodeIds.SKILL.getPath() + "_" + nodeId);
    }

    private static void renderEmptyArm(
            PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            HumanoidArm arm,
            float equippedProgress,
            float swingProgress
    ) {
        boolean right = arm != HumanoidArm.LEFT;
        float side = right ? 1.0F : -1.0F;
        float swingRoot = Mth.sqrt(swingProgress);
        float x = -0.3F * Mth.sin(swingRoot * (float) Math.PI);
        float y = 0.4F * Mth.sin(swingRoot * (float) (Math.PI * 2));
        float z = -0.4F * Mth.sin(swingProgress * (float) Math.PI);

        poseStack.pushPose();
        poseStack.translate(
                side * ((x * SWING_REACH_SCALE) + 0.52000004F),
                (y * SWING_LIFT_SCALE) + -0.78F + OFFHAND_Y_LIFT + equippedProgress * -0.42F,
                (z * SWING_DEPTH_SCALE) + -0.82F
        );
        poseStack.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
        rotate(poseStack, 0.0F, 1.0F, 0.0F, side * 38.0F);
        float ySwing = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float zSwing = Mth.sin(swingRoot * (float) Math.PI);
        rotate(poseStack, 0.0F, 1.0F, 0.0F, side * zSwing * 48.0F);
        rotate(poseStack, 0.0F, 0.0F, 1.0F, side * ySwing * -14.0F);
        poseStack.translate(side * -1.0F, 3.6F, 3.5F);
        rotate(poseStack, 0.0F, 0.0F, 1.0F, side * 120.0F);
        rotate(poseStack, 1.0F, 0.0F, 0.0F, 200.0F);
        rotate(poseStack, 0.0F, 1.0F, 0.0F, side * -135.0F);
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);

        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (right) {
            renderer.renderRightHand(poseStack, buffer, packedLight, player);
        } else {
            renderer.renderLeftHand(poseStack, buffer, packedLight, player);
        }
        poseStack.popPose();
    }

    private static void rotate(PoseStack poseStack, float x, float y, float z, float degrees) {
        poseStack.mulPose(new Quaternionf(new AxisAngle4f(degrees * ((float) Math.PI / 180.0F), x, y, z)));
    }
}
