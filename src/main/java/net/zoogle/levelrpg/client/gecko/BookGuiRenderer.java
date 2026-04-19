package net.zoogle.levelrpg.client.gecko;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

public class BookGuiRenderer extends GeoObjectRenderer<DummyAnimatable> {
    private static final boolean DEBUG_DRAW_QUAD = false;

    public BookGuiRenderer() {
        super(new BookGeoModel());
    }

    private static RenderType guiRenderType(ResourceLocation tex) {
        var state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(true);
        return RenderType.create(
                "levelrpg_book_gui",
                DefaultVertexFormat.NEW_ENTITY,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                256, true, false, state
        );
    }

    @Override
    public RenderType getRenderType(DummyAnimatable anim, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    public void renderForGui(GuiGraphics gfx, PoseStack pose, DummyAnimatable anim, float partialTick) {
        MultiBufferSource.BufferSource guiBuffers = gfx.bufferSource();

        // Safe GUI state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        GlStateManager._disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Bake model & RT
        ResourceLocation modelRes = this.getGeoModel().getModelResource(anim);
        BakedGeoModel baked = this.getGeoModel().getBakedModel(modelRes);
        ResourceLocation tex = this.getTextureLocation(anim);
        RenderType type = this.getRenderType(anim, tex, guiBuffers, partialTick);


        // Draw the actual model
        this.actuallyRender(
                pose, anim, baked, type, guiBuffers,
                /* vertexConsumer */ null,
                /* isReRender    */ false,
                /* partialTick   */ partialTick,
                /* packedLight   */ LightTexture.FULL_BRIGHT,
                /* packedOverlay */ OverlayTexture.NO_OVERLAY,
                /* color (ARGB)  */ 0xFFFFFFFF
        );

        guiBuffers.endBatch();

        GlStateManager._enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

}
