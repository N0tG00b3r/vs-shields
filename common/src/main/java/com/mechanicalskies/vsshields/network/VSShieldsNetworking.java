package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.network.packets.CloakStatusPacket;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import io.netty.buffer.Unpooled;

public class VSShieldsNetworking {
    public static void register() {
        // S2C receivers MUST be registered in client init only!
        // Moved to VSShieldsModClient.registerPackets()
    }

    public static void sendToClient(ServerPlayer player, CloakStatusPacket packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.encode(buf);
        NetworkManager.sendToPlayer(player, CloakStatusPacket.ID, buf);
    }
}
