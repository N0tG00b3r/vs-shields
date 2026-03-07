package com.mechanicalskies.vsshields.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Custom RenderTypes for the shield sphere.
 * <p>
 * Extends RenderType to access protected RenderStateShard members
 * (standard modding pattern for custom render pipelines).
 * <p>
 * Layer 1 ({@link #shieldTranslucent()}) uses {@code rendertype_entity_translucent} shader
 * which Iris/Oculus routes through {@code gbuffers_entities_translucent}.
 * <p>
 * Layer 2 ({@link #shieldBloom()}) uses {@code rendertype_eyes} shader which Iris/Oculus
 * routes through {@code gbuffers_spidereyes} — shader packs apply bloom/glow to this pass.
 */
public class ShieldRenderTypes extends RenderType {

    // All shield layers now use procedural DynamicTextures from ShieldEnergyTexture

    private static RenderType SHIELD_TRANSLUCENT;
    private static RenderType SHIELD_TRANSLUCENT_CULLED;
    private static RenderType SHIELD_BLOOM;
    private static RenderType SHIELD_FILL;
    private static RenderType SHIELD_CONDUIT;

    // Required by RenderType — never instantiated
    @SuppressWarnings("ConstantConditions")
    private ShieldRenderTypes() {
        super("dummy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                256, false, true, null, null);
        throw new UnsupportedOperationException();
    }

    /**
     * TextureStateShard that sets GL_REPEAT wrapping so tiled UV coordinates
     * (in range [0, TILE_COUNT]) wrap the texture correctly.
     */
    private static class TilingTextureState extends TextureStateShard {
        public TilingTextureState(ResourceLocation texture) {
            super(texture, false, false);
        }

        @Override
        public void setupRenderState() {
            super.setupRenderState();
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        }

        @Override
        public void clearRenderState() {
            // Restore default CLAMP_TO_EDGE
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            super.clearRenderState();
        }
    }

    /**
     * Layer 1: Translucent hex grid.
     * Uses entity_translucent shader, fullbright, both sides visible.
     * Writes depth so shader-mod clouds (Iris/Oculus composite pass) render behind the shield.
     */
    public static RenderType shieldTranslucent() {
        if (SHIELD_TRANSLUCENT == null) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TilingTextureState(ShieldEnergyTexture.RD_TEXTURE_LOCATION))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false);
            SHIELD_TRANSLUCENT = create("shield_translucent",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, false, true, state);
        }
        return SHIELD_TRANSLUCENT;
    }

    /**
     * Layer 1 variant with back-face culling (hideShieldBubbleInside=true).
     * Writes depth so shader-mod clouds render behind the shield.
     */
    public static RenderType shieldTranslucentCulled() {
        if (SHIELD_TRANSLUCENT_CULLED == null) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TilingTextureState(ShieldEnergyTexture.RD_TEXTURE_LOCATION))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    // No setCullState → uses default CULL (back-face culling ON)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(false);
            SHIELD_TRANSLUCENT_CULLED = create("shield_translucent_culled",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, false, true, state);
        }
        return SHIELD_TRANSLUCENT_CULLED;
    }

    /**
     * Layer 2: Bloom edges.
     * Uses eyes shader (fullbright, no lightmap) — Iris routes to gbuffers_spidereyes → bloom.
     * No depth write, both sides visible.
     */
    public static RenderType shieldBloom() {
        if (SHIELD_BLOOM == null) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TilingTextureState(ShieldEnergyTexture.RD_TEXTURE_LOCATION))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false);
            SHIELD_BLOOM = create("shield_bloom",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, false, true, state);
        }
        return SHIELD_BLOOM;
    }

    /**
     * Layer 0: Solid color fill underlay (tinted glass behind hex grid).
     * Uses eyes shader for subtle glow, same hex grid texture at very low alpha.
     */
    public static RenderType shieldFill() {
        if (SHIELD_FILL == null) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TilingTextureState(ShieldEnergyTexture.RD_TEXTURE_LOCATION))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false);
            SHIELD_FILL = create("shield_fill",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, false, true, state);
        }
        return SHIELD_FILL;
    }

    /**
     * Layer 4: Conduit pulse network (energy packets along hex edges).
     * Uses eyes shader (bloom), reuses hex edges texture.
     */
    public static RenderType shieldConduit() {
        if (SHIELD_CONDUIT == null) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TilingTextureState(ShieldEnergyTexture.RD_TEXTURE_LOCATION))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false);
            SHIELD_CONDUIT = create("shield_conduit",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLES,
                    256, false, true, state);
        }
        return SHIELD_CONDUIT;
    }
}
