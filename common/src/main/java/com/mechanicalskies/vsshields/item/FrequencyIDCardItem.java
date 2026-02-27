package com.mechanicalskies.vsshields.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A programmable ID card.  The accessCode NBT tag stores up to 8 alphanumeric chars (case-sensitive).
 * Empty card (no tag / empty string) grants no access.
 *
 * Shift+Right-Click → opens FrequencyIDCardScreen (client-side EditBox to set the code).
 * Plain Right-Click → standard item use (opens block GUI when pointing at a block).
 */
public class FrequencyIDCardItem extends Item {

    public FrequencyIDCardItem() {
        super(new Item.Properties().stacksTo(8));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isCrouching()) {
            if (level.isClientSide) {
                // Open the programming screen
                openProgrammingScreen(player.getItemInHand(hand));
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    // Called via EnvExecutor / client-only wrapper to avoid classloading issues
    private static void openProgrammingScreen(ItemStack stack) {
        com.mechanicalskies.vsshields.client.FrequencyIDCardScreen.open(stack);
    }

    // --- Helpers ---

    public static String getCode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("accessCode")) return "";
        return tag.getString("accessCode");
    }

    public static void setCode(ItemStack stack, String code) {
        if (code == null) code = "";
        code = code.replaceAll("[^a-zA-Z0-9]", "");
        if (code.length() > 8) code = code.substring(0, 8);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("accessCode", code);
    }

    public static boolean hasMatchingCode(ItemStack stack, String targetCode) {
        if (targetCode == null || targetCode.isBlank()) return false;
        String code = getCode(stack);
        return !code.isBlank() && code.equals(targetCode);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
            TooltipFlag isAdvanced) {
        String code = getCode(stack);
        if (code.isBlank()) {
            tooltipComponents.add(Component.translatable("item.vs_shields.frequency_id_card.blank")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltipComponents.add(Component.translatable("item.vs_shields.frequency_id_card.code", code)
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        tooltipComponents.add(Component.translatable("item.vs_shields.frequency_id_card.desc")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
