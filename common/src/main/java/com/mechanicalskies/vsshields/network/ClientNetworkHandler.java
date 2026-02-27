package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.client.ClientAnalyzerData;
import com.mechanicalskies.vsshields.client.ShieldEffectHandler;
import com.mechanicalskies.vsshields.network.packets.CloakStatusPacket;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNetworkHandler {

    /**
     * Persistent active-state tracking that survives csm.clear().
     * csm does NOT store inactive shields (active=false, existing=null → null returned),
     * so we can't read previous state from it — this map fills that gap.
     * Populated after every sync, pruned to only ships present in the current sync.
     */
    private static final Map<Long, Boolean> lastKnownActiveState = new ConcurrentHashMap<>();

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
                    boolean[] solidModes = new boolean[count];
                    double[] minXs = new double[count], minYs = new double[count], minZs = new double[count];
                    double[] maxXs = new double[count], maxYs = new double[count], maxZs = new double[count];
                    boolean[] hasAABB = new boolean[count];
                    for (int i = 0; i < count; i++) {
                        shipIds[i] = buf.readLong();
                        currentHPs[i] = buf.readDouble();
                        maxHPs[i] = buf.readDouble();
                        actives[i] = buf.readBoolean();
                        energyPercents[i] = buf.readDouble();
                        solidModes[i] = buf.readBoolean();
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

                        // Detect activate/deactivate transitions using persistent state.
                        // We check BEFORE csm.clear() so the timing is right, but we read
                        // from lastKnownActiveState (not csm) because csm drops inactive entries.
                        for (int i = 0; i < count; i++) {
                            Boolean prev = lastKnownActiveState.get(shipIds[i]);
                            if (prev != null && hasAABB[i]) {
                                double cx = (minXs[i] + maxXs[i]) / 2.0;
                                double cy = (minYs[i] + maxYs[i]) / 2.0;
                                double cz = (minZs[i] + maxZs[i]) / 2.0;
                                if (!prev && actives[i]) {
                                    ShieldEffectHandler.onShieldActivate(cx, cy, cz);
                                } else if (prev && !actives[i]) {
                                    ShieldEffectHandler.onShieldDeactivate(cx, cy, cz);
                                }
                            }
                        }

                        csm.clear();
                        for (int i = 0; i < count; i++) {
                            csm.updateShield(shipIds[i], currentHPs[i], maxHPs[i], actives[i],
                                    energyPercents[i], solidModes[i],
                                    minXs[i], minYs[i], minZs[i],
                                    maxXs[i], maxYs[i], maxZs[i]);
                        }

                        // Update persistent state: only keep ships present in this sync
                        Set<Long> syncedIds = new HashSet<>();
                        for (int i = 0; i < count; i++) syncedIds.add(shipIds[i]);
                        lastKnownActiveState.keySet().retainAll(syncedIds);
                        for (int i = 0; i < count; i++) lastKnownActiveState.put(shipIds[i], actives[i]);
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

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.SHIELD_REGEN_ID,
                (buf, context) -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    context.queue(() -> ShieldEffectHandler.onShieldRegen(x, y, z));
                });

        // S2C: Ship Analyzer data
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.ANALYZER_DATA_ID,
                (buf, context) -> {
                    long shipId  = buf.readLong();
                    double hp    = buf.readDouble();
                    double maxHp = buf.readDouble();
                    boolean active = buf.readBoolean();
                    boolean solid  = buf.readBoolean();
                    float energy   = buf.readFloat();
                    // Matrix (16 doubles, column-major)
                    double[] matrix = new double[16];
                    for (int i = 0; i < 16; i++) matrix[i] = buf.readDouble();
                    // Cannon positions
                    int nCannons = buf.readInt();
                    List<BlockPos> cannons = new ArrayList<>();
                    for (int i = 0; i < nCannons; i++)
                        cannons.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
                    // Critical positions
                    int nCrit = buf.readInt();
                    List<BlockPos> critical = new ArrayList<>();
                    for (int i = 0; i < nCrit; i++)
                        critical.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
                    // Crew IDs
                    int nCrew = buf.readInt();
                    List<Integer> crewIds = new ArrayList<>();
                    for (int i = 0; i < nCrew; i++) crewIds.add(buf.readInt());
                    // Mine world positions
                    int nMines = buf.readInt();
                    List<double[]> mines = new ArrayList<>();
                    for (int i = 0; i < nMines; i++)
                        mines.add(new double[]{buf.readDouble(), buf.readDouble(), buf.readDouble()});

                    context.queue(() -> ClientAnalyzerData.getInstance()
                            .update(shipId, hp, maxHp, active, solid, energy, matrix, cannons, critical, crewIds, mines));
                });
    }
}
