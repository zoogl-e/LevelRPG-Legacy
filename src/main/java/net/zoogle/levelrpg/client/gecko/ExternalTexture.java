package net.zoogle.levelrpg.client.gecko;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

/** Wraps an existing GL texture id. We do NOT own/delete the texture. */
public final class ExternalTexture extends AbstractTexture {
    private int glId;

    public ExternalTexture(int glId) { this.glId = glId; }

    /** Update to a new GL id (e.g., when FBO is resized/recreated). */
    public void setGlId(int glId) { this.glId = glId; }

    @Override public void load(ResourceManager manager) { /* no-op */ }

    @Override public int getId() { return glId; }

    /** Do NOT delete here; the FBO owns the texture. */
    @Override public void releaseId() { /* no-op */ }
}