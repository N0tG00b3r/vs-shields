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
        BlockEntityRendererRegistry.register(ModBlockEntities.CLOAKING_FIELD_GENERATOR.get(), CloakShimmerRenderer::new);
        
        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_GENERATOR_MENU.get(), ShieldGeneratorScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.SHIELD_BATTERY_MENU.get(), ShieldBatteryScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.CLOAK_GENERATOR_MENU.get(), CloakGeneratorScreen::new);
        ClientGuiEvent.RENDER_HUD.register((graphics, delta) -> ShieldHudOverlay.render(graphics, delta));
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
                    for (int i = 0; i < count; i++) {
                        shipIds[i] = buf.readLong();
                        currentHPs[i] = buf.readDouble();
                        maxHPs[i] = buf.readDouble();
                        actives[i] = buf.readBoolean();
                    }
                    context.queue(() -> {
                        ClientShieldManager csm = ClientShieldManager.getInstance();
                        csm.clear();
                        for (int i = 0; i < count; i++) {
                            csm.updateShield(shipIds[i], currentHPs[i], maxHPs[i], actives[i]);
                        }
                    });
                }
        );

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
                            if (opt.isEmpty()) return;
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.level == null) return;
                            Entity nukeEntity = opt.get().create(mc.level);
                            if (nukeEntity == null) return;
                            nukeEntity.setPos(x, y, z);
                            mc.level.putNonPlayerEntity(nukeEntity.getId(), nukeEntity);
                        } catch (Exception ignored) {
                        }
                    });
                }
        );
    }
}
