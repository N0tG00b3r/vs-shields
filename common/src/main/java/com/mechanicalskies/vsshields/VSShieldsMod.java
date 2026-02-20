package com.mechanicalskies.vsshields;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModBlocks;
import com.mechanicalskies.vsshields.registry.ModCreativeTabs;
import com.mechanicalskies.vsshields.registry.ModItems;
import com.mechanicalskies.vsshields.registry.ModMenus;

import java.nio.file.Path;

public class VSShieldsMod {
    public static final String MOD_ID = "vs_shields";

    public static void init() {
        ModBlocks.register();
        ModItems.register();
        ModBlockEntities.register();
        ModCreativeTabs.register();
        ModMenus.register();
        ModNetwork.init();
    }

    public static void loadConfig(Path gameDir) {
        ShieldConfig.load(gameDir);
    }
}
