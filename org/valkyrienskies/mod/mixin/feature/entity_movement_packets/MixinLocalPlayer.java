package org.valkyrienskies.mod.mixin.feature.entity_movement_packets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.networking.PacketEntityShipMotion;
import org.valkyrienskies.mod.common.networking.PacketPlayerShipMotion;
import org.valkyrienskies.mod.common.util.EntityLerper;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends Entity implements IEntityDraggingInformationProvider {
    @Shadow
    public abstract float m_5675_(float f);

    public MixinLocalPlayer(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @author Tomato
     * @reason Intercept client -> server player position sending to send our own data.
     */
    @WrapOperation(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendPosition(ClientPacketListener instance, Packet<?> arg, Operation<Void> original) {
        Packet<?> realArg = arg;
        if (getDraggingInformation().isEntityBeingDraggedByAShip()) {
            if (getDraggingInformation().getLastShipStoodOn() != null) {
                ClientShip ship = VSGameUtilsKt.getShipObjectWorld(Minecraft.m_91087_().f_91073_).getAllShips().getById(getDraggingInformation().getLastShipStoodOn());
                if (ship != null) {
                    Vector3dc relativePosition = ship.getWorldToShip().transformPosition(
                        VectorConversionsMCKt.toJOML(m_20318_(1f)), new Vector3d());

                    double relativeYaw = EntityLerper.INSTANCE.yawToShip(ship, m_5675_(1f));

                    PacketPlayerShipMotion packet = new PacketPlayerShipMotion(ship.getId(), relativePosition.x(), relativePosition.y(), relativePosition.z(), relativeYaw);
                    ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
                }
            }
            if (realArg instanceof ServerboundMovePlayerPacket movePacket) {
                final boolean isOnGround = movePacket.m_134139_() || getDraggingInformation().isEntityBeingDraggedByAShip();
                if (movePacket.m_179683_() && movePacket.m_179684_()) {
                    //posrot
                    realArg = new ServerboundMovePlayerPacket.PosRot(movePacket.m_134129_(0.0), movePacket.m_134140_(0.0), movePacket.m_134146_(0.0), movePacket.m_134131_(0.0f), movePacket.m_134142_(0.0f), isOnGround);
                } else if (movePacket.m_179683_()) {
                    //pos
                    realArg = new ServerboundMovePlayerPacket.Pos(movePacket.m_134129_(0.0), movePacket.m_134140_(0.0), movePacket.m_134146_(0.0), isOnGround);
                } else if (movePacket.m_179684_()) {
                    //rot
                    realArg = new ServerboundMovePlayerPacket.Rot(movePacket.m_134131_(0.0f), movePacket.m_134142_(0.0f), isOnGround);
                } else {
                    //status only
                    realArg = new ServerboundMovePlayerPacket.StatusOnly(isOnGround);
                }
            }
        }
        original.call(instance, realArg);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void wrapSendVehiclePosition(ClientPacketListener instance, Packet<?> arg, Operation<Void> original) {
        if (arg instanceof ServerboundMoveVehiclePacket vehiclePacket && m_20201_() instanceof IEntityDraggingInformationProvider dragProvider) {
            if (dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
                if (dragProvider.getDraggingInformation().getLastShipStoodOn() != null) {
                    ClientShip ship = VSGameUtilsKt.getShipObjectWorld(Minecraft.m_91087_().f_91073_).getAllShips().getById(
                        dragProvider.getDraggingInformation().getLastShipStoodOn());
                    if (ship != null) {
                        Vector3dc relativePosition = ship.getWorldToShip().transformPosition(
                            VectorConversionsMCKt.toJOML(m_20201_().m_20318_(1f)), new Vector3d());

                        double relativeYaw = EntityLerper.INSTANCE.yawToShip(ship, m_20201_().m_146908_());

                        PacketEntityShipMotion packet = new PacketEntityShipMotion(m_20201_().m_19879_(), ship.getId(), relativePosition.x(), relativePosition.y(), relativePosition.z(), 0.0, 0.0, 0.0, relativeYaw, 0.0);
                        ValkyrienSkiesMod.getVsCore().getSimplePacketNetworking().sendToServer(packet);
                    }
                }
            }
        }
        original.call(instance, arg);
    }

}
