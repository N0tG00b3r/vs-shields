package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.entity.BoardingPodEntity;
import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
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

    public static final RegistrySupplier<EntityType<BoardingPodEntity>> BOARDING_POD =
            ENTITIES.register("boarding_pod", () ->
                    EntityType.Builder.<BoardingPodEntity>of(
                                    BoardingPodEntity::new, MobCategory.MISC)
                            .sized(1.8f, 1.5f)
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("boarding_pod"));

    public static final RegistrySupplier<EntityType<CockpitSeatEntity>> COCKPIT_SEAT =
            ENTITIES.register("cockpit_seat", () ->
                    EntityType.Builder.<CockpitSeatEntity>of(
                                    CockpitSeatEntity::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(128)
                            .updateInterval(1)
                            .build("cockpit_seat"));

    public static void register() {
        ENTITIES.register();
    }
}
