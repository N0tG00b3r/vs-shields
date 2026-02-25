package com.mechanicalskies.vsshields.item;

import com.mechanicalskies.vsshields.entity.GravitationalMineEntity;
import com.mechanicalskies.vsshields.registry.ModEntities;
import com.mechanicalskies.vsshields.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class GravitationalMineLauncherItem extends Item {

    public static final int[] RANGES = { 15, 30, 50, 70 };

    public GravitationalMineLauncherItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (!player.isCreative()) {
                ItemStack mineStack = findInInventory(player, ModItems.GRAVITATIONAL_MINE_ITEM.get());
                if (mineStack.isEmpty()) {
                    return InteractionResultHolder.fail(stack);
                }
                mineStack.shrink(1);
            }
            GravitationalMineEntity mine = new GravitationalMineEntity(
                    ModEntities.GRAVITATIONAL_MINE.get(), level);
            mine.setDeploymentDistance(getRange(stack));
            mine.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
            mine.setDeltaMovement(player.getLookAngle().scale(2.5));
            mine.flightStartPos = mine.position();

            // Detect if player is inside a ship — skip that ship during FLIGHT so the mine exits cleanly
            try {
                for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld((ServerLevel) level).getLoadedShips()) {
                    AABBdc w = ship.getWorldAABB();
                    AABB wb = new AABB(w.minX(), w.minY(), w.minZ(), w.maxX(), w.maxY(), w.maxZ());
                    if (wb.contains(player.getEyePosition())) {
                        mine.setOwnerShipId(ship.getId());
                        break;
                    }
                }
            } catch (Exception ignored) {}

            level.addFreshEntity(mine);
            player.getCooldowns().addCooldown(this, 100);
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static int getRangeIndex(ItemStack stack) {
        return stack.hasTag() ? Mth.clamp(stack.getTag().getInt("rangeIndex"), 0, RANGES.length - 1) : 1;
    }

    public static int getRange(ItemStack stack) {
        return RANGES[getRangeIndex(stack)];
    }

    public static void cycleRange(ItemStack stack, int delta) {
        stack.getOrCreateTag().putInt("rangeIndex",
                (getRangeIndex(stack) + delta + RANGES.length) % RANGES.length);
    }

    private static ItemStack findInInventory(Player player, Item item) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item)
                return s;
        }
        return ItemStack.EMPTY;
    }
}
