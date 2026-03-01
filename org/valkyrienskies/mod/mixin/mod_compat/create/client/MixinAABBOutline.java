package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.AABBOutline;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.mod_compat.create.accessors.OutlineParamsAccessor;

@Mixin(AABBOutline.class)
public abstract class MixinAABBOutline extends Outline {
    @Shadow
    protected abstract void renderBoxFaces(PoseStack ms, SuperRenderTypeBuffer buffer, boolean cull, Direction highlightedFace, Vector3f minPos, Vector3f maxPos, Vector4f color, int lightmap);

    @Shadow
    protected abstract void renderBoxEdges(PoseStack ms, VertexConsumer consumer, Vector3f minPos, Vector3f maxPos, float lineWidth, Vector4f color, int lightmap, boolean disableNormals);

    @Inject(method = "renderBox", at = @At("HEAD"), cancellable = true)
    private void preRenderBox(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, AABB box, Vector4f color, int lightmap, boolean disableLineNormals, CallbackInfo ci) {
        final Level level = Minecraft.m_91087_().f_91073_;
        if (level != null) {
            final Vector3dc average = VectorConversionsMCKt.toJOML(box.m_82399_());
            final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, average);
            if (ship != null) {
                final ShipTransform renderTransform = ship.getRenderTransform();
                Vector3f minPos = new Vector3f();
                Vector3f maxPos = new Vector3f();

                final Vector3dc cameraInShip = renderTransform.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(camera));

                boolean cameraInside = box.m_82390_(VectorConversionsMCKt.toMinecraft(cameraInShip));
                boolean cull = !cameraInside && !((OutlineParamsAccessor) params).getDisableCull();
                float inflate = cameraInside ? -1 / 128f : 1 / 128f;

                final AABB boxRelShipCenter = box.m_82386_(-renderTransform.getPositionInShip().x(), -renderTransform.getPositionInShip().y(), -renderTransform.getPositionInShip().z());

                minPos.set((float) boxRelShipCenter.f_82288_ - inflate, (float) boxRelShipCenter.f_82289_ - inflate, (float) boxRelShipCenter.f_82290_ - inflate);
                maxPos.set((float) boxRelShipCenter.f_82291_ + inflate, (float) boxRelShipCenter.f_82292_ + inflate, (float) boxRelShipCenter.f_82293_ + inflate);

                ms.m_85836_();
                ms.m_85837_(renderTransform.getPositionInWorld().x() - camera.f_82479_, renderTransform.getPositionInWorld().y() - camera.f_82480_, renderTransform.getPositionInWorld().z() - camera.f_82481_);
                ms.m_85841_((float) renderTransform.getShipToWorldScaling().x(), (float) renderTransform.getShipToWorldScaling().y(), (float) renderTransform.getShipToWorldScaling().z());
                ms.m_252781_(VectorConversionsMCKt.toFloat(renderTransform.getShipToWorldRotation()));
                renderBoxFaces(ms, buffer, cull, params.getHighlightedFace(), minPos, maxPos, color, lightmap);

                float lineWidth = params.getLineWidth();
                if (lineWidth == 0)
                    return;

                VertexConsumer consumer = buffer.m_6299_(PonderRenderTypes.outlineSolid());
                renderBoxEdges(ms, consumer, minPos, maxPos, lineWidth, color, lightmap, disableLineNormals);

                ms.m_85849_();
                ci.cancel();
            }
        }
    }
}
