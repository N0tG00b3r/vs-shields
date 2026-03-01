package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(NozzleBlockEntity.class)
public abstract class MixinNozzleBlockEntity extends SmartBlockEntity {
    @Shadow
    private float range;

    public MixinNozzleBlockEntity(BlockEntityType<?> type,
        BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @WrapOperation(
        method = {"tick", "lazyTick"},
        at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 getCenterInWorld(Vec3i pos, Operation<Vec3> original) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(this.f_58857_, this.m_58899_());
        if(ship != null) {
            Vector3d posJOML = VectorConversionsMCKt.toJOML(original.call(pos));
            ship.getTransform().getShipToWorld().transformPosition(posJOML);
            return VectorConversionsMCKt.toMinecraft(posJOML);
        }
        return original.call(pos);
    }

    @Inject(
        method = "canSee",
        at = @At("HEAD"),
        cancellable = true
    )
    private void canSeeWithShip(Entity entity, CallbackInfoReturnable<Boolean> cir){
        Vec3 nozzlePosWorld = f_58858_.m_252807_();
        Vec3 entityPosWorld = entity.m_20182_();
        Ship nozzleShip = VSGameUtilsKt.getShipManagingPos(this.f_58857_, f_58858_);
        Ship entityShip = VSGameUtilsKt.getShipManaging(entity);
        if (nozzleShip != null) {
            Vector3d worldPos = VectorConversionsMCKt.toJOML(f_58858_.m_252807_());
            nozzleShip.getShipToWorld().transformPosition(worldPos);
            nozzlePosWorld = VectorConversionsMCKt.toMinecraft(worldPos);
        }
        if(!VSGameUtilsKt.getShipsIntersecting(f_58857_, new AABB(nozzlePosWorld, nozzlePosWorld).m_82400_(range / 2f)).iterator().hasNext()) {
            return;
        }
        if (entityShip != null) {
            Vector3d worldPos = VectorConversionsMCKt.toJOML(entity.m_20182_());
            nozzleShip.getShipToWorld().transformPosition(worldPos);
            entityPosWorld = VectorConversionsMCKt.toMinecraft(worldPos);
        }
        ClipContext context = new ClipContext(entityPosWorld, nozzlePosWorld, Block.COLLIDER, Fluid.NONE, entity);
        BlockHitResult result = RaycastUtilsKt.clipIncludeShips(f_58857_, context);
        cir.setReturnValue(result.m_82425_().equals(f_58858_));
    }
}
