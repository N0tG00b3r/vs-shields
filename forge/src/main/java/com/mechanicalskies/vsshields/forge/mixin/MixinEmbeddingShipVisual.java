package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.primitives.AABBdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.compat.flywheel.EmbeddingShipVisual;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixes cloaking for the Flywheel renderer path (used when Create is installed).
 * Injects at RETURN of updateEmbedding() to override the real ship transform
 * with near-zero scale when the ship is cloaked.
 */
@Mixin(value = EmbeddingShipVisual.class, remap = false)
public abstract class MixinEmbeddingShipVisual {

    private static final Logger LOGGER = LoggerFactory.getLogger("vs_shields/cloak_flywheel");
    private static final Set<Long> loggedShips = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Diagnostic counters — logged first 3 calls only
    private static final AtomicInteger COUNT_UPDATE      = new AtomicInteger(0);
    private static final AtomicInteger COUNT_PLANFRAME   = new AtomicInteger(0);
    private static final AtomicInteger COUNT_UPDEMBED    = new AtomicInteger(0);

    @Shadow public abstract ClientShip getShip();

    // ── Diagnostic: which method does Flywheel actually call? ──────────────────

    /** Fired by Flywheel every frame if update(float) is the entry point. */
    @Inject(method = "update", at = @At("HEAD"), remap = false)
    private void probe_update(float partialTick, CallbackInfo ci) {
        int n = COUNT_UPDATE.incrementAndGet();
        if (n <= 3) LOGGER.info("[probe] update(float) called #{}", n);
    }

    /** Fired by Flywheel every frame if planFrame() is the entry point. */
    @Inject(method = "planFrame", at = @At("HEAD"), remap = false)
    private void probe_planFrame(CallbackInfoReturnable<Object> cir) {
        int n = COUNT_PLANFRAME.incrementAndGet();
        if (n <= 3) LOGGER.info("[probe] planFrame() called #{}", n);
    }

    // ── Actual cloaking logic (injected at RETURN of updateEmbedding) ──────────

    @Inject(method = "updateEmbedding", at = @At("RETURN"), remap = false)
    private void afterUpdateEmbedding(Object ctx, CallbackInfo ci) {
        int n = COUNT_UPDEMBED.incrementAndGet();
        if (n <= 3) LOGGER.info("[probe] updateEmbedding() called #{}", n);

        try {
            ClientShip ship = getShip();
            if (ship == null) return;

            long shipId = ship.getId();
            boolean cloaked = CloakedShipsRegistry.getInstance().isCloaked(shipId);

            if (loggedShips.add(shipId)) {
                LOGGER.info("updateEmbedding fired: ship={} cloaked={}", shipId, cloaked);
            }

            if (!cloaked) return;
            if (isPlayerAboard(ship)) return;

            Object embedding = this.getClass().getMethod("getEmbedding").invoke(this);
            if (embedding == null) return;

            embedding.getClass()
                    .getMethod("transforms", Matrix4fc.class, Matrix3fc.class)
                    .invoke(embedding, new Matrix4f().scale(0.0001f), new Matrix3f());

            LOGGER.info("Cloaking applied to ship {} via Flywheel embedding", shipId);

        } catch (Exception e) {
            LOGGER.error("MixinEmbeddingShipVisual failed", e);
        }
    }

    private static boolean isPlayerAboard(ClientShip ship) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
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
