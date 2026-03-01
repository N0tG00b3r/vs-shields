package org.valkyrienskies.mod.forge.mixin.feature.enchanting_table;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

// This mixin is only necessary because Forge decides to replace simpler vanilla code with its own, which includes an extra check.
@Mixin(EnchantmentMenu.class)
public class MixinEnchantmentMenu {
    @WrapOperation(method = "method_17411", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;", ordinal = -1))
    private static BlockPos correctOffset(BlockPos blockPos, Vec3i offset, Operation<BlockPos> original, @Local(argsOnly = true) Level level) {
        // Repeating some work already done. Still easier than replacing the entire lambda
        if (level.m_8055_(blockPos.m_121955_(offset)).m_204336_(BlockTags.f_278384_) && level.m_8055_(blockPos.m_7918_(offset.m_123341_() / 2, offset.m_123342_(), offset.m_123343_() / 2)).m_204336_(BlockTags.f_278486_)) {
            return blockPos.m_121955_(offset);
        } else {
            Vec3 worldCoordinates = VectorConversionsMCKt.toMinecraft(
                VSGameUtilsKt.getWorldCoordinates(level, blockPos, VectorConversionsMCKt.toJOML(blockPos.m_252807_())));
            return BlockPos.m_274446_(worldCoordinates).m_121955_(offset);
        }
    }
}
