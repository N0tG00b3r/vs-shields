package org.valkyrienskies.mod.mixin.mod_compat.create.packets;

import com.simibubi.create.content.contraptions.glue.SuperGlueRemovalPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(SuperGlueRemovalPacket.class)
public abstract class MixinSuperGlueRemovalPacket {
    @Redirect(method = "lambda$handle$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double redirectPlayerDistanceToSqr(final ServerPlayer instance, final Vec3 vec3) {
        Vec3 newVec3 = vec3;
        if (VSGameUtilsKt.isBlockInShipyard(instance.m_9236_(), BlockPos.m_274561_(vec3.f_82479_, vec3.f_82480_, vec3.f_82481_))) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos(instance.m_9236_(), vec3);
            if (ship != null) {
                newVec3 = VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformPosition(new Vector3d(vec3.f_82479_, vec3.f_82480_, vec3.f_82481_)));
            }
        }
        return instance.m_20238_(newVec3);
    }
}
