package com.mechanicalskies.vsshields.item;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.function.Supplier;

/**
 * Tactical Goggles — equippable in regular helmet slot OR Curios head slot.
 * Abilities:
 * - Passive night vision (handled in forge tick handler)
 * - Ship analyzer HUD (Y toggle, via HelmAnalyzerHandler)
 * - Zoom (Shift+V, via forge FOV modifier)
 * - 3D model on head (via ICurioRenderer / IClientItemExtensions)
 */
public class TacticalGogglesItem extends ArmorItem {

    /** Set from VSShieldsModForgeClient once the model layer has been baked. */
    private static Supplier<HumanoidModel<?>> modelSupplier = null;

    public static void setModelSupplier(Supplier<HumanoidModel<?>> supplier) {
        modelSupplier = supplier;
    }

    public static Supplier<HumanoidModel<?>> getModelSupplier() {
        return modelSupplier;
    }

    public TacticalGogglesItem() {
        super(ArmorMaterials.LEATHER, Type.HELMET,
                new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    /** Forge duck-typed: IForgeItem.getArmorTexture */
    @SuppressWarnings("unused")
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "vs_shields:textures/models/armor/tactical_goggles_layer.png";
    }
}
