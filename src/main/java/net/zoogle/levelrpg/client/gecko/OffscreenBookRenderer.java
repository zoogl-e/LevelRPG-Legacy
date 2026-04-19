package net.zoogle.levelrpg.client.gecko;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
/*
public final class OffscreenBookRenderer {

    private TextureTarget fbo;
    private final BookGuiRenderer renderer;
    private final DummyAnimatable anim;

    // persistent wrapper + key so we can bind by RL every frame
    private ExternalTexture fboWrapper;
    private static final ResourceLocation FBO_KEY =
            ResourceLocation.fromNamespaceAndPath("levelrpg", "book_fbo");

    private static final float MODEL_HEIGHT_UNITS = 90f; // your BB page height

    public OffscreenBookRenderer(BookGuiRenderer renderer, DummyAnimatable anim) {
        this.renderer = renderer;
        this.anim = anim;
    }

    public void renderOffscreen(int sizePx, float pt) {
        if (sizePx < 4) sizePx = 4;

        if (fbo == null || fbo.width != sizePx || fbo.height != sizePx) {
            if (fbo != null) fbo.destroyBuffers();
            fbo = new TextureTarget(sizePx, sizePx, true, false);
            fbo.setClearColor(0f, 0f, 0f, 0f);
        }

        fbo.bindWrite(true);
        RenderSystem.viewport(0, 0, sizePx, sizePx);
        fbo.clear(true);

        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.pushPose();
        pose.translate(sizePx / 2f, sizePx / 2f, 0f);

        // scale: bump if you still want larger (e.g., * 2.0f)
        float scale = (sizePx / 90f) * 1.6f;
        pose.scale(scale, scale, scale);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180f));

        // --- render the model into the FBO ---
        renderer.renderForGui(pose, anim, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);

        // FLUSH anything GeckoLib wrote to the global BufferSource while the FBO is bound
        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();  // FLUSH

        pose.popPose();

        // restore main framebuffer + viewport
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    public void blitToGui(net.minecraft.client.gui.GuiGraphics gfx, int x, int y, int w, int h) {
        if (fbo == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // ensure sampler0 is our FBO texture
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.activeTexture(33984); // GL_TEXTURE0
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(fbo.getColorTextureId());

        org.joml.Matrix4f mat = gfx.pose().last().pose();
        float z = 0f;

        var tes = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        var bb  = tes.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);

        bb.addVertex(mat, x,     y + h, z).setUv(0f, 1f);
        bb.addVertex(mat, x + w, y + h, z).setUv(1f, 1f);
        bb.addVertex(mat, x + w, y,     z).setUv(1f, 0f);
        bb.addVertex(mat, x,     y,     z).setUv(0f, 0f);

        var mesh = bb.build();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);

        RenderSystem.enableDepthTest();
    }
}*/