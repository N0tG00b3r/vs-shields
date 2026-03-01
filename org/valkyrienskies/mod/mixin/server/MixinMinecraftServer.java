package org.valkyrienskies.mod.mixin.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.Unit;
import net.minecraft.BlockUtil;
import net.minecraft.BlockUtil.FoundRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.properties.IShipActiveChunksSet;
import org.valkyrienskies.core.internal.VsiGameServer;
import org.valkyrienskies.core.internal.ShipTeleportData;
import org.valkyrienskies.core.internal.ships.VsiLoadedServerShip;
import org.valkyrienskies.core.internal.world.VsiPlayer;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.core.internal.world.VsiPipeline;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;
import org.valkyrienskies.mod.common.ShipSavedData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.DimensionParametersResolver;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.ShipSettingsKt;
import org.valkyrienskies.mod.common.util.VSLevelChunk;
import org.valkyrienskies.mod.common.util.VSServerLevel;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.ChunkManagement;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.Weather2Compat;
import org.valkyrienskies.mod.util.KrunchSupport;
import org.valkyrienskies.mod.util.McMathUtilKt;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements IShipObjectWorldServerProvider, VsiGameServer {
    @Shadow
    private PlayerList playerList;

    @Shadow
    public abstract ServerLevel overworld();

    @Shadow
    public abstract Iterable<ServerLevel> getAllLevels();

    @Unique
    private VsiServerShipWorld shipWorld;

    @Unique
    private VsiPipeline vsPipeline;

    @Unique
    private Set<String> loadedLevels = new HashSet<>();

    @Unique
    private final Map<String, ServerLevel> dimensionToLevelMap = new HashMap<>();

    @Inject(
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;initServer()Z"),
        method = "runServer"
    )
    private void beforeInitServer(final CallbackInfo info) {
        ValkyrienSkiesMod.setCurrentServer(MinecraftServer.class.cast(this));
    }

    @Inject(at = @At("TAIL"), method = "stopServer")
    private void afterStopServer(final CallbackInfo ci) {
        ValkyrienSkiesMod.setCurrentServer(null);
    }

    @Nullable
    @Override
    public VsiServerShipWorld getShipObjectWorld() {
        return shipWorld;
    }

    @Nullable
    @Override
    public VsiPipeline getVsPipeline() {
        return vsPipeline;
    }

    /**
     * Create the ship world immediately after the levels are created, so that nothing can try to access the ship world
     * before it has been initialized.
     */
    @Inject(
        method = "createLevels",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getDataStorage()Lnet/minecraft/world/level/storage/DimensionDataStorage;"
        )
    )
    private void postCreateLevels(final CallbackInfo ci) {
        // Register blocks
        if (!MassDatapackResolver.INSTANCE.getRegisteredBlocks()) {
            final List<BlockState> blockStateList = new ArrayList<>(Block.f_49791_.m_13562_());
            Block.f_49791_.forEach((blockStateList::add));
            MassDatapackResolver.INSTANCE.registerAllBlockStates(blockStateList);
            ValkyrienSkiesMod.getVsCore().registerBlockStates(MassDatapackResolver.INSTANCE.getBlockStateData());
        }

        // Load ship data from the world storage
        final ShipSavedData shipSavedData = overworld().m_8895_()
            .m_164861_(ShipSavedData::load, ShipSavedData.Companion::createEmpty, ShipSavedData.SAVED_DATA_ID);

        // If there was an error deserializing, re-throw it here so that the game actually crashes.
        // We would prefer to crash the game here than allow the player keep playing with everything corrupted.
        final Throwable ex = shipSavedData.getLoadingException();
        if (ex != null) {
            System.err.println("VALKYRIEN SKIES ERROR WHILE LOADING SHIP DATA");
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }

        // Create ship world and VS Pipeline
        vsPipeline = shipSavedData.getPipeline();

        KrunchSupport.INSTANCE.setKrunchSupported(!vsPipeline.isUsingDummyPhysics());

        shipWorld = vsPipeline.getShipWorld();
        shipWorld.setGameServer(this);

        VSGameEvents.INSTANCE.getRegistriesCompleted().emit(Unit.INSTANCE);

        DimensionParametersResolver.Parameters params = DimensionParametersResolver.INSTANCE.getDimensionMap().get(VSGameUtilsKt.getDimensionId(overworld()));

        if (params != null) {
            getShipObjectWorld().addDimension(
                VSGameUtilsKt.getDimensionId(overworld()),
                VSGameUtilsKt.getYRange(overworld()),
                params.getGravity(),
                params.getSeaLevel(),
                params.getMaxY()
            );
        } else {
            getShipObjectWorld().addDimension(
                VSGameUtilsKt.getDimensionId(overworld()),
                VSGameUtilsKt.getYRange(overworld()),
                McMathUtilKt.getDEFAULT_WORLD_GRAVITY(),
                63.0,
                962.0
            );
        }
    }

    @Inject(
        method = "tickServer",
        at = @At("HEAD")
    )
    private void preTick(final CallbackInfo ci) {
        final Set<VsiPlayer> vsPlayers = playerList.m_11314_().stream()
            .map(VSGameUtilsKt::getPlayerWrapper).collect(Collectors.toSet());

        shipWorld.setPlayers(vsPlayers);

        // region Tell the VS world to load new levels, and unload deleted ones
        final Map<String, ServerLevel> newLoadedLevels = new HashMap<>();
        for (final ServerLevel level : getAllLevels()) {
            final String dimensionId = VSGameUtilsKt.getDimensionId(level);
            newLoadedLevels.put(dimensionId, level);
            dimensionToLevelMap.put(dimensionId, level);
        }
        /*
        for (final var entry : newLoadedLevels.entrySet()) {
            if (!loadedLevels.contains(entry.getKey())) {
                final var yRange = VSGameUtilsKt.getYRange(entry.getValue());
                shipWorld.addDimension(entry.getKey(), yRange);
            }
        }
        */

        for (final String oldLoadedLevelId : loadedLevels) {
            if (!newLoadedLevels.containsKey(oldLoadedLevelId)) {
                shipWorld.removeDimension(oldLoadedLevelId);
                dimensionToLevelMap.remove(oldLoadedLevelId);
            }
        }
        loadedLevels = newLoadedLevels.keySet();
        // endregion

        vsPipeline.preTickGame();
    }

    /**
     * Tick the [shipWorld], then send voxel terrain updates for each level
     */
    @Inject(
        method = "tickChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerConnectionListener;tick()V",
            shift = Shift.AFTER
        )
    )
    private void preConnectionTick(final CallbackInfo ci) {
        ChunkManagement.tickChunkLoading(shipWorld, MinecraftServer.class.cast(this));
    }

    @Shadow
    public abstract ServerLevel getLevel(ResourceKey<Level> resourceKey);

    @Shadow
    public abstract boolean isNetherEnabled();

    @Inject(
        method = "tickServer",
        at = @At("TAIL")
    )
    private void postTick(final CallbackInfo ci) {
        vsPipeline.postTickGame();
        // Only drag entities after we have updated the ship positions
        for (final ServerLevel level : getAllLevels()) {
            EntityDragger.INSTANCE.dragEntitiesWithShips(level.m_8583_(), false);
            if (LoadedMods.getWeather2())
                Weather2Compat.INSTANCE.tick(level);
        }

        //TODO must reimplement
        // handleShipPortals();
    }

    @Unique
    private void handleShipPortals() {
        // Teleport ships that touch portals
        final ArrayList<LoadedServerShip> loadedShipsCopy = new ArrayList<>(shipWorld.getLoadedShips());
        for (final LoadedServerShip shipObject : loadedShipsCopy) {
            if (!ShipSettingsKt.getSettings(shipObject).getChangeDimensionOnTouchPortals()) {
                // Only send ships through portals if it's enabled in settings
                continue;
            }
            final ServerLevel level = dimensionToLevelMap.get(shipObject.getChunkClaimDimension());
            final Vector3dc shipPos = shipObject.getTransform().getPositionInWorld();
            final double bbRadius = 0.5;
            final BlockPos blockPos = BlockPos.m_274561_(shipPos.x() - bbRadius, shipPos.y() - bbRadius, shipPos.z() - bbRadius);
            final BlockPos blockPos2 = BlockPos.m_274561_(shipPos.x() + bbRadius, shipPos.y() + bbRadius, shipPos.z() + bbRadius);
            // Only run this code if the chunks between blockPos and blockPos2 are loaded
            if (level.m_46832_(blockPos, blockPos2)) {
                ((VsiLoadedServerShip) shipObject).decayPortalCoolDown();

                final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
                for (int i = blockPos.m_123341_(); i <= blockPos2.m_123341_(); ++i) {
                    for (int j = blockPos.m_123342_(); j <= blockPos2.m_123342_(); ++j) {
                        for (int k = blockPos.m_123343_(); k <= blockPos2.m_123343_(); ++k) {
                            mutableBlockPos.m_122178_(i, j, k);
                            final BlockState blockState = level.m_8055_(mutableBlockPos);
                            if (blockState.m_60734_() == Blocks.f_50142_) {
                                // Handle nether portal teleport
                                if (!((VsiLoadedServerShip) shipObject).isOnPortalCoolDown()) {
                                    // Move the ship between dimensions
                                    final ServerLevel destLevel = getLevel(level.m_46472_() == Level.f_46429_ ? Level.f_46428_ : Level.f_46429_);
                                    // TODO: Do we want portal time?
                                    if (destLevel != null && isNetherEnabled()) { // && this.portalTime++ >= i) {
                                        level.m_46473_().m_6180_("portal");
                                        shipChangeDimension(level, destLevel, mutableBlockPos, shipObject);
                                        level.m_46473_().m_7238_();
                                    }
                                }
                                ((VsiLoadedServerShip) shipObject).handleInsidePortal();
                            } else if (blockState.m_60734_() == Blocks.f_50257_) {
                                // Handle end portal teleport
                                final ServerLevel destLevel = level.m_7654_().m_129880_(level.m_46472_() == Level.f_46430_ ? Level.f_46428_ : Level.f_46430_);
                                if (destLevel == null) {
                                    return;
                                }
                                shipChangeDimension(level, destLevel, null, shipObject);
                            }
                        }
                    }
                }
            }
        }
    }

    @Unique
    private void shipChangeDimension(@NotNull final ServerLevel srcLevel, @NotNull final ServerLevel destLevel, @Nullable final BlockPos portalEntrancePos, @NotNull final LoadedServerShip shipObject) {
        final PortalInfo
            portalInfo = findDimensionEntryPoint(srcLevel, destLevel, portalEntrancePos, shipObject.getTransform().getPositionInWorld());
        if (portalInfo == null) {
            // Getting portal info failed? Don't teleport.
            return;
        }
        final ShipTeleportData shipTeleportData = ValkyrienSkiesMod.getVsCore().newShipTeleportData(
            VectorConversionsMCKt.toJOML(portalInfo.f_77676_),
            shipObject.getTransform().getShipToWorldRotation(),
            new Vector3d(),
            new Vector3d(),
            VSGameUtilsKt.getDimensionId(destLevel),
            null,
            null
        );
        shipWorld.teleportShip(shipObject, shipTeleportData);
    }

    @Unique
    @Nullable
    private PortalInfo findDimensionEntryPoint(@NotNull final ServerLevel srcLevel, @NotNull final ServerLevel destLevel, @Nullable final BlockPos portalEntrancePos, @NotNull final Vector3dc shipPos) {
        final boolean bl = srcLevel.m_46472_() == Level.f_46430_ && destLevel.m_46472_() == Level.f_46428_;
        final boolean bl2 = destLevel.m_46472_() == Level.f_46430_;
        final Vec3 deltaMovement = Vec3.f_82478_;
        final EntityDimensions entityDimensions = new EntityDimensions(1.0f, 1.0f, true);
        if (bl || bl2) {
            final BlockPos blockPos = bl2 ? ServerLevel.f_8562_ : destLevel.m_5452_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, destLevel.m_220360_());
            return new PortalInfo(new Vec3((double)blockPos.m_123341_() + 0.5, blockPos.m_123342_(), (double)blockPos.m_123343_() + 0.5), deltaMovement, 0f, 0f);
        }
        final boolean bl3 = destLevel.m_46472_() == Level.f_46429_;
        if (srcLevel.m_46472_() != Level.f_46429_ && !bl3) {
            return null;
        }
        final WorldBorder worldBorder = destLevel.m_6857_();
        final double d = DimensionType.m_63908_(srcLevel.m_6042_(), destLevel.m_6042_());
        final BlockPos blockPos2 = worldBorder.m_187569_(shipPos.x() * d, shipPos.y(), shipPos.z() * d);
        return this.getExitPortal(destLevel, blockPos2, bl3, worldBorder).map(foundRectangle -> {
            final Vec3 vec3;
            final Direction.Axis axis;
            if (portalEntrancePos != null) {
                final BlockState blockState = srcLevel.m_8055_(portalEntrancePos);
                if (blockState.m_61138_(BlockStateProperties.f_61364_)) {
                    axis = blockState.m_61143_(BlockStateProperties.f_61364_);
                    final BlockUtil.FoundRectangle foundRectangle2 =
                        BlockUtil.m_124334_(portalEntrancePos, axis, 21, Direction.Axis.Y, 21,
                            blockPos -> srcLevel.m_8055_(blockPos) == blockState);
                    vec3 = this.getRelativePortalPosition(axis, foundRectangle2, entityDimensions,
                        VectorConversionsMCKt.toMinecraft(shipPos));
                } else {
                    axis = Direction.Axis.X;
                    vec3 = new Vec3(0.5, 0.0, 0.0);
                }
            } else {
                axis = Direction.Axis.X;
                vec3 = new Vec3(0.5, 0.0, 0.0);
            }
            return valkyrienskies$createPortalInfo(destLevel, foundRectangle, axis, vec3, entityDimensions, deltaMovement, 0.0f, 0.0f);
        }).orElse(null);
    }

    @Unique
    private static PortalInfo valkyrienskies$createPortalInfo(ServerLevel serverLevel, BlockUtil.FoundRectangle foundRectangle,
        Direction.Axis axis, Vec3 vec3, EntityDimensions entityDimensions, Vec3 vec32, float f, float g) {
        BlockPos blockPos = foundRectangle.f_124348_;
        BlockState blockState = serverLevel.m_8055_(blockPos);
        Direction.Axis axis2 = (Direction.Axis)blockState.m_61145_(BlockStateProperties.f_61364_).orElse(
            Axis.X);
        double d = (double)foundRectangle.f_124349_;
        double e = (double)foundRectangle.f_124350_;
        int i = axis == axis2 ? 0 : 90;
        Vec3 vec33 = axis == axis2 ? vec32 : new Vec3(vec32.f_82481_, vec32.f_82480_, -vec32.f_82479_);
        double h = (double)entityDimensions.f_20377_ / (double)2.0F + (d - (double)entityDimensions.f_20377_) * vec3.m_7096_();
        double j = (e - (double)entityDimensions.f_20378_) * vec3.m_7098_();
        double k = (double)0.5F + vec3.m_7094_();
        boolean bl = axis2 == Axis.X;
        Vec3 vec34 = new Vec3((double)blockPos.m_123341_() + (bl ? h : k), (double)blockPos.m_123342_() + j, (double)blockPos.m_123343_() + (bl ? k : h));
        Vec3 vec35 = valkyrienskies$findCollisionFreePosition(vec34, serverLevel, entityDimensions);
        return new PortalInfo(vec35, vec33, f + (float)i, g);
    }

    @Unique
    private static Vec3 valkyrienskies$findCollisionFreePosition(Vec3 vec3, ServerLevel serverLevel, EntityDimensions entityDimensions) {
        if (!(entityDimensions.f_20377_ > 4.0F) && !(entityDimensions.f_20378_ > 4.0F)) {
            double d = (double)entityDimensions.f_20378_ / (double)2.0F;
            Vec3 vec32 = vec3.m_82520_((double)0.0F, d, (double)0.0F);
            VoxelShape voxelShape = Shapes.m_83064_(
                AABB.m_165882_(vec32, (double)entityDimensions.f_20377_, (double)0.0F, (double)entityDimensions.f_20377_).m_82363_((double)0.0F, (double)1.0F, (double)0.0F).m_82400_(1.0E-6));
            Optional<Vec3> optional = serverLevel.m_151418_(null, voxelShape, vec32, (double)entityDimensions.f_20377_, (double)entityDimensions.f_20378_, (double)entityDimensions.f_20377_);
            Optional<Vec3> optional2 = optional.map((vec3x) -> vec3x.m_82492_((double)0.0F, d, (double)0.0F));
            return (Vec3)optional2.orElse(vec3);
        } else {
            return vec3;
        }
    }

    @Unique
    private Vec3 getRelativePortalPosition(final Direction.Axis axis, final BlockUtil.FoundRectangle foundRectangle, final EntityDimensions entityDimensions, final Vec3 position) {
        return PortalShape.m_77738_(foundRectangle, axis, position, entityDimensions);
    }

    @Unique
    private Optional<FoundRectangle> getExitPortal(final ServerLevel serverLevel, final BlockPos blockPos, final boolean bl, final WorldBorder worldBorder) {
        return serverLevel.m_8871_().m_192985_(blockPos, bl, worldBorder);
    }

    @Inject(
        method = "stopServer",
        at = @At("HEAD")
    )
    private void preStopServer(final CallbackInfo ci) {
        if (vsPipeline != null) {
            vsPipeline.setDeleteResources(true);
            vsPipeline.setArePhysicsRunning(true);
        }
    }

    // Only clear these after stopping the server so we can use them when saving
    @Inject(
        method = "stopServer",
        at = @At("RETURN")
    )
    private void postStopServer(final CallbackInfo ci) {
        dimensionToLevelMap.clear();
        shipWorld.setGameServer(null);
        shipWorld = null;
    }

    @NotNull
    private ServerLevel getLevelFromDimensionId(@NotNull final String dimensionId) {
        return dimensionToLevelMap.get(dimensionId);
    }

    @Override
    public void moveTerrainAcrossDimensions(
        @NotNull final IShipActiveChunksSet shipChunks,
        @NotNull final String srcDimension,
        @NotNull final String destDimension
    ) {
        final ServerLevel srcLevel = getLevelFromDimensionId(srcDimension);
        final ServerLevel destLevel = getLevelFromDimensionId(destDimension);

        // Copy ship chunks from srcLevel to destLevel
        shipChunks.forEach((final int x, final int z) -> {
            final LevelChunk srcChunk = srcLevel.m_6325_(x, z);

            // This is a hack, but it fixes destLevel being in the wrong state
            ((VSServerLevel) destLevel).removeChunk(x, z);

            final LevelChunk destChunk = destLevel.m_6325_(x, z);
            ((VSLevelChunk) destChunk).copyChunkFromOtherDimension((VSLevelChunk) srcChunk);
        });

        // Delete ship chunks from srcLevel
        shipChunks.forEach((final int x, final int z) -> {
            final LevelChunk srcChunk = srcLevel.m_6325_(x, z);
            ((VSLevelChunk) srcChunk).clearChunk();

            final ChunkPos chunkPos = srcChunk.m_7697_();
            srcLevel.m_7726_().m_6692_(chunkPos, false);
            ((VSServerLevel) srcLevel).removeChunk(x, z);
        });
    }
}
