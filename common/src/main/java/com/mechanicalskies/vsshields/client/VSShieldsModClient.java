package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModEntities;
import com.mechanicalskies.vsshields.registry.ModMenus;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class VSShieldsModClient {
    public static void initClient() {
        // registerPackets() is now handled by EnvExecutor in ModNetwork.init()
        BlockEntityRendererRegistry.register(ModBlockEntities.SHIELD_GENERATOR.get(), ShieldRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.CLOAKING_FIELD_GENERATOR.get(),
                CloakShimmerRenderer::new);

        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_GENERATOR_MENU.get(), ShieldGeneratorScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_BATTERY_MENU.get(), ShieldBatteryScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.CLOAK_GENERATOR_MENU.get(), CloakGeneratorScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.shield_jammer_MENU.get(),
                com.mechanicalskies.vsshields.client.ShieldJammerScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.GRAVITY_FIELD_MENU.get(),
                com.mechanicalskies.vsshields.client.GravityFieldScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.SOLID_PROJECTION_MODULE_MENU.get(),
                com.mechanicalskies.vsshields.client.SolidProjectionModuleScreen::new);
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> ShieldHudOverlay.render(graphics, delta));
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> AnalyzerHudOverlay.render(graphics, delta));

        EntityRendererRegistry.register(ModEntities.GRAVITATIONAL_MINE, ThrownItemRenderer::new);

        // Ambient shield hum sound + shield break animations + helm scanner keybind
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(instance -> {
            ShieldAmbientSoundHandler.tick();
            ShieldEffectHandler.tick();
            HelmAnalyzerHandler.tick(instance);
        });
    }

}
