package net.zoogle.levelrpg.client.gecko;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.LevelRPG;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;

import java.io.IOException;
import java.io.InputStream;

/**
 * Render-to-texture for the book pages:
 * - Draw into an offscreen FBO (opaque bg → base atlas → force alpha=1 → text)
 * - Copy FBO into a persistent TextureManager-owned texture (adapter) every frame
 * - Use private BufferSource for text (no global flush)
 * - Lock active texture unit during copy to prevent state races
 * - No mipmaps; clamp; projection backup/restore
 */
public final class DynamicBookTexture {
    private static final ResourceLocation BASE_BOOK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "textures/gui/book_tex_2.png");
    private static final ResourceLocation DYNAMIC_BOOK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LevelRPG.MODID, "dynamic/book_pages");

    private static DynamicBookTexture INSTANCE;
    public static DynamicBookTexture get() {
        if (INSTANCE == null) INSTANCE = new DynamicBookTexture();
        return INSTANCE;
    }
    private DynamicBookTexture() {}

    private TextureTarget fbo;
    private AdapterTexture adapter;
    private int width, height;
    private boolean hasContent = false;

    public ResourceLocation getTextureLocation() {
        ensureInitialized();
        return DYNAMIC_BOOK_TEXTURE;
    }
    public boolean hasContent() { return hasContent; }

    public void update(String leftText, String rightText) {
        if (leftText == null) leftText = "";
        if (rightText == null) rightText = "";
        ensureInitialized();
        renderToFbo(leftText, rightText);
        copyFboToAdapter(); // Copy after drawing
    }

    public void destroy() {
        if (fbo != null) { fbo.destroyBuffers(); fbo = null; }
        adapter = null;
        hasContent = false;
    }

    // --------------------------------------------------------------------------------------------

    private void ensureInitialized() {
        if (fbo != null && adapter != null) return;

        int[] dims = readPngDimensions(BASE_BOOK_TEXTURE);
        width = Math.max(256, dims[0]);
        height = Math.max(256, dims[1]);

        fbo = new TextureTarget(width, height, false, false);
        fbo.setClearColor(0f, 0f, 0f, 0f);
        configureTexture(fbo.getColorTextureId());

        TextureManager tm = Minecraft.getInstance().getTextureManager();
        adapter = new AdapterTexture(width, height);
        tm.register(DYNAMIC_BOOK_TEXTURE, adapter);

        System.out.println("[LevelRPG] Registered dyn RL id=" + adapter.getId() + " RL=" + DYNAMIC_BOOK_TEXTURE);
    }

    private void renderToFbo(String leftText, String rightText) {
        Minecraft mc = Minecraft.getInstance();

        fbo.bindWrite(true);
        RenderSystem.viewport(0, 0, width, height);
        fbo.clear(true);

        RenderSystem.backupProjectionMatrix();
        Matrix4f ortho = new Matrix4f().setOrtho(0, width, height, 0, -1f, 1f);
        RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);

        // 0) Opaque white background (no blending)
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        {
            Tesselator t0 = Tesselator.getInstance();
            var b0 = t0.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            b0.addVertex(ortho, 0,      height, 0).setColor(1f, 1f, 1f, 1f);
            b0.addVertex(ortho, width,  height, 0).setColor(1f, 1f, 1f, 1f);
            b0.addVertex(ortho, width,  0,      0).setColor(1f, 1f, 1f, 1f);
            b0.addVertex(ortho, 0,      0,      0).setColor(1f, 1f, 1f, 1f);
            BufferUploader.drawWithShader(b0.build());
        }

        // 1) Blit base atlas (still no blending)
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, BASE_BOOK_TEXTURE);
        {
            Tesselator tes = Tesselator.getInstance();
            var buf = tes.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buf.addVertex(ortho, 0,      height, 0).setUv(0f, 1f);
            buf.addVertex(ortho, width,  height, 0).setUv(1f, 1f);
            buf.addVertex(ortho, width,  0,      0).setUv(1f, 0f);
            buf.addVertex(ortho, 0,      0,      0).setUv(0f, 0f);
            BufferUploader.drawWithShader(buf.build());
        }

        // 1b) Force alpha=1 across the RT
        GlStateManager._colorMask(false, false, false, true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableBlend();
        {
            Tesselator ta = Tesselator.getInstance();
            var ba = ta.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            ba.addVertex(ortho, 0,      height, 0).setColor(0f, 0f, 0f, 1f);
            ba.addVertex(ortho, width,  height, 0).setColor(0f, 0f, 0f, 1f);
            ba.addVertex(ortho, width,  0,      0).setColor(0f, 0f, 0f, 1f);
            ba.addVertex(ortho, 0,      0,      0).setColor(0f, 0f, 0f, 1f);
            BufferUploader.drawWithShader(ba.build());
        }
        GlStateManager._colorMask(true, true, true, true);

        // DEBUG
        System.out.println("[LevelRPG] FBO draw frame (write); size=" + width + "x" + height);

        // 2) Text (with blending) using private buffer source
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        {
            ByteBufferBuilder bytes = new ByteBufferBuilder(256 * 1024);
            MultiBufferSource.BufferSource localBuffers = MultiBufferSource.immediate(bytes);

            Font font = mc.font;

            // Replace with exact rects for pages 5/6/7 later
            int leftX = Math.round(width * 0.12f);
            int leftY = Math.round(height * 0.20f);
            int leftW = Math.round(width * 0.30f);
            int leftH = Math.round(height * 0.60f);

            int rightX = Math.round(width * 0.58f);
            int rightY = leftY;
            int rightW = leftW;
            int rightH = leftH;

            drawWrappedText(font, localBuffers, leftText,  leftX,  leftY,  leftW,  leftH, 0xFF2B2B2B);
            drawWrappedText(font, localBuffers, rightText, rightX, rightY, rightW, rightH, 0xFF2B2B2B);

            localBuffers.endBatch();
        }

        // Restore main FB & projection
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        RenderSystem.restoreProjectionMatrix();

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        hasContent = true;
    }

    /** Copy the FBO color buffer into the persistent adapter texture. */
    private void copyFboToAdapter() {
        if (adapter == null || fbo == null) return;

        // Lock active texture unit to 0 for the copy so other passes can’t interfere
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        adapter.bind();                       // bind destination texture (GL_TEXTURE_2D on unit 0)
        configureTexture(adapter.getId());    // ensure no mips + clamp on adapter as well

        // Bind the offscreen FBO for READ and copy into currently bound texture
        fbo.bindRead();
        GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
        GL11C.glCopyTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);

        // Restore default READ framebuffer
        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);

        // DEBUG
        System.out.println("[LevelRPG] Copied FBO -> adapter texId=" + adapter.getId());
    }

    private void drawWrappedText(Font font,
                                 MultiBufferSource buffers,
                                 String text,
                                 int x, int y, int w, int h,
                                 int argb) {
        if (text.isEmpty()) return;

        var lines = font.split(net.minecraft.network.chat.Component.literal(text), w);
        int lineHeight = font.lineHeight + 1;
        int maxLines = Math.max(0, h / lineHeight);
        int drawLines = Math.min(maxLines, lines.size());

        Matrix4f mat = new Matrix4f().setOrtho(0, width, height, 0, -1f, 1f);
        for (int i = 0; i < drawLines; i++) {
            var line = lines.get(i);
            font.drawInBatch(
                    line, x, y + i * lineHeight,
                    argb, false, mat, buffers,
                    Font.DisplayMode.NORMAL,
                    0, 0x00F000F0
            );
        }
    }

    private int[] readPngDimensions(ResourceLocation rl) {
        try {
            var opt = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (opt.isPresent()) {
                try (InputStream in = opt.get().open()) {
                    byte[] header = in.readNBytes(33);
                    if (header.length >= 33 &&
                            header[0] == (byte)137 && header[1] == 80 && header[2] == 78 && header[3] == 71) {
                        int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) |
                                ((header[18] & 0xFF) << 8)  |  (header[19] & 0xFF);
                        int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) |
                                ((header[22] & 0xFF) << 8)  |  (header[23] & 0xFF);
                        return new int[]{w, h};
                    }
                }
            }
        } catch (IOException ignored) {}
        return new int[]{512, 512};
    }

    private static void configureTexture(int glId) {
        GlStateManager._bindTexture(glId);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_BASE_LEVEL, 0);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
    }

    /** Persistent TextureManager-owned texture we copy into each frame. */
    private static final class AdapterTexture extends AbstractTexture {
        private final int w, h;

        AdapterTexture(int w, int h) {
            this.w = w; this.h = h;
            bindEnsureAllocated();
        }

        @Override
        public void bind() {
            bindEnsureAllocated();
        }

        private void bindEnsureAllocated() {
            if (this.id == -1) {
                this.id = GlStateManager._genTexture();
                GlStateManager._bindTexture(this.id);
                GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, w, h, 0,
                        GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_BASE_LEVEL, 0);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            } else {
                GlStateManager._bindTexture(this.id);
            }
        }

        public int getId() { return this.id; }

        @Override public void close() { /* TextureManager manages lifecycle */ }
        @Override public void load(net.minecraft.server.packs.resources.ResourceManager rm) { /* no-op */ }
    }
}
