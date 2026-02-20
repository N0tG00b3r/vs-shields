package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.network.packets.CloakStatusPacket;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import io.netty.buffer.Unpooled;

public class VSShieldsNetworking {
    public static void register() {
        // Register CloakStatusPacket
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CloakStatusPacket.ID, (buf, context) -> {
            CloakStatusPacket packet = new CloakStatusPacket(buf);
            packet.handle(context);
        });
    }

    public static void sendToClient(ServerPlayer player, CloakStatusPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        NetworkManager.sendToPlayer(player, CloakStatusPacket.ID, buf);
    }
}
