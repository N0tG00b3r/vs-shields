package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.client.ShieldEffectHandler;
import com.mechanicalskies.vsshields.network.packets.CloakStatusPacket;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public class ClientNetworkHandler {
    public static void registerS2C() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.SHIELD_SYNC_ID,
                (buf, context) -> {
                    int count = buf.readVarInt();
                    long[] shipIds = new long[count];
                    double[] currentHPs = new double[count];
                    double[] maxHPs = new double[count];
                    boolean[] actives = new boolean[count];
                    double[] energyPercents = new double[count];
                    double[] minXs = new double[count], minYs = new double[count], minZs = new double[count];
                    double[] maxXs = new double[count], maxYs = new double[count], maxZs = new double[count];
                    boolean[] hasAABB = new boolean[count];
                    for (int i = 0; i < count; i++) {
                        shipIds[i] = buf.readLong();
                        currentHPs[i] = buf.readDouble();
                        maxHPs[i] = buf.readDouble();
                        actives[i] = buf.readBoolean();
                        energyPercents[i] = buf.readDouble();
                        hasAABB[i] = buf.readBoolean();
                        if (hasAABB[i]) {
                            minXs[i] = buf.readDouble();
                            minYs[i] = buf.readDouble();
                            minZs[i] = buf.readDouble();
                            maxXs[i] = buf.readDouble();
                            maxYs[i] = buf.readDouble();
                            maxZs[i] = buf.readDouble();
                        }
                    }
                    context.queue(() -> {
                        ClientShieldManager csm = ClientShieldManager.getInstance();
                        csm.clear();
                        for (int i = 0; i < count; i++) {
                            csm.updateShield(shipIds[i], currentHPs[i], maxHPs[i], actives[i],
                                    energyPercents[i],
                                    minXs[i], minYs[i], minZs[i],
                                    maxXs[i], maxYs[i], maxZs[i]);
                        }
                    });
                });

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.NUKE_VISUAL_ID,
                (buf, context) -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    context.queue(() -> {
                        try {
                            ResourceLocation nukeId = new ResourceLocation("alexscaves", "nuclear_explosion");
                            Optional<EntityType<?>> opt = BuiltInRegistries.ENTITY_TYPE.getOptional(nukeId);
                            if (opt.isEmpty())
                                return;
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.level == null)
                                return;
                            Entity nukeEntity = opt.get().create(mc.level);
                            if (nukeEntity == null)
                                return;
                            nukeEntity.setPos(x, y, z);
                            mc.level.putNonPlayerEntity(nukeEntity.getId(), nukeEntity);
                        } catch (Exception ignored) {
                        }
                    });
                });

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.SHIELD_HIT_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    float damage = buf.readFloat();
                    context.queue(() -> ShieldEffectHandler.onShieldHit(shipId, x, y, z, damage));
                });

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.SHIELD_BREAK_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    context.queue(() -> ShieldEffectHandler.onShieldBreak(shipId));
                });

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                CloakStatusPacket.ID,
                (buf, context) -> {
                    CloakStatusPacket packet = new CloakStatusPacket(buf);
                    packet.handle(context);
                });
    }
}
