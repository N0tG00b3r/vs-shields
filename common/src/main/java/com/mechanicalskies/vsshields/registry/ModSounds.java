package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(VSShieldsMod.MOD_ID,
            Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> SHIELD_HUM = SOUNDS.register(
            "shield_hum",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VSShieldsMod.MOD_ID, "shield_hum")));

    public static final RegistrySupplier<SoundEvent> SHIELD_HIT = SOUNDS.register(
            "shield_hit",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VSShieldsMod.MOD_ID, "shield_hit")));

    public static final RegistrySupplier<SoundEvent> SHIELD_COLLAPSE = SOUNDS.register(
            "shield_collapse",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VSShieldsMod.MOD_ID, "shield_collapse")));

    public static final RegistrySupplier<SoundEvent> SHIELD_ACTIVATION = SOUNDS.register(
            "shield_activation",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VSShieldsMod.MOD_ID, "shield_activation")));

    public static final RegistrySupplier<SoundEvent> SHIELD_DEACTIVATION = SOUNDS.register(
            "shield_deactivation",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VSShieldsMod.MOD_ID, "shield_deactivation")));

    public static final RegistrySupplier<SoundEvent> SHIELD_REGENERATION = SOUNDS.register(
            "shield_regeneration",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VSShieldsMod.MOD_ID, "shield_regeneration")));

    /** Gravitational mine detonation — add vs_shields:sounds/mine_explosion.ogg to activate. */
    public static final RegistrySupplier<SoundEvent> MINE_EXPLOSION = SOUNDS.register(
            "mine_explosion",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(VSShieldsMod.MOD_ID, "mine_explosion")));

    public static void register() {
        SOUNDS.register();
    }
}
