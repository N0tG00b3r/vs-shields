package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.shield.CloakManager;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * Cross-platform network registration using Architectury NetworkManager.
 */
public class ModNetwork {
    public static final ResourceLocation SHIELD_SYNC_ID =
            new ResourceLocation(VSShieldsMod.MOD_ID, "shield_sync");
    public static final ResourceLocation SHIELD_TOGGLE_ID =
            new ResourceLocation(VSShieldsMod.MOD_ID, "shield_toggle");
    public static final ResourceLocation NUKE_VISUAL_ID =
            new ResourceLocation(VSShieldsMod.MOD_ID, "nuke_visual");
    public static final ResourceLocation CLOAK_TOGGLE_ID =
            new ResourceLocation(VSShieldsMod.MOD_ID, "cloak_toggle");

    public static void init() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                SHIELD_TOGGLE_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    boolean active = buf.readBoolean();
                    context.queue(() -> handleToggle(context.getPlayer(), shipId, active));
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                CLOAK_TOGGLE_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    boolean active = buf.readBoolean();
                    context.queue(() -> CloakManager.getInstance().toggleCloak(shipId, active));
                }
        );

        VSShieldsNetworking.register();
    }

    public static void sendNukeVisual(MinecraftServer server, double x, double y, double z) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), NUKE_VISUAL_ID, buf);
    }

    public static void sendSyncToPlayer(ServerPlayer player) {
        Map<Long, ShieldInstance> shields = ShieldManager.getInstance().getAllShields();

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(shields.size());
        for (ShieldInstance shield : shields.values()) {
            buf.writeLong(shield.getShipId());
            buf.writeDouble(shield.getCurrentHP());
            buf.writeDouble(shield.getMaxHP());
            buf.writeBoolean(shield.isActive());
        }
        NetworkManager.sendToPlayer(player, SHIELD_SYNC_ID, buf);
    }

    public static void sendSyncToAll(MinecraftServer server) {
        Map<Long, ShieldInstance> shields = ShieldManager.getInstance().getAllShields();

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(shields.size());
        for (ShieldInstance shield : shields.values()) {
            buf.writeLong(shield.getShipId());
            buf.writeDouble(shield.getCurrentHP());
            buf.writeDouble(shield.getMaxHP());
            buf.writeBoolean(shield.isActive());
        }
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), SHIELD_SYNC_ID, buf);
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
