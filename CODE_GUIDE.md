# Code Guide (Solutions Applied)

This document summarizes the code changes applied to resolve compilation errors.

## 1. `common/src/main/java/com/mechanicalskies/vsshields/blockentity/CloakingFieldGeneratorBlockEntity.java`

```java
package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.shield.CloakManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.core.api.ships.Ship;
// import org.valkyrienskies.core.api.ships.properties.ShipId; // Removed as ShipId is a Kotlin typealias for Long
import org.valkyrienskies.mod.common.VSGameUtilsKt; // Keep this import for other utility functions
// import org.valkyrienskies.mod.common.ValkyrienSkiesMod; // Removed, as getVsWorld was not found

public class CloakingFieldGeneratorBlockEntity extends BlockEntity {

    private boolean isCloakingActive = false;
    private long shipIdLong = -1; // Storing as long internally for NBT

    public CloakingFieldGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLOAKING_FIELD_GENERATOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CloakingFieldGeneratorBlockEntity blockEntity) {
        if (level.isClientSide) return;

        if (blockEntity.shipIdLong == -1) {
            // Try to find the ship
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
            if (ship != null) {
                blockEntity.shipIdLong = ship.getId(); // ship.getId() returns long
                blockEntity.setChanged();
            }
        }

        if (blockEntity.shipIdLong != -1) {
            // Check if cloaking should be active (e.g., based on redstone, GUI setting, etc.)
            boolean shouldBeCloaking = true; // Placeholder for actual logic

            Long shipId = blockEntity.shipIdLong; // ShipId is a Long typealias

            if (shouldBeCloaking && !blockEntity.isCloakingActive) {
                blockEntity.isCloakingActive = true;
                // THIS LINE IS THE REMAINING PROBLEM: how to get Ship from Level and Long shipId
                CloakManager.getInstance().cloakShip(VSGameUtilsKt.getShipById(level, shipId), level.getServer());
                blockEntity.setChanged();
            } else if (!shouldBeCloaking && blockEntity.isCloakingActive) {
                blockEntity.isCloakingActive = false;
                // THIS LINE IS THE REMAINING PROBLEM: how to get Ship from Level and Long shipId
                CloakManager.getInstance().uncloakShip(VSGameUtilsKt.getShipById(level, shipId), level.getServer());
                blockEntity.setChanged();
            }

            // If active, consume FE
            if (blockEntity.isCloakingActive) {
                // TODO: Implement actual FE consumption
            }
        }
    }

    public void onBroken() {
        if (level != null && !level.isClientSide && isCloakingActive && shipIdLong != -1) {
            Long shipId = shipIdLong; // ShipId is a Long typealias
            // THIS LINE IS THE REMAINING PROBLEM: how to get Ship from Level and Long shipId
            CloakManager.getInstance().uncloakShip(VSGameUtilsKt.getShipById(level, shipId), level.getServer());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putBoolean("IsCloakingActive", isCloakingActive);
        nbt.putLong("ShipId", shipIdLong);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        isCloakingActive = nbt.getBoolean("IsCloakingActive");
        shipIdLong = nbt.getLong("ShipId");
    }

    public boolean isCloakingActive() {
        return isCloakingActive;
    }

    public void setCloakingActive(boolean cloakingActive) {
        if (this.isCloakingActive != cloakingActive) {
            this.isCloakingActive = cloakingActive;
            if (level != null && !level.isClientSide && shipIdLong != -1) {
                Long shipId = shipIdLong; // ShipId is a Long typealias
                // THIS LINE IS THE REMAINING PROBLEM: how to get Ship from Level and Long shipId
                Ship ship = VSGameUtilsKt.getShipById(level, shipId);
                if (cloakingActive) {
                    CloakManager.getInstance().cloakShip(ship, level.getServer());
                } else {
                    CloakManager.getInstance().uncloakShip(ship, level.getServer());
                }
            }
            setChanged();
        }
    }
}
```

## 2. `common/src/main/java/com/mechanicalskies/vsshields/client/ClientCloakManager.java`

```java
package com.mechanicalskies.vsshields.client;

// import org.valkyrienskies.core.api.ships.properties.ShipId; // Removed as ShipId is a Kotlin typealias for Long

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the cloaking status of ships on the client side.
 * This class receives updates from the server and maintains a local cache
 * of which ships are currently cloaked.
 */
public class ClientCloakManager {
    private static final ClientCloakManager INSTANCE = new ClientCloakManager();

    // Store Long objects directly
    private final Set<Long> cloakedShips;

    private ClientCloakManager() {
        this.cloakedShips = Collections.synchronizedSet(new HashSet<>());
    }

    public static ClientCloakManager getInstance() {
        return INSTANCE;
    }

    /**
     * Updates the cloaking status of a ship.
     *
     * @param shipId The ID of the ship to update.
     * @param isCloaked The new cloaking status (true for cloaked, false for uncloaked).
     */
    public void updateCloakingStatus(Long shipId, boolean isCloaked) { // Parameter is now Long
        if (isCloaked) {
            cloakedShips.add(shipId);
        } else {
            cloakedShips.remove(shipId);
        }
    }

    /**
     * Checks if a ship is currently cloaked on the client.
     *
     * @param shipId The ID of the ship to check.
     * @return True if the ship is cloaked, false otherwise.
     */
    public boolean isCloaked(Long shipId) { // Parameter is now Long
        return cloakedShips.contains(shipId);
    }

    /**
     * Clears all cloaked ship data from the client.
     * This should be called when disconnecting from a server.
     */
    public void clear() {
        cloakedShips.clear();
    }
}
```

## 3. `common/src/main/java/com/mechanicalskies/vsshields/mixin/LevelRendererMixin.java`

```java
package com.mechanicalskies.vsshields.mixin;

import com.mechanicalskies.vsshields.client.ClientCloakManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
// import org.valkyrienskies.core.api.ships.properties.ShipId; // Removed as ShipId is a Kotlin typealias for Long
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow
    private ClientLevel level;

    /**
     * Injects into `renderShip` (a method added by VS2's mixins) to conditionally
     * cancel rendering for cloaked ships.
     *
     * @param ship The ship being rendered.
     * @param ci   CallbackInfo to cancel the rendering.
     */
    @Inject(
            method = "renderShip",
            at = @At("HEAD"),
            cancellable = true,
            remap = false // This method is from VS2, not Mojang, so no remapping
    )
    private void vs_shields$onRenderShip(Ship ship, PoseStack poseStack, MultiBufferSource multiBufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
        if (ship == null) return;

        // ShipId is a Long typealias
        Long currentShipId = ship.getId();

        if (ClientCloakManager.getInstance().isCloaked(currentShipId)) { // Pass Long directly
            // Don't cloak if the local player is aboard this ship
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Ship playerShip = VSGameUtilsKt.getShipManagingPos(level, mc.player.blockPosition());
                if (playerShip != null && playerShip.getId() == ship.getId()) { // Compare long values directly
                    return; // Player is on the cloaked ship, so render it for them
                }
            }
            // If we are here, the ship is cloaked and the player is not on it.
            ci.cancel(); // Cancel rendering for this ship
        }
    }
}
```

## 4. `common/src/main/java/com/mechanicalskies/vsshields/network/packets/CloakStatusPacket.java`

```java
package com.mechanicalskies.vsshields.network.packets;

import com.mechanicalskies.vsshields.client.ClientCloakManager;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf; // Use Minecraft's FriendlyByteBuf
import net.minecraft.resources.ResourceLocation;
import dev.architectury.networking.NetworkManager.Side; // Import Architectury's Side for environment check
// import org.valkyrienskies.core.api.ships.properties.ShipId; // Removed as ShipId is a Kotlin typealias for Long

import static com.mechanicalskies.vsshields.VSShieldsMod.MOD_ID;

public class CloakStatusPacket {
    public static final ResourceLocation ID = new ResourceLocation(MOD_ID, "cloak_status");

    private final Long shipId; // Changed type to Long
    private final boolean isCloaked;

    public CloakStatusPacket(Long shipId, boolean isCloaked) { // Changed constructor parameter type
        this.shipId = shipId;
        this.isCloaked = isCloaked;
    }

    public CloakStatusPacket(FriendlyByteBuf buf) { // Changed to FriendlyByteBuf
        this.shipId = buf.readLong(); // Read long directly
        this.isCloaked = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) { // Changed to FriendlyByteBuf
        buf.writeLong(shipId); // Write long value directly
        buf.writeBoolean(isCloaked);
    }

    public void handle(NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (context.getSide().isClient()) { // Changed to context.getSide().isClient()
                ClientCloakManager.getInstance().updateCloakingStatus(shipId, isCloaked); // Pass Long directly
            }
        });
    }

    public Long getShipId() { // Changed return type to Long
        return shipId;
    }

    public boolean isCloaked() {
        return isCloaked;
    }
}
```

## 5. `common/src/main/java/com/mechanicalskies/vsshields/network/VSShieldsNetworking.java`

```java
package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.network.packets.CloakStatusPacket;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.FriendlyByteBuf; // Import FriendlyByteBuf for the Consumer cast
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer; // Import Consumer for the explicit cast

import static com.mechanicalskies.vsshields.VSShieldsMod.MOD_ID;

public class VSShieldsNetworking {
    public static void register() {
        // Register CloakStatusPacket
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, CloakStatusPacket.ID, (buf, context) -> {
            CloakStatusPacket packet = new CloakStatusPacket(buf);
            packet.handle(context);
        });
    }

    // Changed to specifically handle CloakStatusPacket and use method reference with explicit cast
    public static void sendToClient(ServerPlayer player, CloakStatusPacket packet) {
        NetworkManager.sendToPlayer(player, CloakStatusPacket.ID, (Consumer<FriendlyByteBuf>) packet::encode);
    }
}
```

## 6. `common/src/main/java/com/mechanicalskies/vsshields/shield/CloakManager.java`

```java
package com.mechanicalskies.vsshields.shield;

import com.mechanicalskies.vsshields.network.VSShieldsNetworking;
import com.mechanicalskies.vsshields.network.packets.CloakStatusPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.valkyrienskies.core.api.ships.Ship;
// Removed as ShipId is a Kotlin typealias for Long

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the cloaking status of ships on the server side.
 * This class is responsible for tracking which ships are cloaked and
 * synchronizing this information to clients.
 */
public class CloakManager {
    private static final CloakManager INSTANCE = new CloakManager();

    // Store Long objects directly
    private final Set<Long> cloakedShips;

    private CloakManager() {
        this.cloakedShips = Collections.synchronizedSet(new HashSet<>());
    }

    public static CloakManager getInstance() {
        return INSTANCE;
    }

    /**
     * Cloaks a ship and sends an update to all players.
     *
     * @param ship The ship to cloak.
     * @param server The MinecraftServer instance.
     */
    public void cloakShip(Ship ship, MinecraftServer server) {
        if (ship == null) return;
        Long shipId = ship.getId(); // ShipId is a Long typealias
        if (cloakedShips.add(shipId)) {
            // Only send update if the ship was not already cloaked
            sendCloakStatusToAllClients(shipId, true, server);
        }
    }

    /**
     * Uncloaks a ship and sends an update to all players.
     *
     * @param ship The ship to uncloak.
     * @param server The MinecraftServer instance.
     */
    public void uncloakShip(Ship ship, MinecraftServer server) {
        if (ship == null) return;
        Long shipId = ship.getId(); // ShipId is a Long typealias
        if (cloakedShips.remove(shipId)) {
            // Only send update if the ship was cloaked
            sendCloakStatusToAllClients(shipId, false, server);
        }
    }

    /**
     * Checks if a ship is currently cloaked.
     *
     * @param shipId The ID of the ship to check.
     * @return True if the ship is cloaked, false otherwise.
     */
    public boolean isShipCloaked(Long shipId) { // Parameter is now Long
        return cloakedShips.contains(shipId);
    }

    /**
     * Sends the cloaking status of a specific ship to all connected clients.
     *
     * @param shipId The ID of the ship whose status is being sent.
     * @param isCloaked The cloaking status (true for cloaked, false for uncloaked).
     * @param server The MinecraftServer instance.
     */
    private void sendCloakStatusToAllClients(Long shipId, boolean isCloaked, MinecraftServer server) { // Parameter is now Long
        CloakStatusPacket packet = new CloakStatusPacket(shipId, isCloaked);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VSShieldsNetworking.sendToClient(player, packet);
        }
    }

    /**
     * Sends the full list of currently cloaked ships to a specific client.
     * This is useful when a player first joins the server or when their client
     * needs to resynchronize.
     *
     * @param player The player to send the cloaked ship list to.
     */
    public void sendAllCloakedShipsToClient(ServerPlayer player) {
        for (Long shipId : cloakedShips) { // Iterate over Long objects
            VSShieldsNetworking.sendToClient(player, new CloakStatusPacket(shipId, true));
        }
    }
}
```
