package net.zoogle.levelrpg.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.zoogle.levelrpg.client.gecko.BookGuiRenderer;
import net.zoogle.levelrpg.client.gecko.DummyAnimatable;
import net.zoogle.levelrpg.client.gecko.DynamicBookTexture;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LevelBookScreen extends Screen {
    private BookGuiRenderer bookRenderer;
    private DummyAnimatable anim;
    private DynamicBookTexture pageTexture;

    private static final boolean SAFE_MODE = true; // turn off to go back to fancy transforms

    // Anim clips
    private static final String CLIP_IDLE_CLOSED = "animation.model.idle_closed";
    private static final String CLIP_OPEN        = "animation.model.open";
    private static final String CLIP_IDLE_OPEN   = "animation.model.idle_open";

    // Layout
    private static final float MODEL_H = 90f;     // just used to derive a reasonable scale

    public LevelBookScreen() { super(Component.literal("Level Book")); }

    @Override protected void init() {
        if (bookRenderer == null) bookRenderer = new BookGuiRenderer();
        if (anim == null)         anim         = new DummyAnimatable();
        if (pageTexture == null)  pageTexture  = DynamicBookTexture.get();

        anim.setDesiredAnimation(CLIP_IDLE_CLOSED, true);
        anim.setOverrideTexture(null);
        pageTexture.update("Welcome!", "Loading pages…");
        super.init();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() {
        super.removed();
        if (anim != null) anim.setOverrideTexture(null);
        if (pageTexture != null) { pageTexture.destroy(); pageTexture = null; }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);

        // 1) Update/bind dynamic page BEFORE render
        String playerName = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getName().getString()
                : "Player";
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        pageTexture.update("Adventurer: " + playerName + "\nDate: " + time + "\n\nWelcome!",
                "Dynamic page:\n- Live text\n- Into RT\n- Then sampled");
        if (anim.getOverrideTexture() == null && pageTexture.hasContent()) {
            anim.setOverrideTexture(pageTexture.getTextureLocation());
            System.out.println("[LevelRPG] LevelBookScreen: bound dynamic RL " + pageTexture.getTextureLocation());
        }

        // 2) Place/render
        PoseStack pose = gfx.pose();
        pose.pushPose();

        float cx = this.width * 0.5f;
        float cy = this.height * 0.5f;

        // Debug rect at anchor so we SEE where we render
        int dbgW = 80, dbgH = 40;
        gfx.fill((int)cx - dbgW, (int)cy - dbgH, (int)cx + dbgW, (int)cy + dbgH, 0x40FF00FF);

        pose.translate(cx, cy, 0f);

        // SAFE MODE: huge, no flip, no rotation
        if (SAFE_MODE) {
            float scale = Math.min(this.width, this.height) / MODEL_H * 16.0f; // very large
            pose.scale(scale, scale, scale);
            // No mirroring, no 180° rotate — draw in model's native orientation
        } else {
            float scale = Math.min(this.width, this.height) / MODEL_H * 10.0f;
            pose.scale(scale, scale, scale);
            // If you want the old flips, put them here
            // pose.scale(-1f, 1f, 1f);
            // pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));
        }

        bookRenderer.renderForGui(gfx, pose, anim, partialTick);
        pose.popPose();
    }
}
