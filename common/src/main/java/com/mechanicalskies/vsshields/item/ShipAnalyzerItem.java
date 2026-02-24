package com.mechanicalskies.vsshields.item;

import com.mechanicalskies.vsshields.client.ClientAnalyzerData;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class ShipAnalyzerItem extends Item {
    public static final int USE_DURATION = 72000;

    public ShipAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide && (USE_DURATION - remainingUseDuration) % 10 == 0) {
            ModNetwork.sendAnalyzerScan(entity.getEyePosition(), entity.getLookAngle());
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (level.isClientSide) {
            ClientAnalyzerData.getInstance().clear();
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // Spyglass animation gives FOV zoom + vignette overlay for free from vanilla
        return UseAnim.SPYGLASS;
    }
}
