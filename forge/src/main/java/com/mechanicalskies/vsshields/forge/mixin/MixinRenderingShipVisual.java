package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.primitives.AABBdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.compat.flywheel.RenderingShipVisual;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides cloaked ships on the Flywheel render path by calling setVisible(false)
 * on every TransformedInstance that belongs to the ship.
 *
 * RenderingShipVisual is the class that renders actual ship BLOCKS via Flywheel
 * (one TransformedInstance per chunk section). update(float) is called every frame
 * on the main thread before planFrame() schedules work on worker threads —
 * setting visibility here ensures Flywheel skips invisible instances entirely.
 */
@Mixin(value = RenderingShipVisual.class, remap = false)
public abstract class MixinRenderingShipVisual {

    private static final Logger LOGGER = LoggerFactory.getLogger("vs_shields/cloak_flywheel");
    private static final Set<Long> loggedShips = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Cached reflection handles — resolved once per class, reused every frame
    private static volatile Field instancesField = null;
    private static volatile Method setVisibleMethod = null;

    @Inject(method = "update", at = @At("RETURN"), remap = false)
    private void afterUpdate(float partialTick, CallbackInfo ci) {
        try {
            // Get ShipEffect via public getter, then get ClientShip via reflection
            Object effect = this.getClass().getMethod("getEffect").invoke(this);
            if (effect == null) return;

            Object shipObj = effect.getClass().getMethod("getShip").invoke(effect);
            if (!(shipObj instanceof ClientShip)) return;
            ClientShip ship = (ClientShip) shipObj;

            long shipId = ship.getId();
            boolean cloaked = CloakedShipsRegistry.getInstance().isCloaked(shipId);
            boolean aboard = cloaked && isPlayerAboard(ship);

            if (loggedShips.add(shipId)) {
                LOGGER.info("RenderingShipVisual.update: ship={} cloaked={}", shipId, cloaked);
            }

            // Resolve the instances field once and cache it
            if (instancesField == null) {
                for (Class<?> c = this.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        Field f = c.getDeclaredField("instances");
                        f.setAccessible(true);
                        instancesField = f;
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
            }
            if (instancesField == null) {
                LOGGER.warn("Could not find 'instances' field on {}", this.getClass().getName());
                return;
            }

            Object instancesMap = instancesField.get(this);
            if (instancesMap == null) return;

            // Long2ObjectMap implements java.util.Map, so values() works
            Collection<?> instances = ((java.util.Map<?, ?>) instancesMap).values();
            if (instances.isEmpty()) return;

            // Resolve setVisible(boolean) once from the first instance
            if (setVisibleMethod == null) {
                Object first = instances.iterator().next();
                for (Class<?> c = first.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        Method m = c.getDeclaredMethod("setVisible", boolean.class);
                        m.setAccessible(true);
                        setVisibleMethod = m;
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
            }
            if (setVisibleMethod == null) {
                LOGGER.warn("Could not find setVisible(boolean) on instance class");
                return;
            }

            boolean visible = !cloaked || aboard;
            for (Object instance : instances) {
                setVisibleMethod.invoke(instance, visible);
            }

            if (cloaked && !aboard && loggedShips.size() == 1) {
                LOGGER.info("Cloaking applied to ship {} ({} instances hidden)", shipId, instances.size());
            }

        } catch (Exception e) {
            LOGGER.error("MixinRenderingShipVisual.afterUpdate failed", e);
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
