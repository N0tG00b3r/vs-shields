package com.mechanicalskies.vsshields.item;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Netherite helmet with a built-in Ship Analyzer.
 * Activate by holding the configured keybind (default: Y).
 * Repair material: netherite ingot (inherited from ArmorMaterials.NETHERITE).
 *
 * Uses a custom 3D model (TacticalHelmModel) via the Forge armor model
 * duck-type API.
 * The model supplier is set from the forge-client module after layers are baked
 * (EntityRenderersEvent.AddLayers), so there are no client-class references in
 * this file.
 */
public class TacticalNetheriteHelm extends ArmorItem {

    /** Set from VSShieldsModForgeClient once the model layer has been baked. */
    private static Supplier<HumanoidModel<?>> modelSupplier = null;

    public static void setModelSupplier(Supplier<HumanoidModel<?>> supplier) {
        modelSupplier = supplier;
    }

    public static Supplier<HumanoidModel<?>> getModelSupplier() {
        return modelSupplier;
    }

    public TacticalNetheriteHelm() {
        super(ArmorMaterials.NETHERITE, Type.HELMET,
                new Properties().fireResistant().stacksTo(1));
    }

    /** Forge duck-typed: IForgeItem.getArmorTexture */
    @SuppressWarnings("unused")
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "vs_shields:textures/models/armor/tactical_helm_layer_1.png";
    }

    /**
     * Forge duck-typed: IForgeItem.getArmorModel — returns custom 3D model for the
     * helmet slot.
     */
    @SuppressWarnings({ "unchecked", "unused" })
    public <A extends HumanoidModel<?>> A getArmorModel(
            LivingEntity entity, ItemStack stack, EquipmentSlot slot, A defaultModel) {
        if (slot == EquipmentSlot.HEAD && modelSupplier != null) {
            return (A) modelSupplier.get();
        }
        return null;
    }
}
