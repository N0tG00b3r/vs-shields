package com.mechanicalskies.vsshields.blockentity;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ShieldJammerTracker {
    private static final Set<ShieldJammerControllerBlockEntity> LOADED_RAMS = Collections
            .newSetFromMap(new ConcurrentHashMap<>());

    public static void addRam(ShieldJammerControllerBlockEntity ram) {
        LOADED_RAMS.add(ram);
    }

    public static void removeRam(ShieldJammerControllerBlockEntity ram) {
        LOADED_RAMS.remove(ram);
    }

    public static Set<ShieldJammerControllerBlockEntity> getLoadedRams() {
        return LOADED_RAMS;
    }
}
