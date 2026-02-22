package com.mechanicalskies.vsshields.mixin;

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Intercepts Valkyrien Skies 2 chunk rendering pipeline to completely cull
 * cloaked ships.
 * priority = 2000 ensures this applies AFTER VS2's MixinLevelRendererVanilla.
 */
@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererCloakMixin {

    private static Field shipRenderChunksField = null;

    @Inject(method = "vs$addShipVisibleChunks", at = @At("TAIL"), remap = false, require = 0 // Failsafe if method
                                                                                             // changes signature in
                                                                                             // newer VS2 version
    )
    private void cullCloakedShips(CallbackInfo ci) {
        try {
            if (shipRenderChunksField == null) {
                shipRenderChunksField = LevelRenderer.class.getDeclaredField("shipRenderChunks");
                shipRenderChunksField.setAccessible(true);
            }

            @SuppressWarnings("unchecked")
            Map<Object, ?> shipRenderChunks = (Map<Object, ?>) shipRenderChunksField.get(this);

            if (shipRenderChunks != null && !shipRenderChunks.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                ClientShip playerShip = null;
                if (mc.player != null) {
                    playerShip = VSClientGameUtils.getClientShip(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                }

                ClientShip finalPlayerShip = playerShip;
                shipRenderChunks.entrySet().removeIf(entry -> {
                    Object key = entry.getKey();
                    if (key instanceof Ship ship) {
                        long shipId = ship.getId();

                        if (CloakedShipsRegistry.getInstance().isCloaked(shipId)) {
                            // Do not hide the ship if the local player is currently ON IT
                            if (finalPlayerShip != null && finalPlayerShip.getId() == shipId) {
                                return false; // Keep it
                            }
                            return true; // Cull it
                        }
                    }
                    return false;
                });
            }
        } catch (Exception e) {
            // Field might be renamed or missing in this VS2 version, ignore gracefully to
            // prevent crashes
        }
    }
}
