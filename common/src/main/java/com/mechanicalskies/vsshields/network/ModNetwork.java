package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.shield.CloakManager;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.Map;

import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;

/**
 * Cross-platform network registration using Architectury NetworkManager.
 */
public class ModNetwork {
    public static final ResourceLocation SHIELD_SYNC_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "shield_sync");
    public static final ResourceLocation SHIELD_TOGGLE_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "shield_toggle");
    public static final ResourceLocation SHIELD_HIT_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "shield_hit");
    public static final ResourceLocation SHIELD_BREAK_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "shield_break");
    public static final ResourceLocation NUKE_VISUAL_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "nuke_visual");
    public static final ResourceLocation CLOAK_TOGGLE_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "cloak_toggle");

    public static void init() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                SHIELD_TOGGLE_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    boolean active = buf.readBoolean();
                    context.queue(() -> handleToggle(context.getPlayer(), shipId, active));
                });

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CLOAK_TOGGLE_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    boolean active = buf.readBoolean();
                    context.queue(() -> CloakManager.getInstance().toggleCloak(shipId, active));
                });

        // Register S2C Receivers (Client Only)
        EnvExecutor.runInEnv(Env.CLIENT, () -> () -> {
            ClientNetworkHandler.registerS2C();
        });
    }

    public static void sendNukeVisual(MinecraftServer server, double x, double y, double z) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), NUKE_VISUAL_ID, buf);
    }

    /**
     * Send a shield hit effect to all clients.
     * 
     * @param shipId the ship that was hit
     * @param x,y,z  world-space hit position
     * @param damage amount of damage dealt (used to scale particle intensity)
     */
    public static void sendShieldHit(MinecraftServer server, long shipId, double x, double y, double z, float damage) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(shipId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(damage);
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), SHIELD_HIT_ID, buf);
    }

    /**
     * Send a shield break effect to all clients.
     */
    public static void sendShieldBreak(MinecraftServer server, long shipId) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(shipId);
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), SHIELD_BREAK_ID, buf);
    }

    public static void sendSyncToPlayer(ServerPlayer player) {
        writeAndSendSync(player.server, buf -> NetworkManager.sendToPlayer(player, SHIELD_SYNC_ID, buf));
    }

    public static void sendSyncToAll(MinecraftServer server) {
        writeAndSendSync(server,
                buf -> NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), SHIELD_SYNC_ID, buf));
    }

    private static void writeAndSendSync(MinecraftServer server, java.util.function.Consumer<FriendlyByteBuf> sender) {
        ShieldManager mgr = ShieldManager.getInstance();
        Map<Long, ShieldInstance> shields = mgr.getAllShields();

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(shields.size());
        for (ShieldInstance shield : shields.values()) {
            buf.writeLong(shield.getShipId());
            buf.writeDouble(shield.getCurrentHP());
            buf.writeDouble(shield.getMaxHP());
            buf.writeBoolean(shield.isActive());
            buf.writeDouble(shield.getEnergyPercent());

            // Include ship world-space AABB for client-side proximity checks
            BlockPos ownerPos = mgr.getShieldOwnerPos(shield.getShipId());
            AABBdc worldAABB = null;
            if (ownerPos != null) {
                for (ServerLevel level : server.getAllLevels()) {
                    Ship ship = VSGameUtilsKt.getShipManagingPos(level, ownerPos);
                    if (ship != null) {
                        worldAABB = ship.getWorldAABB();
                        break;
                    }
                }
            }
            if (worldAABB != null) {
                buf.writeBoolean(true);
                buf.writeDouble(worldAABB.minX());
                buf.writeDouble(worldAABB.minY());
                buf.writeDouble(worldAABB.minZ());
                buf.writeDouble(worldAABB.maxX());
                buf.writeDouble(worldAABB.maxY());
                buf.writeDouble(worldAABB.maxZ());
            } else {
                buf.writeBoolean(false);
            }
        }
        sender.accept(buf);
    }

    public static void sendToggleToServer(long shipId, boolean active) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(shipId);
        buf.writeBoolean(active);
        NetworkManager.sendToServer(SHIELD_TOGGLE_ID, buf);
    }

    public static void sendCloakToggleToServer(long shipId, boolean active) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(shipId);
        buf.writeBoolean(active);
        NetworkManager.sendToServer(CLOAK_TOGGLE_ID, buf);
    }

    private static void handleToggle(Player player, long shipId, boolean active) {
        ShieldInstance shield = ShieldManager.getInstance().getShield(shipId);
        if (shield != null) {
            shield.setActive(active);
        }
    }
}
