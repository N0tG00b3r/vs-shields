package com.mechanicalskies.vsshields.item;

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.config.ShieldConfig;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EnergyCellItem extends Item {

    public EnergyCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        if (be instanceof ShieldGeneratorBlockEntity gen) {
            int fe = ShieldConfig.get().getGeneral().energyCellFE;
            int added = gen.receiveEnergy(fe, false);
            if (added > 0) {
                ctx.getItemInHand().shrink(1);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }
}
