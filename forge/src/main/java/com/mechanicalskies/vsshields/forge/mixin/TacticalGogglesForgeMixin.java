package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.item.TacticalGogglesItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

@Mixin(TacticalGogglesItem.class)
public class TacticalGogglesForgeMixin {
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entityLiving, ItemStack itemStack,
                    EquipmentSlot armorSlot, HumanoidModel<?> _default) {
                if (armorSlot == EquipmentSlot.HEAD) {
                    if (TacticalGogglesItem.getModelSupplier() != null) {
                        return TacticalGogglesItem.getModelSupplier().get();
                    }
                }
                return _default;
            }
        });
    }
}
