package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.Translate;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * SchematicTransformation is responsible for the render transform of the schematic preview
 * <p>
 * Create applies both the camera and schematic positions in the same operation, the latter of which does not respect ship-space.
 * This mixin redirects the operation and fixes it by extracting the position components from the argument.
 * I can't think of a better way to get around it.
 */
@Mixin(value = {SchematicTransformation.class}, remap = false)
public abstract class MixinSchematicTransformation {
    @Shadow
    private BlockPos target;
    @Shadow
    private Vec3 chasingPos;
    @Shadow
    private Vec3 prevChasingPos;

    @Redirect(
        method = {"applyTransformations(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;)V"},
        at = @At(
            value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/transform/PoseTransformStack;translate(Lnet/minecraft/world/phys/Vec3;)Ldev/engine_room/flywheel/lib/transform/Translate;",
            ordinal = 0
        ),
        require = 0
    )
    private Translate<?> redirectTranslate(PoseTransformStack instance, Vec3 orig) {
        PoseStack ms = instance.unwrap();
        Ship ship = VSGameUtilsKt.getLoadedShipManagingPos(Minecraft.m_91087_().f_91073_, target.m_123341_(), target.m_123342_(), target.m_123343_());

        if (ship != null) {
            float pt = AnimationTickHolder.getPartialTicks();
            Vec3 pos = VecHelper.lerp(pt, prevChasingPos, chasingPos);
            Vec3 camera = pos.m_82546_(orig);
            VSClientGameUtils.transformRenderWithShip(ship.getTransform(), ms, pos.f_82479_, pos.f_82480_, pos.f_82481_, camera.f_82479_, camera.f_82480_, camera.f_82481_);
            return instance;
        } else {
            return instance.translate(orig);
        }
    }
}
