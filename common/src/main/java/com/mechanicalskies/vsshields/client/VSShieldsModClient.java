package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.network.ClientShieldManager;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModMenus;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

public class VSShieldsModClient {
    public static void initClient() {
        registerPackets();
        BlockEntityRendererRegistry.register(ModBlockEntities.SHIELD_GENERATOR.get(), ShieldRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.CLOAKING_FIELD_GENERATOR.get(),
                CloakShimmerRenderer::new);

        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_GENERATOR_MENU.get(), ShieldGeneratorScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_BATTERY_MENU.get(), ShieldBatteryScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.CLOAK_GENERATOR_MENU.get(), CloakGeneratorScreen::new);
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> ShieldHudOverlay.render(graphics, delta));

        // Ambient shield hum sound + shield break animations
        dev.architectury.event.events.client.ClientTickEvent.CLIENT_POST.register(instance -> {
            ShieldAmbientSoundHandler.tick();
            ShieldEffectHandler.tick();
        });

        // Cloaking: use VS2's public render events to track which ship is being
        // rendered
        registerCloakRenderHook();
    }

    private static void registerPackets() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.SHIELD_SYNC_ID,
                (buf, context) -> {
                    int count = buf.readVarInt();
                    long[] shipIds = new long[count];
                    double[] currentHPs = new double[count];
                    double[] maxHPs = new double[count];
                    boolean[] actives = new boolean[count];
                    double[] energyPercents = new double[count];
                    double[] minXs = new double[count], minYs = new double[count], minZs = new double[count];
                    double[] maxXs = new double[count], maxYs = new double[count], maxZs = new double[count];
                    boolean[] hasAABB = new boolean[count];
                    for (int i = 0; i < count; i++) {
                        shipIds[i] = buf.readLong();
                        currentHPs[i] = buf.readDouble();
                        maxHPs[i] = buf.readDouble();
                        actives[i] = buf.readBoolean();
                        energyPercents[i] = buf.readDouble();
                        hasAABB[i] = buf.readBoolean();
                        if (hasAABB[i]) {
                            minXs[i] = buf.readDouble();
                            minYs[i] = buf.readDouble();
                            minZs[i] = buf.readDouble();
                            maxXs[i] = buf.readDouble();
                            maxYs[i] = buf.readDouble();
                            maxZs[i] = buf.readDouble();
                        }
                    }
                    context.queue(() -> {
                        ClientShieldManager csm = ClientShieldManager.getInstance();
                        csm.clear();
                        for (int i = 0; i < count; i++) {
                            csm.updateShield(shipIds[i], currentHPs[i], maxHPs[i], actives[i],
                                    energyPercents[i],
                                    minXs[i], minYs[i], minZs[i],
                                    maxXs[i], maxYs[i], maxZs[i]);
                        }
                    });
                });

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.NUKE_VISUAL_ID,
                (buf, context) -> {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    context.queue(() -> {
                        try {
                            ResourceLocation nukeId = new ResourceLocation("alexscaves", "nuclear_explosion");
                            Optional<EntityType<?>> opt = BuiltInRegistries.ENTITY_TYPE.getOptional(nukeId);
                            if (opt.isEmpty())
                                return;
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.level == null)
                                return;
                            Entity nukeEntity = opt.get().create(mc.level);
                            if (nukeEntity == null)
                                return;
                            nukeEntity.setPos(x, y, z);
                            mc.level.putNonPlayerEntity(nukeEntity.getId(), nukeEntity);
                        } catch (Exception ignored) {
                        }
                    });
                });

        // Shield hit effect (particles at impact point)
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.SHIELD_HIT_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    float damage = buf.readFloat();
                    context.queue(() -> ShieldEffectHandler.onShieldHit(shipId, x, y, z, damage));
                });

        // Shield break effect (shatter animation)
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                ModNetwork.SHIELD_BREAK_ID,
                (buf, context) -> {
                    long shipId = buf.readLong();
                    context.queue(() -> ShieldEffectHandler.onShieldBreak(shipId));
                });
    }

    /**
     * Hooks into VS2's public render events for cloaking (currently dormant).
     * Waiting for VS2 developers to provide a stable per-ship render suppression
     * API.
     * The CloakRenderState tracking is kept for future use.
     */
    private static void registerCloakRenderHook() {
        try {
            org.valkyrienskies.mod.common.hooks.VSGameEvents.INSTANCE.getRenderShip().on(event -> {
                org.valkyrienskies.core.api.ships.ClientShip ship = event.getShip();
                boolean shouldSkip = ClientCloakManager.getInstance().isCloaked(ship.getId());
                if (shouldSkip) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        org.valkyrienskies.core.api.ships.ClientShip playerShip = org.valkyrienskies.mod.common.VSClientGameUtils
                                .getClientShip(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        if (playerShip != null && playerShip.getId() == ship.getId()) {
                            shouldSkip = false;
                        }
                    }
                }
                CloakRenderState.beginShipRender(ship.getId(), shouldSkip);
            });

            org.valkyrienskies.mod.common.hooks.VSGameEvents.INSTANCE.getPostRenderShip().on(event -> {
                CloakRenderState.endShipRender();
            });
        } catch (Exception e) {
            System.err.println("[vs-shields] Failed to register VS2 render events: " + e.getMessage());
        }
    }
}
