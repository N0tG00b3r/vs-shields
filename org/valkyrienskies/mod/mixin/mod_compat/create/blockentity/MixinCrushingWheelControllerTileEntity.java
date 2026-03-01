package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(CrushingWheelControllerBlockEntity.class)
public abstract class MixinCrushingWheelControllerTileEntity extends SmartBlockEntity {

    @Shadow
    public Entity processingEntity;

    public MixinCrushingWheelControllerTileEntity(BlockEntityType<?> type,
        BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<Entity> redirectBounds(Level instance, Entity entity, AABB area, Predicate<Entity> predicate) {
        if (instance != null) {
            area = VSGameUtilsKt.transformAabbToWorld(instance, area);
            return instance.m_6249_(entity, area, predicate);
        }
        return new ArrayList<>();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean redirectIntersects(AABB instance, AABB other) {
        Level level = this.m_58904_();
        if (level != null) {
            Iterator<Ship> ships = VSGameUtilsKt.getShipsIntersecting(level, instance).iterator();
            if (ships.hasNext()) {
                AABBd result = new AABBd();
                VectorConversionsMCKt.toJOML(instance).transform(ships.next().getTransform().getWorldToShip(), result);
                instance = VectorConversionsMCKt.toMinecraft(result);
            }
        }
        return instance.m_82381_(other);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void redirectSetDeltaMovement(Entity instance, Vec3 motion) {
        Vec3 transformedDirection = directionShip2World(motion);
        instance.m_20256_(transformedDirection);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"))
    private void redirectSetPosition(Entity instance, double d, double e, double f, Operation<Void> original){
        Vector3d worldCoord = VSGameUtilsKt.toWorldCoordinates(m_58904_(), d, e, f);
        original.call(instance, worldCoord.x, worldCoord.y, worldCoord.z);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getX()D"))
    private double redirectEntityGetX(Entity instance) {
        return getTransformedPosition(instance).f_82479_;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D"))
    private double redirectEntityGetY(Entity instance) {
        return getTransformedPosition(instance).f_82480_;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getZ()D"))
    private double redirectEntityGetZ(Entity instance) {
        return getTransformedPosition(instance).f_82481_;
    }

    private Vec3 getTransformedPosition(Entity instance) {
        Vec3 result = instance.m_20182_();
        Ship ship = VSGameUtilsKt.getShipManagingPos(this.m_58904_(), this.m_58899_());
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getWorldToShip().transformPosition(result.f_82479_, result.f_82480_, result.f_82481_, tempVec);
            result = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return result;
    }

    @ModifyVariable(
        method = "tick",
        at = @At("STORE"),
        name = "xMotion",
        remap = false
    )
    private double doubleXMotion(double original) {
        return ((f_58858_.m_123341_() + .5) - redirectEntityGetX(processingEntity)) / 2.0;
    }


    @ModifyVariable(
        method = "tick",
        at = @At("STORE"),
        name = "zMotion",
        remap = false
    )
    private double doubleZMotion(double original) {
        return ((f_58858_.m_123343_() + .5) - redirectEntityGetZ(processingEntity)) / 2.0;
    }

    private Vec3 directionShip2World(Vec3 direction) {
        Vec3 result = direction;
        Ship ship = VSGameUtilsKt.getShipManagingPos(this.m_58904_(), this.m_58899_());
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformDirection(result.f_82479_, result.f_82480_, result.f_82481_, tempVec);
            result = VectorConversionsMCKt.toMinecraft(tempVec);
        }
        return result;
    }
}
