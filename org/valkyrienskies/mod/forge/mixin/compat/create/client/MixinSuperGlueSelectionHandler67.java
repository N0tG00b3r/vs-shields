package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.simibubi.create.foundation.utility.RaycastHelper;
import java.util.Iterator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * This mixin is for Create 6.0.7+
 * <br>
 * For the 6.0.6 equivalent, see {@link MixinSuperGlueSelectionHandler66}
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler")
public abstract class MixinSuperGlueSelectionHandler67 {
    @Unique
    private Vec3 newTarget;

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectGetTraceOrigin(LocalPlayer playerIn) {
        double range = playerIn.m_21051_(ForgeMod.ENTITY_REACH.get()).m_22135_() + 1;
        Vec3 origin = playerIn.m_146892_();
        Vec3 target = RaycastHelper.getTraceTarget(playerIn, range, origin);


        AABB searchAABB = new AABB(origin, target).m_82377_(0.25, 2, 0.25);
        final Iterator<Ship> ships = VSGameUtilsKt.getShipsIntersecting(playerIn.m_9236_(), searchAABB).iterator();

        if (ships.hasNext()) {
            Ship ship = ships.next();

            Matrix4d world2Ship = (Matrix4d) ship.getTransform().getWorldToShip();
            AABBic shAABBi = ship.getShipAABB();

            // Hopefully fixes https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/1341
            // Presumably the AABB is null while the ship is still loading
            // This fix is also applied on the fabric version of this class
            if (shAABBi != null) {
                AABB shipAABB = new AABB(shAABBi.minX(), shAABBi.minY(), shAABBi.minZ(), shAABBi.maxX(), shAABBi.maxY(), shAABBi.maxZ());


                origin = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(origin)));
                target = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(target)));

                Quaterniond tempQuat = new Quaterniond();
                if (playerIn.m_20202_() != null && playerIn.m_20202_().m_20191_().m_82381_(shipAABB.m_82400_(20))) {
                    ship.getTransform().getWorldToShip().getNormalizedRotation(tempQuat);
                    tempQuat.invert();
                    Vector3d offset = VectorConversionsMCKt.toJOML(target.m_82546_(origin));
                    tempQuat.transform(offset);
                    target = origin.m_82549_(VectorConversionsMCKt.toMinecraft(offset));
                }
            }
        }

        newTarget = target;
        return origin;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget(Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private Vec3 redirectGetTraceTarget(final Player playerIn, final double range, final Vec3 origin) {
        return newTarget;
    }
}
