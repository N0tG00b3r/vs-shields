package com.mechanicalskies.vsshields.network.packets;

import com.mechanicalskies.vsshields.client.ClientCloakManager;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;

import static com.mechanicalskies.vsshields.VSShieldsMod.MOD_ID;

public class CloakStatusPacket {
    public static final ResourceLocation ID = new ResourceLocation(MOD_ID, "cloak_status");

    private final Long shipId;
    private final boolean isCloaked;

    public CloakStatusPacket(Long shipId, boolean isCloaked) {
        this.shipId = shipId;
        this.isCloaked = isCloaked;
    }

    public CloakStatusPacket(FriendlyByteBuf buf) {
        this.shipId = buf.readLong();
        this.isCloaked = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(shipId);
        buf.writeBoolean(isCloaked);
    }

    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            EnvExecutor.runInEnv(Env.CLIENT, () -> () -> {
                ClientCloakManager.getInstance().updateCloakingStatus(shipId, isCloaked);
            });
        });
    }

    public Long getShipId() {
        return shipId;
    }

    public boolean isCloaked() {
        return isCloaked;
    }
}
