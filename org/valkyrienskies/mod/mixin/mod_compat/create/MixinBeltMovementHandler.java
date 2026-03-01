package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.BeltMovementHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BeltMovementHandler.class)
public abstract class MixinBeltMovementHandler {

    @Unique
    private static Vector3d blockPos;

    @Unique
    private static Level level;

    @Unique
    private static Ship ship;

    @Unique
    private static Direction.Axis axis;

    @Unique
    private static Entity entity;

    @Inject(method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;fromAxisAndDirection(Lnet/minecraft/core/Direction$Axis;Lnet/minecraft/core/Direction$AxisDirection;)Lnet/minecraft/core/Direction;"), locals = LocalCapture.CAPTURE_FAILHARD)
    private static void injectHead(BeltBlockEntity beltTe, Entity entityIn, BeltMovementHandler.TransportedEntityInfo info, CallbackInfo ci, BlockPos pos) {
        blockPos = VectorConversionsMCKt.toJOMLD(pos);
        entity = entityIn;
        level = beltTe.m_58904_();
        if (level != null) {
            ship = VSGameUtilsKt.getShipManagingPos(level, blockPos);
        }
    }

    @ModifyVariable(method = "transportEntity", at = @At(value = "STORE"), name = "axis")
    private static Direction.Axis injectHarvestAxis(Direction.Axis value) {
        axis = value;
        return value;
    }

    @Redirect(method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", ordinal = 2))
    private static void redirectMove1(Entity instance, MoverType type, Vec3 pos) {
        if (ship != null) {
            instance.m_6478_(type, new Vec3(pos.f_82479_ * 3, 0.2, pos.f_82481_ * 3));
        } else
            instance.m_6478_(type, pos);
    }

    @Redirect(method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", ordinal = 1))
    private static void redirectMove2(Entity instance, MoverType type, Vec3 pos) {
        if (ship != null) {
            instance.m_6478_(type, new Vec3(pos.f_82479_ * 3, 0, pos.f_82481_ * 3));
        } else
            instance.m_6478_(type, pos);
    }

    @Redirect(method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction$Axis;choose(DDD)D"))
    private static double redirectChoose(Direction.Axis instance, double x, double y, double z) {
        if (ship != null) {
            Vec3 mul = new Vec3(0, 0, 0);
            if (instance == Direction.Axis.X) {
                mul = redirectGetNormal(new Vec3i(1, 0, 0));
            }
            if (instance == Direction.Axis.Y) {
                mul = redirectGetNormal(new Vec3i(0, 1, 0));
            }
            if (instance == Direction.Axis.Z) {
                mul = redirectGetNormal(new Vec3i(0, 0, 1));
            }
            return Math.abs(x * mul.f_82479_) + Math.abs(y * mul.f_82480_) + Math.abs(z * mul.f_82481_);
        }
        return instance.m_6150_(x, y, z);
    }

    @ModifyVariable(method = "transportEntity", at = @At(value = "STORE"), name = "diffCenter")
    private static double modDiffCenter(double value) {
        //if (ship != null) value = value + Math.copySign(value) );
        return axis == Direction.Axis.Z ? (blockPos.x + .5 - getPos(entity).f_82479_) : (blockPos.z + .5 - getPos(entity).f_82481_);
    }

    @Redirect(
            method = "transportEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 redirectGetNormal(Vec3i toCopy) {
        Vec3 result = Vec3.m_82528_(toCopy);
        if (level != null) {
            if (ship != null) {
                Vector3d tempVec = VectorConversionsMCKt.toJOML(result);
                Quaterniond tempQuat = new Quaterniond();
                ship.getTransform().getShipToWorld().getNormalizedRotation(tempQuat);
                tempVec.rotate(tempQuat);
                result = VectorConversionsMCKt.toMinecraft(tempVec);
            }
        }
        return result;
    }

    @Redirect(method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private static double redirectGetX(Entity instance) {
        return getPos(instance).f_82479_;
    }

    @Redirect(method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private static double redirectGetY(Entity instance) {
        return getPos(instance).f_82480_;
    }

    @Redirect(method = "transportEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private static double redirectGetZ(Entity instance) {
        return getPos(instance).f_82481_;
    }

    @Unique
    private static Vec3 getPos(Entity entity) {
        Vec3 result = entity.m_20182_();
        if (level != null) {
            if (ship != null) {
                Vector3d tempVec = VectorConversionsMCKt.toJOML(result);
                ship.getTransform().getWorldToShip().transformPosition(tempVec);
                result = VectorConversionsMCKt.toMinecraft(tempVec);
            }
        }
        return result;
    }
}
