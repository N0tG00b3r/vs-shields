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

    public static void register() {
        SOUNDS.register();
    }
}
