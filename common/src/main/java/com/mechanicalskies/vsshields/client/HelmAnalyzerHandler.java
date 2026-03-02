package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.item.TacticalGogglesItem;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/**
 * Handles the Ship Analyzer keybind for the Tactical Goggles.
 * Toggle mode: press once to activate, press again to deactivate.
 * Tick this from VSShieldsModClient CLIENT_POST event (client-side only).
 */
public class HelmAnalyzerHandler {

    /** Default key: Y (GLFW_KEY_Y = 89). Category: VS Energy Shields. */
    public static final KeyMapping SCAN_KEY = new KeyMapping(
            "key.vs_shields.helm_scan",
            InputConstants.Type.KEYSYM,
            89,  // GLFW_KEY_Y
            "key.categories.vs_shields"
    );

    /** Set from forge module to check Curios head slot for TacticalGoggles. */
    private static Predicate<Player> gogglesChecker = p -> false;

    public static void setGogglesChecker(Predicate<Player> checker) {
        gogglesChecker = checker;
    }

    private static boolean isActive   = false;
    private static boolean keyWasDown = false;
    private static int     cooldown   = 0;

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        // Must be wearing goggles in helmet slot OR goggles in Curios head slot
        ItemStack helmet = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean wearingGogglesArmor = !helmet.isEmpty() && helmet.getItem() instanceof TacticalGogglesItem;
        boolean wearingGogglesCurios = gogglesChecker.test(mc.player);

        if (!wearingGogglesArmor && !wearingGogglesCurios) {
            if (isActive) {
                ClientAnalyzerData.getInstance().clear();
                isActive = false;
            }
            keyWasDown = false;
            cooldown = 0;
            return;
        }

        boolean keyDown = SCAN_KEY.isDown();

        // Toggle on rising edge (first tick of press only)
        if (keyDown && !keyWasDown) {
            isActive = !isActive;
            if (!isActive) {
                ClientAnalyzerData.getInstance().clear();
            } else {
                // Send immediately on activation
                ModNetwork.sendAnalyzerScan(mc.player.getEyePosition(), mc.player.getLookAngle());
                cooldown = 0;
            }
        }
        keyWasDown = keyDown;

        // Refresh scan data every 10 ticks while active
        if (isActive) {
            cooldown++;
            if (cooldown >= 10) {
                cooldown = 0;
                ModNetwork.sendAnalyzerScan(mc.player.getEyePosition(), mc.player.getLookAngle());
            }
        }
    }

    /** True while the scanner is toggled on AND valid scan data exists. */
    public static boolean isScanning() {
        return isActive && ClientAnalyzerData.getInstance().isValid();
    }

    /** Raw toggle state — for renderers that need it even before data arrives. */
    public static boolean isActive() {
        return isActive;
    }
}
