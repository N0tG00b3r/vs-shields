package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.blockentity.SolidProjectionModuleBlockEntity;
import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import com.mechanicalskies.vsshields.item.FrequencyIDCardItem;
import com.mechanicalskies.vsshields.item.GravitationalMineLauncherItem;
import com.mechanicalskies.vsshields.scanner.AnalyzerScanHandler;
import com.mechanicalskies.vsshields.shield.CloakManager;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public static final ResourceLocation JAMMER_RELOAD_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "jammer_reload");
    public static final ResourceLocation JAMMER_ENABLE_ID          = new ResourceLocation(VSShieldsMod.MOD_ID, "jammer_enable");
    public static final ResourceLocation GRAVITY_TOGGLE_ID         = new ResourceLocation(VSShieldsMod.MOD_ID, "gravity_toggle");
    public static final ResourceLocation GRAVITY_FLIGHT_TOGGLE_ID  = new ResourceLocation(VSShieldsMod.MOD_ID, "gravity_flight_toggle");
    public static final ResourceLocation GRAVITY_FALL_TOGGLE_ID    = new ResourceLocation(VSShieldsMod.MOD_ID, "gravity_fall_toggle");
    public static final ResourceLocation SHIELD_REGEN_ID           = new ResourceLocation(VSShieldsMod.MOD_ID, "shield_regen");
    public static final ResourceLocation ANALYZER_SCAN_ID          = new ResourceLocation(VSShieldsMod.MOD_ID, "analyzer_scan");
    public static final ResourceLocation ANALYZER_DATA_ID          = new ResourceLocation(VSShieldsMod.MOD_ID, "analyzer_data");
    public static final ResourceLocation MINE_SCROLL_ID            = new ResourceLocation(VSShieldsMod.MOD_ID, "mine_scroll");
    public static final ResourceLocation SOLID_TOGGLE_ID           = new ResourceLocation(VSShieldsMod.MOD_ID, "solid_toggle");
    public static final ResourceLocation SOLID_CODE_SET_ID         = new ResourceLocation(VSShieldsMod.MOD_ID, "solid_code_set");
    public static final ResourceLocation CARD_PROGRAM_ID           = new ResourceLocation(VSShieldsMod.MOD_ID, "card_program");
    public static final ResourceLocation BOARDING_POD_FIRE_ID      = new ResourceLocation(VSShieldsMod.MOD_ID, "boarding_pod_fire");
    public static final ResourceLocation BOARDING_POD_RCS_ID       = new ResourceLocation(VSShieldsMod.MOD_ID, "boarding_pod_rcs");
    public static final ResourceLocation ANOMALY_SPAWN_ID          = new ResourceLocation(VSShieldsMod.MOD_ID, "anomaly_spawn");
    public static final ResourceLocation ANOMALY_DESPAWN_ID        = new ResourceLocation(VSShieldsMod.MOD_ID, "anomaly_despawn");
    public static final ResourceLocation EXTRACTION_PROGRESS_ID    = new ResourceLocation(VSShieldsMod.MOD_ID, "extraction_progress");
    public static final ResourceLocation ANOMALY_PULSE_ID          = new ResourceLocation(VSShieldsMod.MOD_ID, "anomaly_pulse");
    public static final ResourceLocation BEACON_SCAN_START_ID      = new ResourceLocation(VSShieldsMod.MOD_ID, "beacon_scan_start");
    public static final ResourceLocation BEACON_SCAN_RESULT_ID     = new ResourceLocation(VSShieldsMod.MOD_ID, "beacon_scan_result");
    public static final ResourceLocation ANOMALY_TIMER_ID          = new ResourceLocation(VSShieldsMod.MOD_ID, "anomaly_timer");

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

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                JAMMER_RELOAD_ID,
                (buf, context) -> {
                    BlockPos pos = buf.readBlockPos();
                    context.queue(() -> handleJammerReload(context.getPlayer(), pos));
                });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, GRAVITY_TOGGLE_ID,
                (buf, context) -> { BlockPos pos = buf.readBlockPos(); boolean v = buf.readBoolean();
                    context.queue(() -> handleGravityToggle(context.getPlayer(), pos, v)); });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, GRAVITY_FLIGHT_TOGGLE_ID,
                (buf, context) -> { BlockPos pos = buf.readBlockPos(); boolean v = buf.readBoolean();
                    context.queue(() -> handleGravityFlight(context.getPlayer(), pos, v)); });

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, GRAVITY_FALL_TOGGLE_ID,
                (buf, context) -> { BlockPos pos = buf.readBlockPos(); boolean v = buf.readBoolean();
                    context.queue(() -> handleGravityFall(context.getPlayer(), pos, v)); });

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                JAMMER_ENABLE_ID,
                (buf, context) -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean enable = buf.readBoolean();
                    context.queue(() -> handleJammerEnable(context.getPlayer(), pos, enable));
                });

        // C2S: Gravitational Mine Launcher scroll (Shift+Scroll to cycle deploy range)
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, MINE_SCROLL_ID,
                (buf, context) -> {
                    int delta = buf.readInt();
                    context.queue(() -> {
                        ServerPlayer p = (ServerPlayer) context.getPlayer();
                        ItemStack held = findLauncherInHands(p);
                        if (held == null) return;
                        GravitationalMineLauncherItem.cycleRange(held, delta);
                        p.displayClientMessage(
                                Component.translatable("item.vs_shields.gravitational_mine_launcher.range",
                                        GravitationalMineLauncherItem.getRange(held)), true);
                    });
                });

        // C2S: Solid Projection Module toggle
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SOLID_TOGGLE_ID,
                (buf, context) -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean active = buf.readBoolean();
                    context.queue(() -> handleSolidToggle(context.getPlayer(), pos, active));
                });

        // C2S: Solid Projection Module set access code
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, SOLID_CODE_SET_ID,
                (buf, context) -> {
                    BlockPos pos = buf.readBlockPos();
                    String code = buf.readUtf(8);
                    context.queue(() -> handleSolidCodeSet(context.getPlayer(), pos, code));
                });

        // C2S: Program a FrequencyIDCard in player's hand
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, CARD_PROGRAM_ID,
                (buf, context) -> {
                    boolean mainHand = buf.readBoolean();
                    String code = buf.readUtf(8);
                    context.queue(() -> handleCardProgram(context.getPlayer(), mainHand, code));
                });

        // C2S: Ship Analyzer scan request
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ANALYZER_SCAN_ID,
                (buf, context) -> {
                    double ex = buf.readDouble(), ey = buf.readDouble(), ez = buf.readDouble();
                    double lx = buf.readDouble(), ly = buf.readDouble(), lz = buf.readDouble();
                    context.queue(() -> AnalyzerScanHandler.handle(
                            (ServerPlayer) context.getPlayer(),
                            new Vec3(ex, ey, ez), new Vec3(lx, ly, lz)));
                });

        // C2S: Boarding Pod fire — client sends player's current yaw/pitch
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, BOARDING_POD_FIRE_ID,
                (buf, context) -> {
                    int entityId = buf.readInt();
                    float yaw    = buf.readFloat();
                    float pitch  = buf.readFloat();
                    context.queue(() -> {
                        Player player = context.getPlayer();
                        if (player == null) return;
                        if (player.getVehicle() instanceof CockpitSeatEntity seat
                                && seat.getId() == entityId) {
                            seat.onFire(yaw, pitch);
                        }
                    });
                });

        // C2S: Steering + boost state — client sends yaw/pitch each tick while riding pod in flight
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, BOARDING_POD_RCS_ID,
                (buf, context) -> {
                    int   entityId    = buf.readInt();
                    float yaw         = buf.readFloat(); // player look yaw (degrees)
                    float pitch       = buf.readFloat(); // player look pitch (degrees)
                    int   boostActive = buf.readInt();   // 1 = Space held, 0 = released
                    context.queue(() -> {
                        Player player = context.getPlayer();
                        if (player == null) return;
                        if (player.getVehicle() instanceof CockpitSeatEntity seat
                                && seat.getId() == entityId) {
                            seat.onRcs(yaw, pitch, boostActive);
                        }
                    });
                });

        // C2S: Beacon scan start
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, BEACON_SCAN_START_ID,
                (buf, context) -> {
                    BlockPos pos = buf.readBlockPos();
                    context.queue(() -> {
                        Player player = context.getPlayer();
                        if (player instanceof ServerPlayer sp && player.level() instanceof ServerLevel sl) {
                            if (sl.getBlockEntity(pos) instanceof com.mechanicalskies.vsshields.blockentity.ResonanceBeaconBlockEntity beacon) {
                                beacon.startScan(sp);
                            }
                        }
                    });
                });

        // Register S2C Receivers (Client Only)
        EnvExecutor.runInEnv(Env.CLIENT, () -> () -> {
            ClientNetworkHandler.registerS2C();
        });
    }

    public static void sendShieldRegen(MinecraftServer server, double x, double y, double z) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), SHIELD_REGEN_ID, buf);
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

    /** C2S helper: send analyzer scan request from client to server. */
    public static void sendAnalyzerScan(Vec3 eyePos, Vec3 look) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeDouble(eyePos.x); buf.writeDouble(eyePos.y); buf.writeDouble(eyePos.z);
        buf.writeDouble(look.x);   buf.writeDouble(look.y);   buf.writeDouble(look.z);
        NetworkManager.sendToServer(ANALYZER_SCAN_ID, buf);
    }

    /**
     * S2C: send analyzer scan results to a specific player.
     *
     * @param matrix  column-major double[16] of the ship's shipToWorld transform
     * @param cannons  cannon block positions in shipyard space (capped to 64)
     * @param critical critical block positions in shipyard space (capped to 4)
     * @param crewIds  entity IDs of crew entities in world space
     */
    public static void sendAnalyzerData(ServerPlayer player, long shipId,
                                        double hp, double maxHp,
                                        boolean active, boolean solid, float energy,
                                        double[] matrix,
                                        Set<BlockPos> cannons, Set<BlockPos> critical,
                                        List<Integer> crewIds,
                                        List<double[]> minePositions,
                                        boolean isAnomaly, int anomalyTTLSeconds) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(shipId);
        buf.writeDouble(hp);
        buf.writeDouble(maxHp);
        buf.writeBoolean(active);
        buf.writeBoolean(solid);
        buf.writeFloat(energy);
        // Matrix: 16 doubles, column-major
        for (double v : matrix) buf.writeDouble(v);
        // Cannon positions (max 128)
        List<BlockPos> cannonList = new ArrayList<>(cannons);
        if (cannonList.size() > 128) cannonList = cannonList.subList(0, 128);
        buf.writeInt(cannonList.size());
        for (BlockPos pos : cannonList) {
            buf.writeInt(pos.getX()); buf.writeInt(pos.getY()); buf.writeInt(pos.getZ());
        }
        // Critical positions (max 4)
        List<BlockPos> critList = new ArrayList<>(critical);
        if (critList.size() > 4) critList = critList.subList(0, 4);
        buf.writeInt(critList.size());
        for (BlockPos pos : critList) {
            buf.writeInt(pos.getX()); buf.writeInt(pos.getY()); buf.writeInt(pos.getZ());
        }
        // Crew entity IDs
        buf.writeInt(crewIds.size());
        for (int id : crewIds) buf.writeInt(id);
        // ARMED mine world positions (max 32)
        List<double[]> mineList = minePositions != null ? minePositions : java.util.Collections.emptyList();
        if (mineList.size() > 32) mineList = mineList.subList(0, 32);
        buf.writeInt(mineList.size());
        for (double[] m : mineList) {
            buf.writeDouble(m[0]); buf.writeDouble(m[1]); buf.writeDouble(m[2]);
        }
        // Anomaly detection
        buf.writeBoolean(isAnomaly);
        buf.writeInt(anomalyTTLSeconds);
        NetworkManager.sendToPlayer(player, ANALYZER_DATA_ID, buf);
    }

    /** C2S helper: send mine launcher scroll delta from client to server. */
    public static void sendMineScroll(int delta) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(delta);
        NetworkManager.sendToServer(MINE_SCROLL_ID, buf);
    }

    private static ItemStack findLauncherInHands(Player player) {
        if (player.getMainHandItem().getItem() instanceof GravitationalMineLauncherItem)
            return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof GravitationalMineLauncherItem)
            return player.getOffhandItem();
        return null;
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
            buf.writeBoolean(shield.isSolidMode());

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

    public static void sendJammerReloadToServer(BlockPos pos) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        NetworkManager.sendToServer(JAMMER_RELOAD_ID, buf);
    }

    public static void sendJammerEnableToServer(BlockPos pos, boolean enable) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeBoolean(enable);
        NetworkManager.sendToServer(JAMMER_ENABLE_ID, buf);
    }

    private static void handleJammerReload(Player player, BlockPos pos) {
        if (player.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(
                    pos) instanceof com.mechanicalskies.vsshields.blockentity.ShieldJammerControllerBlockEntity be) {
                be.forceCooldown();
            }
        }
    }

    // --- Gravity Field ---

    public static void sendGravityToggleToServer(BlockPos pos, boolean active) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos); buf.writeBoolean(active);
        NetworkManager.sendToServer(GRAVITY_TOGGLE_ID, buf);
    }

    public static void sendGravityFlightToggleToServer(BlockPos pos, boolean enabled) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos); buf.writeBoolean(enabled);
        NetworkManager.sendToServer(GRAVITY_FLIGHT_TOGGLE_ID, buf);
    }

    public static void sendGravityFallToggleToServer(BlockPos pos, boolean enabled) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos); buf.writeBoolean(enabled);
        NetworkManager.sendToServer(GRAVITY_FALL_TOGGLE_ID, buf);
    }

    private static void handleGravityToggle(Player player, BlockPos pos, boolean active) {
        if (player.level() instanceof ServerLevel level)
            if (level.getBlockEntity(pos) instanceof com.mechanicalskies.vsshields.blockentity.GravityFieldGeneratorBlockEntity be)
                be.setActive(active);
    }

    private static void handleGravityFlight(Player player, BlockPos pos, boolean enabled) {
        if (player.level() instanceof ServerLevel level)
            if (level.getBlockEntity(pos) instanceof com.mechanicalskies.vsshields.blockentity.GravityFieldGeneratorBlockEntity be)
                be.setFlightEnabled(enabled);
    }

    private static void handleGravityFall(Player player, BlockPos pos, boolean enabled) {
        if (player.level() instanceof ServerLevel level)
            if (level.getBlockEntity(pos) instanceof com.mechanicalskies.vsshields.blockentity.GravityFieldGeneratorBlockEntity be)
                be.setFallProtectionEnabled(enabled);
    }

    // --- Solid Projection Module ---

    public static void sendSolidToggleToServer(BlockPos pos, boolean active) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeBoolean(active);
        NetworkManager.sendToServer(SOLID_TOGGLE_ID, buf);
    }

    public static void sendSolidCodeSetToServer(BlockPos pos, String code) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeUtf(code, 8);
        NetworkManager.sendToServer(SOLID_CODE_SET_ID, buf);
    }

    /** C2S: fire the boarding pod the player is currently riding. */
    public static void sendBoardingPodFire(int entityId, float yaw, float pitch) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        NetworkManager.sendToServer(BOARDING_POD_FIRE_ID, buf);
    }

    /** C2S: Steering + boost state while riding a boarding pod in flight.
     *  yaw/pitch: player look angles in degrees. boostActive: 1=Space held, 0=released. */
    public static void sendBoardingPodRcs(int entityId, float yaw, float pitch, int boostActive) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeInt(boostActive);
        NetworkManager.sendToServer(BOARDING_POD_RCS_ID, buf);
    }

    public static void sendCardProgramToServer(net.minecraft.world.InteractionHand hand, String code) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(hand == net.minecraft.world.InteractionHand.MAIN_HAND);
        buf.writeUtf(code, 8);
        NetworkManager.sendToServer(CARD_PROGRAM_ID, buf);
    }

    private static void handleSolidToggle(Player player, BlockPos pos, boolean active) {
        if (player.level() instanceof ServerLevel level)
            if (level.getBlockEntity(pos) instanceof SolidProjectionModuleBlockEntity be)
                be.setActive(active);
    }

    private static void handleSolidCodeSet(Player player, BlockPos pos, String code) {
        if (player.level() instanceof ServerLevel level)
            if (level.getBlockEntity(pos) instanceof SolidProjectionModuleBlockEntity be)
                be.setAccessCode(code);
    }

    private static void handleCardProgram(Player player, boolean mainHand, String code) {
        net.minecraft.world.InteractionHand hand = mainHand
                ? net.minecraft.world.InteractionHand.MAIN_HAND
                : net.minecraft.world.InteractionHand.OFF_HAND;
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof FrequencyIDCardItem) {
            FrequencyIDCardItem.setCode(stack, code);
        }
    }

    // --- Aetheric Anomaly ---

    /** S2C: notify all clients that an anomaly spawned. */
    public static void sendAnomalySpawn(MinecraftServer server, long shipId, double x, double y, double z) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(shipId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), ANOMALY_SPAWN_ID, buf);
    }

    /** S2C: notify all clients that the anomaly was removed. */
    public static void sendAnomalyDespawn(MinecraftServer server) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), ANOMALY_DESPAWN_ID, buf);
    }

    /** S2C: send extraction progress to a specific player. */
    public static void sendExtractionProgress(ServerPlayer player, float progress, boolean active) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeFloat(progress);
        buf.writeBoolean(active);
        NetworkManager.sendToPlayer(player, EXTRACTION_PROGRESS_ID, buf);
    }

    /** S2C: notify all clients of an aetheric pulse (screen shake / particles). */
    public static void sendAnomalyPulse(MinecraftServer server, double x, double y, double z, double radius) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(radius);
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), ANOMALY_PULSE_ID, buf);
    }

    /** S2C: send beacon scan result to the player who started the scan. */
    public static void sendBeaconScanResult(ServerPlayer player, boolean found,
                                            double x, double y, double z, int ttlSeconds) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(found);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(ttlSeconds);
        NetworkManager.sendToPlayer(player, BEACON_SCAN_RESULT_ID, buf);
    }

    /** C2S: player clicks SCAN button in beacon GUI. */
    public static void sendBeaconScanStart(BlockPos pos) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        NetworkManager.sendToServer(BEACON_SCAN_START_ID, buf);
    }

    /** S2C: send anomaly timer + phase to players near the island. */
    public static void sendAnomalyTimer(ServerPlayer player, int remainingSeconds, boolean active, int phaseOrdinal) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(remainingSeconds);
        buf.writeBoolean(active);
        buf.writeInt(phaseOrdinal);
        NetworkManager.sendToPlayer(player, ANOMALY_TIMER_ID, buf);
    }

    private static void handleJammerEnable(Player player, BlockPos pos, boolean enable) {
        if (player.level() instanceof ServerLevel serverLevel) {
            if (serverLevel.getBlockEntity(
                    pos) instanceof com.mechanicalskies.vsshields.blockentity.ShieldJammerControllerBlockEntity be) {
                if (enable) {
                    be.enable();
                } else {
                    be.disable();
                }
            }
        }
    }
}
