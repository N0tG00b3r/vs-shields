package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.tools.DeployTool;
import com.simibubi.create.content.schematics.client.tools.SchematicToolBase;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * DeployTool is responsible for the render transform of the placement bounding box (not the preview)
 * <p>
 * Create applies both the camera and bounding-box position in the same PoseStack operation,
 * the latter of which does not respect ship-space.
 * This mixin cancels the aforementioned operation and injects the fix in front.
 */
@Mixin(value={DeployTool.class})
public abstract class MixinDeployTool extends SchematicToolBase {
    @Redirect(
        method = "renderTool",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            ordinal = 0
        )
    )
    private void redirectTranslate(PoseStack ms, double _x, double _y, double _z) {
    }

    @Inject(
        method = "renderTool",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            ordinal = 0
        )
    )
    private void mixinRenderTool(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, CallbackInfo ci) {
        float pt = AnimationTickHolder.getPartialTicks();
        double x = Mth.m_14139_(pt, lastChasingSelectedPos.f_82479_, chasingSelectedPos.f_82479_);
        double y = Mth.m_14139_(pt, lastChasingSelectedPos.f_82480_, chasingSelectedPos.f_82480_);
        double z = Mth.m_14139_(pt, lastChasingSelectedPos.f_82481_, chasingSelectedPos.f_82481_);
        Ship ship = VSGameUtilsKt.getLoadedShipManagingPos(Minecraft.m_91087_().f_91073_, x, y, z);

        AABB bounds = schematicHandler.getBounds();
        Vec3 center = bounds.m_82399_();
        int centerX = (int) center.f_82479_;
        int centerZ = (int) center.f_82481_;

        if (ship != null) {
            VSClientGameUtils.transformRenderWithShip(ship.getTransform(), ms, x - centerX, y, z - centerZ, camera.f_82479_, camera.f_82480_, camera.f_82481_);
        } else {
            ms.m_85837_(x - centerX - camera.f_82479_, y - camera.f_82480_, z - centerZ - camera.f_82481_);
        }
    }
}
