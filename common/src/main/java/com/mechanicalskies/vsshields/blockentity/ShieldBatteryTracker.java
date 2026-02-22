package com.mechanicalskies.vsshields.blockentity;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ShieldBatteryTracker {
    private static final Set<ShieldBatteryControllerBlockEntity> LOADED_BATTERIES = Collections
            .newSetFromMap(new ConcurrentHashMap<>());

    public static void addBattery(ShieldBatteryControllerBlockEntity battery) {
        LOADED_BATTERIES.add(battery);
    }

    public static void removeBattery(ShieldBatteryControllerBlockEntity battery) {
        LOADED_BATTERIES.remove(battery);
    }

    public static Set<ShieldBatteryControllerBlockEntity> getLoadedBatteries() {
        return LOADED_BATTERIES;
    }
}
