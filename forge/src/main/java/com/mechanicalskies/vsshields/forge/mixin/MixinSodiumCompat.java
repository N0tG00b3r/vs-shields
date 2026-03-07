package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;

import java.util.WeakHashMap;

/**
 * Client-side safety layer: removes cloaked ships from Embeddium's ship render list
 * before VS2's SodiumCompat iterates it. Prevents rendering of cloaked ship chunks
 * even if chunk data is cached on the client (race condition during cloak toggle).
 */
@Mixin(targets = "org.valkyrienskies.mod.compat.SodiumCompat", remap = false)
public class MixinSodiumCompat {

    @Inject(method = "renderShips", at = @At("HEAD"), remap = false)
    private static void vs_shields$filterCloaked(
            RenderSectionManager rsm, RenderType renderLayer,
            ChunkRenderMatrices matrices, double x, double y, double z,
            CallbackInfo ci) {

        CloakedShipsRegistry registry = CloakedShipsRegistry.getInstance();
        if (!registry.hasAnyCloakedShips()) return;

        try {
            WeakHashMap<ClientShip, ?> shipRenderLists =
                    ((RenderSectionManagerDuck) rsm).vs_getShipRenderLists();
            if (shipRenderLists == null || shipRenderLists.isEmpty()) return;

            Player player = Minecraft.getInstance().player;
            shipRenderLists.entrySet().removeIf(entry -> {
                ClientShip ship = entry.getKey();
                if (!registry.isCloaked(ship.getId())) return false;
                return player == null || !isPlayerAboard(ship, player);
            });
        } catch (Exception ignored) {
            // Graceful degradation if duck interface fails
        }
    }

    private static boolean isPlayerAboard(ClientShip ship, Player player) {
        try {
            AABBdc aabb = ship.getWorldAABB();
            double t = 2.0;
            return player.getX() >= aabb.minX() - t && player.getX() <= aabb.maxX() + t
                    && player.getY() >= aabb.minY() - t && player.getY() <= aabb.maxY() + t
                    && player.getZ() >= aabb.minZ() - t && player.getZ() <= aabb.maxZ() + t;
        } catch (Exception e) {
            return false;
        }
    }
}
