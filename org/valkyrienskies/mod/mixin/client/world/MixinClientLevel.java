package org.valkyrienskies.mod.mixin.client.world;

import static org.valkyrienskies.mod.common.ValkyrienSkiesMod.getVsCore;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3ic;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBi;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.util.AerodynamicUtils;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.core.util.AABBdUtilKt;
import org.valkyrienskies.core.util.VectorConversionsKt;
import org.valkyrienskies.mod.client.audio.SimpleSoundInstanceOnShip;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.DimensionParametersResolver;
import org.valkyrienskies.mod.util.McMathUtilKt;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel implements IShipObjectWorldClientProvider {
    @Unique
    private final RandomSource vsRandom = RandomSource.m_216327_();

    @Shadow
    @Final
    private Minecraft minecraft;

    @NotNull
    @Override
    public VsiClientShipWorld getShipObjectWorld() {
        return ((IShipObjectWorldClientProvider) minecraft).getShipObjectWorld();
    }

    @Shadow
    private void trySpawnDripParticles(final BlockPos blockPos, final BlockState blockState,
        final ParticleOptions particleData, final boolean shapeDownSolid) {
    }

    @Shadow
    @Final
    private ClientChunkCache chunkSource;
    // Map from ChunkPos to the list of voxel chunks that chunk owns
    @Unique
    private final Map<ChunkPos, List<Vector3ic>> vs$knownChunks = new HashMap<>();

    // Maps chunk pos to number of ticks we have considered unloading the chunk
    @Unique
    private final Long2LongOpenHashMap vs$chunksToUnload = new Long2LongOpenHashMap();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void postInit(ClientPacketListener clientPacketListener, ClientLevelData clientLevelData,
        ResourceKey resourceKey, Holder holder, int i, int j, Supplier supplier, LevelRenderer levelRenderer,
        boolean bl, long l, CallbackInfo ci) {
        if (getShipObjectWorld() != null) {
            DimensionParametersResolver.Parameters params = DimensionParametersResolver.INSTANCE.getDimensionMap().get(
                VSGameUtilsKt.getDimensionId((ClientLevel) (Object) this)
            );
            if (params != null) {
                getShipObjectWorld().addDimension(
                    VSGameUtilsKt.getDimensionId((ClientLevel) (Object) this),
                    VSGameUtilsKt.getYRange((ClientLevel) (Object) this),
                    params.getGravity(),
                    params.getSeaLevel(),
                    params.getMaxY()
                );
                return;
            }
            getShipObjectWorld().addDimension(
                VSGameUtilsKt.getDimensionId((ClientLevel) (Object) this),
                VSGameUtilsKt.getYRange((ClientLevel) (Object) this),
                McMathUtilKt.getDEFAULT_WORLD_GRAVITY(),
                AerodynamicUtils.DEFAULT_SEA_LEVEL,
                AerodynamicUtils.DEFAULT_MAX
            );
        }

    }


    @Inject(method = "disconnect", at = @At("TAIL"))
    private void afterDisconnect(final CallbackInfo ci) {
        getVsCore().getHooks().afterDisconnect();
    }

    // do particle ticks for ships near the player
    @Inject(
        at = @At("TAIL"),
        method = "animateTick"
    )
    private void afterAnimatedTick(final int posX, final int posY, final int posZ, final CallbackInfo ci) {
        boolean holdingBarrierItem = false;
        if (this.minecraft.f_91072_.m_105295_() == GameType.CREATIVE) {
            for (final ItemStack itemStack : this.minecraft.f_91074_.m_6167_()) {
                if (itemStack.m_41720_() == Blocks.f_50375_.m_5456_()) {
                    holdingBarrierItem = true;
                    break;
                }
            }
        }
        Player player = this.minecraft.f_91074_;
        assert player != null;

        final double playerScale = player.m_20205_() / Player.f_36088_.f_20377_;


        // use more precise player position, since ship blocks aren't locked to the grid of integer coordinates in world space
        final Vec3 pos = player.m_20182_();

        final AABBdc playerCenterBB = new AABBd(pos.f_82479_, pos.f_82480_, pos.f_82481_, pos.f_82479_, pos.f_82480_, pos.f_82481_);
        final AABBdc shipIntersectBB = AABBdUtilKt.expand(new AABBd(playerCenterBB), 32.0);
        final double biggerBBProbability = 668.0 / (32.0 * 32.0 * 32.0);
        final double smallerBBProbability = 668.0 / (16.0 * 16.0 * 16.0);

        final AABBd temp0 = new AABBd();
        final AABBi temp1 = new AABBi();
        final AABBd temp2 = new AABBd();
        final AABBi temp3 = new AABBi();
        final AABBi temp4 = new AABBi();
        final AABBi temp5 = new AABBi();
        final AABBd temp6 = new AABBd();
        final AABBd temp7 = new AABBd();
        for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(ClientLevel.class.cast(this), shipIntersectBB)) {
            final AABBic shipVoxelAABB = ship.getShipAABB();
            if (shipVoxelAABB == null) {
                continue;
            }

            // Scaled to reduce lag for mini ships
            final AABBdc biggerBB = AABBdUtilKt.expand(temp6.set(playerCenterBB), 32.0 * playerScale);
            final AABBdc smallerBB = AABBdUtilKt.expand(temp7.set(playerCenterBB), 16.0 * playerScale);

            // Only spawn particles in the intersection of the ship bounding box and the particle spawning bounding
            // boxes surrounding the player
            final AABBic biggerBBTransformed =
                VectorConversionsKt.toAABBi(biggerBB.transform(ship.getWorldToShip(), temp0), temp1);
            final AABBic smallerBBTransformed =
                VectorConversionsKt.toAABBi(smallerBB.transform(ship.getWorldToShip(), temp2), temp3);

            // Expand [shipVoxelAABB] by 1 on each side to account for blocks like torches not expanding the voxel AABB
            final AABBic biggerBBIntersection =
                VectorConversionsKt.expand(shipVoxelAABB, 1, temp4).intersection(biggerBBTransformed);
            final AABBic smallerBBIntersection =
                VectorConversionsKt.expand(shipVoxelAABB, 1, temp5).intersection(smallerBBTransformed);

            if (biggerBBIntersection.isValid()) {
                animateTickVS(biggerBBIntersection, biggerBBProbability, holdingBarrierItem);
            }
            if (smallerBBIntersection.isValid()) {
                animateTickVS(smallerBBIntersection, smallerBBProbability, holdingBarrierItem);
            }
        }
    }

    @Unique
    private void animateTickVS(
        final AABBic region,
        final double regionBlockProbability,
        final boolean holdingBarrierItem
    ) {
        final int volume = (region.maxX() - region.minX() + 1) * (region.maxY() - region.minY() + 1)
            * (region.maxZ() - region.minZ() + 1);
        final double blocksToTickAsDouble = volume * regionBlockProbability;
        int blocksToTick = (int) Math.floor(blocksToTickAsDouble);
        // Handle the case of partial blocks to tick
        if (vsRandom.m_188500_() > blocksToTickAsDouble - blocksToTick) {
            blocksToTick++;
        }
        final ClientLevel thisAsClientLevel = ClientLevel.class.cast(this);
        final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        final int minCX = SectionPos.m_123171_(region.minX());
        final int maxCX = SectionPos.m_123171_(region.maxX());
        final int minCZ = SectionPos.m_123171_(region.minZ());
        final int maxCZ = SectionPos.m_123171_(region.maxZ());

        // chunk lookup from level is very slow, this helps a lot
        final LevelChunk[][] chunkCache = new LevelChunk[maxCX - minCX + 1][maxCZ - minCZ + 1];

        for (int i = 0; i < blocksToTick; i++) {
            final int posX = region.minX() + vsRandom.m_188503_(region.maxX() - region.minX() + 1);
            final int posY = region.minY() + vsRandom.m_188503_(region.maxY() - region.minY() + 1);
            final int posZ = region.minZ() + vsRandom.m_188503_(region.maxZ() - region.minZ() + 1);

            mutableBlockPos.m_122178_(posX, posY, posZ);
            if (thisAsClientLevel.m_151570_(mutableBlockPos)) continue;

            final int cx = SectionPos.m_123171_(posX) - minCX;
            final int cz = SectionPos.m_123171_(posZ) - minCZ;

            LevelChunk levelChunk = chunkCache[cx][cz];
            if (levelChunk == null) {
                levelChunk = thisAsClientLevel.m_6325_(cx + minCX, cz + minCZ);
                chunkCache[cx][cz] = levelChunk;
            }

            final BlockState blockState = levelChunk.m_8055_(mutableBlockPos);
            blockState.m_60734_().m_214162_(blockState, thisAsClientLevel, mutableBlockPos, vsRandom);
            final FluidState fluidState = levelChunk.m_6425_(mutableBlockPos);
            if (!fluidState.m_76178_()) {
                fluidState.m_230558_(thisAsClientLevel, mutableBlockPos, vsRandom);
                final ParticleOptions particleOptions = fluidState.m_76189_();
                if (particleOptions != null && vsRandom.m_188503_(10) == 0) {
                    final boolean bl2 = blockState.m_60783_(thisAsClientLevel, mutableBlockPos, Direction.DOWN);
                    final BlockPos blockPos = mutableBlockPos.m_7495_();
                    if (!thisAsClientLevel.m_151570_(blockPos)) {
                        this.trySpawnDripParticles(blockPos, levelChunk.m_8055_(blockPos), particleOptions, bl2);
                    }
                }
            }

            if (holdingBarrierItem && blockState.m_60713_(Blocks.f_50375_)) {
                thisAsClientLevel.m_7106_(new BlockParticleOption(ParticleTypes.f_194652_, blockState),
                    (double) posX + 0.5, (double) posY + 0.5,
                    (double) posZ + 0.5, 0.0, 0.0, 0.0);
            }
        }
    }

    @Redirect(
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFLnet/minecraft/util/RandomSource;DDD)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"
        ),
        method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V"
    )
    private SimpleSoundInstance redirectNewSoundInstance(final SoundEvent soundEvent, final SoundSource soundSource,
        final float volume, final float pitch, final RandomSource randomSource, final double x, final double y,
        final double z) {

        final Ship ship = VSGameUtilsKt.getShipManagingPos(ClientLevel.class.cast(this), x, y, z);
        if (ship != null) {
            return new SimpleSoundInstanceOnShip(soundEvent, soundSource, volume, pitch, randomSource, x, y, z,
                ship);
        }

        return new SimpleSoundInstance(soundEvent, soundSource, volume, pitch, randomSource, x, y, z);
    }

}
