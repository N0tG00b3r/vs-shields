package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.entity.GravitationalMineEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(VSShieldsMod.MOD_ID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<GravitationalMineEntity>> GRAVITATIONAL_MINE =
            ENTITIES.register("gravitational_mine", () ->
                    EntityType.Builder.<GravitationalMineEntity>of(
                                    GravitationalMineEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("gravitational_mine"));

    public static void register() {
        ENTITIES.register();
    }
}
