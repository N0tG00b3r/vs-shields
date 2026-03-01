package org.valkyrienskies.mod.mixin.world.chunk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VSLevelChunk;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk extends ChunkAccess implements VSLevelChunk {
    @Shadow
    @Final
    Level level;

    @Shadow
    @Mutable
    private LevelChunkTicks<Block> blockTicks;
    @Shadow
    @Mutable
    private LevelChunkTicks<Fluid> fluidTicks;

    @Unique
    private static final Set<Types> ALL_HEIGHT_MAP_TYPES = new HashSet<>(Arrays.asList((Heightmap.Types.values())));

    // Dummy constructor
    public MixinLevelChunk(final Ship ship) {
        super(null, null, null, null, 0, null, null);
        throw new IllegalStateException("This should never be called!");
    }

    @Inject(method = "setBlockState", at = @At("TAIL"))
    public void postSetBlockState(final BlockPos pos, final BlockState state, final boolean moved,
        final CallbackInfoReturnable<BlockState> cir) {
        final BlockState prevState = cir.getReturnValue();
        // This function is getting invoked by non-game threads for some reason. So use executeOrSchedule() to schedule
        // onSetBlock() to be run on the next tick when this function is invoked by a non-game thread.
        // See https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/913 for more info.
        VSGameUtilsKt.executeOrSchedule(level, () -> BlockStateInfo.INSTANCE.onSetBlock(level, pos, prevState, state));
    }

    @Shadow
    public abstract void clearAllBlockEntities();

    @Shadow
    public abstract void registerTickContainerInLevel(ServerLevel serverLevel);

    @Shadow
    public abstract void unregisterTickContainerFromLevel(ServerLevel serverLevel);

    @Override
    public void clearChunk() {
        clearAllBlockEntities();
        unregisterTickContainerFromLevel((ServerLevel) level);

        // Set terrain to empty
        f_187608_.clear();
        Arrays.fill(f_187612_, null);
        final Registry<Biome> registry = level.m_9598_().m_175515_(Registries.f_256952_);
        for (int i = 0; i < f_187612_.length; ++i) {
            if (f_187612_[i] != null) continue;
            //new LevelChunkSection(registry);
            f_187612_[i] = new LevelChunkSection(registry);
        }
        this.m_8094_(false);

        registerTickContainerInLevel((ServerLevel) level);
        this.f_187603_ = true;
    }

    @Override
    public void copyChunkFromOtherDimension(@NotNull final VSLevelChunk srcChunkVS) {
        clearAllBlockEntities();
        unregisterTickContainerFromLevel((ServerLevel) level);

        // Set terrain to empty
        f_187608_.clear();
        Arrays.fill(f_187612_, null);

        // Copy heightmap and sections and block entities from srcChunk
        final LevelChunk srcChunk = (LevelChunk) srcChunkVS;
        final CompoundTag compoundTag = ChunkSerializer.m_63454_((ServerLevel) srcChunk.m_62953_(), srcChunk);
        // Set status to be ProtoChunk to fix block entities not saving
        compoundTag.m_128359_("Status", ChunkStatus.ChunkType.PROTOCHUNK.name());
        final ProtoChunk protoChunk = ChunkSerializer.m_188230_((ServerLevel) level, ((ServerLevel) level).m_8904_(), f_187604_, compoundTag);

        this.blockTicks = protoChunk.m_188181_();
        this.fluidTicks = protoChunk.m_188182_();
        // Copy data from the protoChunk
        // this.chunkPos = chunkPos;
        // this.upgradeData = upgradeData;
        // this.levelHeightAccessor = levelHeightAccessor;

        for (int i = 0; i < f_187612_.length; i++) {
            f_187612_[i] = protoChunk.m_183278_(i);
        }
        final Registry<Biome> registry = level.m_9598_().m_175515_(Registries.f_256952_);
        for (int i = 0; i < f_187612_.length; ++i) {
            if (f_187612_[i] != null) continue;
            f_187612_[i] = new LevelChunkSection(registry);
        }

        // this.inhabitedTime = l;
        // this.postProcessing = new ShortList[levelHeightAccessor.getSectionsCount()];
        this.f_187607_ = protoChunk.m_183407_();

        for (final BlockEntity blockEntity : protoChunk.m_63292_().values()) {
            this.m_142169_(blockEntity);
        }
        this.f_187609_.putAll(protoChunk.m_63294_());
        for (int i = 0; i < protoChunk.m_6720_().length; ++i) {
            this.f_187602_[i] = protoChunk.m_6720_()[i];
        }
        this.m_8040_(protoChunk.m_6633_());
        this.m_62737_(protoChunk.m_62769_());

        // Recompute height maps instead of getting them from protoChunk (This fixes crashes from missing height maps)
        Heightmap.m_64256_(this, ALL_HEIGHT_MAP_TYPES);
        this.m_8094_(false);

        registerTickContainerInLevel((ServerLevel) level);

        this.f_187603_ = true;
    }
}
