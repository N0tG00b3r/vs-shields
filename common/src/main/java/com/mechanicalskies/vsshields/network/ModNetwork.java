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
    public static final ResourceLocation JAMMER_RELOAD_ID = new ResourceLocation(VSShieldsMod.MOD_ID, "jammer_reload");
    public static final ResourceLocation JAMMER_ENABLE_ID          = new ResourceLocation(VSShieldsMod.MOD_ID, "jammer_enable");
    public static final ResourceLocation GRAVITY_TOGGLE_ID         = new ResourceLocation(VSShieldsMod.MOD_ID, "gravity_toggle");
    public static final ResourceLocation GRAVITY_FLIGHT_TOGGLE_ID  = new ResourceLocation(VSShieldsMod.MOD_ID, "gravity_flight_toggle");
    public static final ResourceLocation GRAVITY_FALL_TOGGLE_ID    = new ResourceLocation(VSShieldsMod.MOD_ID, "gravity_fall_toggle");
    public static final ResourceLocation SHIELD_REGEN_ID           = new ResourceLocation(VSShieldsMod.MOD_ID, "shield_regen");

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
