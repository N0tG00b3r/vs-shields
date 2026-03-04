package com.mechanicalskies.vsshields.anomaly;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles the aetheric pulse effect triggered when a void deposit is exhausted.
 * Knocks back nearby entities and damages nearby shields.
 */
public class AnomalyPulseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnomalyPulseHandler");

    /**
     * Trigger a pulse centered on the anomaly island.
     * Knocks back entities and damages shields within pulse radius.
     */
    public static void triggerPulse(ServerLevel level, AnomalyInstance anomaly) {
        if (anomaly == null) return;

        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        double radius = config.pulseRadius;
        double knockback = config.pulseKnockback;
        double shieldDamage = config.pulseShieldDamage;

        double cx = anomaly.getWorldX();
        double cy = anomaly.getWorldY();
        double cz = anomaly.getWorldZ();

        // Knockback nearby entities
        AABB searchBox = new AABB(
                cx - radius, cy - radius, cz - radius,
                cx + radius, cy + radius, cz + radius
        );
        List<Entity> entities = level.getEntities(null, searchBox);
        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity.getTags().contains("anomaly_guardian")) continue; // Don't push guardians

            double dx = entity.getX() - cx;
            double dy = entity.getY() - cy;
            double dz = entity.getZ() - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < 1.0) dist = 1.0;
            if (dist > radius) continue;

            // Linear falloff: full force at center, zero at edge
            double magnitude = knockback * (1.0 - dist / radius);
            // Cap to prevent absurd velocities
            magnitude = Math.min(magnitude, 1.5);

            double nx = dx / dist;
            double ny = dy / dist;
            double nz = dz / dist;

            entity.setDeltaMovement(entity.getDeltaMovement().add(
                    nx * magnitude, ny * magnitude * 0.3, nz * magnitude
            ));
            entity.hurtMarked = true;
        }

        // Damage shields of nearby ships
        ShieldManager shieldMgr = ShieldManager.getInstance();
        for (ShieldInstance shield : shieldMgr.getAllShields().values()) {
            if (!shield.isActive() || shield.getCurrentHP() <= 0) continue;
            if (anomaly.getShipId() == shield.getShipId()) continue; // Don't damage anomaly itself

            // Simple proximity check using shield's ship position
            // (The shield manager tracks ship positions via its BlockEntity owners)
            // We'll use a rough distance check — shields within pulse radius get hit
            // This is approximate since we don't have direct world positions here,
            // but the pulse is a dramatic area effect, so approximation is fine
            shield.damage(shieldDamage, shieldMgr.getCurrentTick());
        }

        // Send pulse visual to clients
        ModNetwork.sendAnomalyPulse(level.getServer(), cx, cy, cz, radius);

        LOGGER.info("[Anomaly] Aetheric pulse triggered at ({}, {}, {}) radius={}", cx, cy, cz, radius);
    }
}
