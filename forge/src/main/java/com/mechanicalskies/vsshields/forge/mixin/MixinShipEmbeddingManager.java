package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.compat.flywheel.ShipEmbeddingManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suppresses Flywheel-rendered ship visuals for cloaked ships.
 *
 * VS2 uses ShipEmbeddingManager (Flywheel) instead of MixinLevelRendererVanilla
 * when Create is installed. This mixin intercepts updateAllShips() at RETURN —
 * right after VS2 sets each ship's world transform — and overrides cloaked ships
 * with a near-zero scale transform, making them invisible.
 *
 * remap=false: ShipEmbeddingManager is a VS2 class, not a Minecraft class.
 * defaultRequire=0 in mixin config: if Flywheel isn't installed this gracefully no-ops.
 */
@Mixin(value = ShipEmbeddingManager.class, remap = false)
public class MixinShipEmbeddingManager {

    private static final org.slf4j.Logger CLOAKLOG = LoggerFactory.getLogger("vs_shields/cloak_flywheel");

    // Cached reflection: vs$shipEmbedding static field on ShipEmbeddingManager
    private static volatile Field shipEmbeddingField = null;
    // Cache per embedding-class: the transforms(Matrix4fc, Matrix3fc) method
    private static final ConcurrentHashMap<Class<?>, Method> transformsCache = new ConcurrentHashMap<>();

    @Inject(method = "updateAllShips", at = @At("RETURN"), remap = false)
    private void afterUpdateAllShips(CallbackInfo ci) {
        try {
            Field f = shipEmbeddingField;
            if (f == null) {
                f = ShipEmbeddingManager.class.getDeclaredField("vs$shipEmbedding");
                f.setAccessible(true);
                shipEmbeddingField = f;
                CLOAKLOG.info("MixinShipEmbeddingManager active (Flywheel cloak suppression ready)");
            }

            @SuppressWarnings("unchecked")
            ConcurrentHashMap<ClientShip, Object> embeddingMap =
                    (ConcurrentHashMap<ClientShip, Object>) f.get(null);
            if (embeddingMap == null || embeddingMap.isEmpty()) return;

            CloakedShipsRegistry registry = CloakedShipsRegistry.getInstance();

            for (Map.Entry<ClientShip, Object> entry : embeddingMap.entrySet()) {
                ClientShip ship = entry.getKey();
                if (ship == null) continue;
                if (!registry.isCloaked(ship.getId())) continue;

                Object embedding = entry.getValue();
                if (embedding == null) continue;

                // Find (and cache) the transforms(Matrix4fc, Matrix3fc) method
                Method transforms = transformsCache.computeIfAbsent(
                        embedding.getClass(), MixinShipEmbeddingManager::findTransformsMethod);
                if (transforms == null) continue;

                // Near-zero scale — ship occupies ~0 pixels, effectively invisible
                transforms.invoke(embedding,
                        new Matrix4f().scale(0.0001f),
                        new Matrix3f());
            }
        } catch (Exception ignored) {
            // Flywheel not present or API changed — safe to ignore
        }
    }

    private static Method findTransformsMethod(Class<?> cls) {
        for (Method m : cls.getMethods()) {
            if ("transforms".equals(m.getName()) && m.getParameterCount() == 2) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
