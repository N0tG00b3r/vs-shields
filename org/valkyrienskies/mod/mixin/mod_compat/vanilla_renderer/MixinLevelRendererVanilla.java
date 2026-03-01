package org.valkyrienskies.mod.mixin.mod_compat.vanilla_renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.ListIterator;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.RenderChunkInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.util.datastructures.BlockPos2ByteOpenHashMap;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.ShipRenderer;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ShipRenderEvent;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.accessors.client.render.ViewAreaAccessor;
import org.valkyrienskies.mod.mixin.mod_compat.optifine.RenderChunkInfoAccessorOptifine;
import org.valkyrienskies.mod.mixinducks.mod_compat.vanilla_renderer.LevelRendererDuck;
import org.valkyrienskies.mod.mixinducks.client.render.LevelRendererVanillaDuck;

@Mixin(value = LevelRenderer.class, priority = 999)
public abstract class MixinLevelRendererVanilla implements LevelRendererDuck, LevelRendererVanillaDuck {
    @Unique
    private final WeakHashMap<ClientShip, ObjectList<RenderChunkInfo>> shipRenderChunks = new WeakHashMap<>();
    @Shadow
    private ClientLevel level;

    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<RenderChunkInfo> renderChunksInFrustum;

    @Shadow
    private @Nullable ViewArea viewArea;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private AtomicBoolean needsFrustumUpdate;

    @Unique
    private BlockPos2ByteOpenHashMap vs$visibileShipChunks = new BlockPos2ByteOpenHashMap();
    @Unique
    private Long lastMountedShipId = null;
    @Unique
    private ShipTransform lastTransform = null;

    /**
     * Fix the distance to render chunks, so that MC doesn't think ship chunks are too far away
     */
    @Redirect(
        method = "compileChunks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"
        ),
        require = 0
    )
    private double includeShipChunksInNearChunks(final BlockPos b, final Vec3i v) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            level, b.m_123341_(), b.m_123342_(), b.m_123343_(), v.m_123341_(), v.m_123342_(), v.m_123343_()
        );
    }

    /**
     * Force frustum update if the ship moves and the camera doesn't
     */
    @ModifyExpressionValue(
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicBoolean;compareAndSet(ZZ)Z"
        ),
        method = "setupRender"
    )
    private boolean getNeedsFrustumUpdate(final boolean needsFrustumUpdate) {
        // force frustum update if default behaviour says to OR if the player is mounted to a ship
        final Player player = this.minecraft.f_91074_;
        if (player == null || !(VSGameUtilsKt.getShipMountedTo(player) instanceof final ClientShip ship)) {
            this.lastMountedShipId = null;
            return needsFrustumUpdate;
        }
        final ShipTransform transform = ship.getRenderTransform();
        if (this.lastMountedShipId == null || this.lastMountedShipId.longValue() != ship.getId() || this.lastTransform == null) {
            this.lastMountedShipId = ship.getId();
            this.lastTransform = transform;
            return true;
        }
        final boolean needUpdate = this.lastTransform != transform && !this.lastTransform.getShipToWorld().equals(transform.getShipToWorld());
        this.lastTransform = transform;
        return needUpdate;
    }

    @Override
    public void vs$setNeedsFrustumUpdate() {
        this.needsFrustumUpdate.set(true);
    }

    /**
     * Add ship render chunks to [renderChunks]
     */
    @Inject(
        method = "setupRender",
        at = @At("RETURN")
    )
    private void preSetupRender(final Camera camera, final Frustum frustum, final boolean bl, final boolean bl2, final CallbackInfo ci) {
        // This mixin never gets called for IP dimensions, instead we'll call it manually
        vs$addShipVisibleChunks(frustum);
    }

    @Override
    public void vs$addShipVisibleChunks(final Frustum frustum) {
        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
        final ViewAreaAccessor chunkStorageAccessor = (ViewAreaAccessor) viewArea;
        for (final ClientShip shipObject : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (ShipRendererKt.getShipRenderer(shipObject) != ShipRenderer.VANILLA)
                continue;

            // Don't bother rendering the ship if its AABB isn't visible to the frustum
            if (!frustum.m_113029_(VectorConversionsMCKt.toMinecraft(shipObject.getRenderAABB())))
                continue;

            shipObject.getActiveChunksSet().forEach((x, z) -> {
                final LevelChunk levelChunk = level.m_6325_(x, z);
                for (int y = level.m_151560_(); y < level.m_151561_(); y++) {
                    // Don't add ship chunks more than once
                    if (vs$visibileShipChunks.contains(x, y, z)) {
                        continue;
                    }
                    tempPos.m_122178_(x << 4, y << 4, z << 4);
                    final ChunkRenderDispatcher.RenderChunk renderChunk =
                        chunkStorageAccessor.callGetRenderChunkAt(tempPos);
                    if (renderChunk != null) {
                        // If the chunk section is empty then skip it
                        final LevelChunkSection levelChunkSection = levelChunk.m_183278_(y - level.m_151560_());
                        if (levelChunkSection.m_188008_()) {
                            continue;
                        }

                        // If the chunk isn't in the frustum then skip it
                        final AABBd b2 = new AABBd((x << 4) - 6e-1, (y << 4) - 6e-1, (z << 4) - 6e-1,
                            (x << 4) + 15.6, (y << 4) + 15.6, (z << 4) + 15.6)
                            .transform(shipObject.getRenderTransform().getShipToWorld());

                        if (!frustum.m_113029_(VectorConversionsMCKt.toMinecraft(b2))) {
                            continue;
                        }

                        final LevelRenderer.RenderChunkInfo newChunkInfo;
                        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.OPTIFINE) {
                            newChunkInfo =
                                RenderChunkInfoAccessorOptifine.vs$new(renderChunk, null, 0);
                        } else {
                            newChunkInfo =
                                RenderChunkInfoAccessor.vs$new(renderChunk, null, 0);
                        }
                        shipRenderChunks.computeIfAbsent(shipObject, k -> new ObjectArrayList<>()).add(newChunkInfo);
                        vs$visibileShipChunks.put(x, y, z, (byte) 1);
                        renderChunksInFrustum.add(newChunkInfo);
                    }
                }
            });
        }
    }

    @WrapOperation(
        method = "*",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D")
    )
    private double distToShips(BlockPos from, Vec3i to, Operation<Double> distSqr){
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(level, from.m_252807_(), Vec3.m_82512_(to), distSqr);
    }

    @Inject(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;clear()V"
        )
    )
    private void clearShipChunks(final CallbackInfo ci) {
        shipRenderChunks.forEach((ship, chunks) -> chunks.clear());
        vs$visibileShipChunks = new BlockPos2ByteOpenHashMap();
    }

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V"
        ),
        method = "renderLevel"
    )
    private void redirectRenderChunkLayer(final LevelRenderer receiver,
        final RenderType renderType, final PoseStack poseStack, final double camX, final double camY, final double camZ,
        final Matrix4f matrix4f, final Operation<Void> renderChunkLayer) {

        renderChunkLayer.call(receiver, renderType, poseStack, camX, camY, camZ, matrix4f);

        VSGameEvents.INSTANCE.getShipsStartRendering().emit(new VSGameEvents.ShipStartRenderEvent(
            receiver, renderType, poseStack, camX, camY, camZ, matrix4f
        ));

        shipRenderChunks.forEach((ship, chunks) -> {
            poseStack.m_85836_();
            final ShipTransform shipTransform = ship.getRenderTransform();
            final Vector3dc cameraShipSpace = shipTransform.getWorldToShip().transformPosition(new Vector3d(camX, camY, camZ));
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), poseStack,
                cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z(),
                camX, camY, camZ);

            final var event = new VSGameEvents.ShipRenderEvent(
                receiver, renderType, poseStack, camX, camY, camZ, matrix4f, ship, chunks
            );

            VSGameEvents.INSTANCE.getRenderShip().emit(event);
            renderChunkLayer(renderType, poseStack, cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z(), matrix4f, chunks);
            VSGameEvents.INSTANCE.getPostRenderShip().emit(event);

            poseStack.m_85849_();
        });
    }


    @Unique
    private void renderChunkLayer(final RenderType renderType, final PoseStack poseStack, final double d,
        final double e, final double f,
        final Matrix4f matrix4f, final ObjectList<RenderChunkInfo> chunksToRender) {
        RenderSystem.assertOnRenderThread();
        renderType.m_110185_();
        this.minecraft.m_91307_().m_6180_("filterempty");
        this.minecraft.m_91307_().m_6523_(() -> {
            return "render_" + renderType;
        });
        boolean bl = renderType != RenderType.m_110466_();
        final ListIterator objectListIterator = chunksToRender.listIterator(bl ? 0 : chunksToRender.size());
        ShaderInstance shaderInstance = RenderSystem.getShader();

        for(int k = 0; k < 12; ++k) {
            int l = RenderSystem.getShaderTexture(k);
            shaderInstance.m_173350_("Sampler" + k, l);
        }

        if (shaderInstance.f_173308_ != null) {
            shaderInstance.f_173308_.m_5679_(poseStack.m_85850_().m_252922_());
        }

        if (shaderInstance.f_173309_ != null) {
            shaderInstance.f_173309_.m_5679_(matrix4f);
        }

        if (shaderInstance.f_173312_ != null) {
            shaderInstance.f_173312_.m_5941_(RenderSystem.getShaderColor());
        }

        if (shaderInstance.f_173315_ != null) {
            shaderInstance.f_173315_.m_5985_(RenderSystem.getShaderFogStart());
        }

        if (shaderInstance.f_173316_ != null) {
            shaderInstance.f_173316_.m_5985_(RenderSystem.getShaderFogEnd());
        }

        if (shaderInstance.f_173317_ != null) {
            shaderInstance.f_173317_.m_5941_(RenderSystem.getShaderFogColor());
        }

        if (shaderInstance.f_202432_ != null) {
            shaderInstance.f_202432_.m_142617_(RenderSystem.getShaderFogShape().m_202324_());
        }

        if (shaderInstance.f_173310_ != null) {
            shaderInstance.f_173310_.m_5679_(RenderSystem.getTextureMatrix());
        }

        if (shaderInstance.f_173319_ != null) {
            shaderInstance.f_173319_.m_5985_(RenderSystem.getShaderGameTime());
        }

        RenderSystem.setupShaderLights(shaderInstance);
        shaderInstance.m_173363_();
        Uniform uniform = shaderInstance.f_173320_;

        while(true) {
            if (bl) {
                if (!objectListIterator.hasNext()) {
                    break;
                }
            } else if (!objectListIterator.hasPrevious()) {
                break;
            }

            RenderChunkInfo renderChunkInfo2 = bl ? (RenderChunkInfo)objectListIterator.next() : (RenderChunkInfo)objectListIterator.previous();
            ChunkRenderDispatcher.RenderChunk renderChunk = renderChunkInfo2.f_109839_;
            if (!renderChunk.m_112835_().m_112758_(renderType)) {
                VertexBuffer vertexBuffer = renderChunk.m_112807_(renderType);
                BlockPos blockPos = renderChunk.m_112839_();
                if (uniform != null) {
                    uniform.m_5889_((float)((double)blockPos.m_123341_() - d), (float)((double)blockPos.m_123342_() - e), (float)((double)blockPos.m_123343_() - f));
                    uniform.m_85633_();
                }

                vertexBuffer.m_85921_();
                vertexBuffer.m_166882_();
            }
        }

        if (uniform != null) {
            uniform.m_142276_(new Vector3f());
        }

        shaderInstance.m_173362_();
        VertexBuffer.m_85931_();
        this.minecraft.m_91307_().m_7238_();
        renderType.m_110188_();
    }
}
