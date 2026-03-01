package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.schematics.SchematicPrinter;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SchematicannonBlockEntity.class)
public class MixinSchematicannonBlockEntity {

    @Redirect(
        method = "initializePrinter",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/SchematicPrinter;getAnchor()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos redirectGetBlockPos(final SchematicPrinter instance) {
        final BlockEntity thisBE = BlockEntity.class.cast(this);
        final BlockPos original = instance.getAnchor();
        final Ship thisShip = VSGameUtilsKt.getShipObjectManagingPos(thisBE.m_58904_(), thisBE.m_58899_());

        return BlockPos.m_274446_(
            CompatUtil.INSTANCE.toSameSpaceAs(
                thisBE.m_58904_(),
                original.m_252807_(),
                thisShip
            )
        );
    }

    @WrapOperation(
        method = {
            "launchBlock",
            "launchEntity",
            "launchBelt"
        },
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/schematics/cannon/SchematicannonBlockEntity;getBlockPos()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos useTargetSpacePos(
        SchematicannonBlockEntity instance, Operation<BlockPos> original,
        @Local(argsOnly = true) BlockPos target
    ) {
        return BlockPos.m_274446_(
            CompatUtil.INSTANCE.toSameSpaceAs(instance.m_58904_(), original.call(instance).m_252807_(), target)
        );
    }
}
