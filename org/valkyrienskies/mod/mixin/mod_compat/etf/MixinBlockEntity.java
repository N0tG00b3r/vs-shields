package org.valkyrienskies.mod.mixin.mod_compat.etf;

import D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import traben.entity_texture_features.utils.ETFEntity;

@Mixin(value = BlockEntity.class, priority = 1200)
public abstract class MixinBlockEntity implements ETFEntity {
    @Override
    public float etf$distanceTo(final Entity entity) {
        final var level = Minecraft.m_91087_().f_91073_;
        final var aW = VSGameUtilsKt.toWorldCoordinates(level, Vec3.m_82512_(etf$getBlockPos()));
        final var bW = VSGameUtilsKt.toWorldCoordinates(level, entity.m_20182_());
        final var dist = aW.m_82554_(bW);
        return (float) dist;
    }
}
