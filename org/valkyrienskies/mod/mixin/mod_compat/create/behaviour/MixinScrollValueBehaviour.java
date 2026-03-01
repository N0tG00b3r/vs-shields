package org.valkyrienskies.mod.mixin.mod_compat.create.behaviour;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.bodies.properties.BodyTransform;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ScrollValueBehaviour.class)
public class MixinScrollValueBehaviour {

    @Redirect(
            method = "testHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    public Vec3 redirectSubtract(Vec3 instance, Vec3 vec) {
        Level level = ((ScrollValueBehaviour) (Object) this).getWorld();

        Vec3 pos1 = instance;
        Vec3 pos2 = vec;

        if (level != null) {
            Ship ship1 = VSGameUtilsKt.getShipManagingPos(level, pos1.f_82479_, pos1.f_82480_, pos1.f_82481_);
            Ship ship2 = VSGameUtilsKt.getShipManagingPos(level, pos2.f_82479_, pos2.f_82480_, pos2.f_82481_);
            if (ship1 != null && ship2 == null) {
                BodyTransform transform = ship1 instanceof ClientShip cs ?
                        cs.getRenderTransform() : ship1.getTransform();
                pos2 = VectorConversionsMCKt.toMinecraft(
                        transform.getToModel().transformPosition(VectorConversionsMCKt.toJOML(pos2))
                );
            } else if (ship1 == null && ship2 != null) {
                BodyTransform transform = ship2 instanceof ClientShip cs ?
                    cs.getRenderTransform() : ship2.getTransform();
                pos1 = VectorConversionsMCKt.toMinecraft(
                        transform.getToModel().transformPosition(VectorConversionsMCKt.toJOML(pos1))
                );
            }
        }
        return pos1.m_82546_(pos2);
    }
}

