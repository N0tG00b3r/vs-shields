package com.mechanicalskies.vsshields.item;

import com.mechanicalskies.vsshields.anomaly.ClientAnomalyData;
import com.mechanicalskies.vsshields.config.ShieldConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Aetheric Compass — points toward active anomaly.
 * 3 states: searching (no anomaly), signal (>chaosRadius), interference (≤chaosRadius).
 * The needle angle is driven by a ClampedItemPropertyFunction registered in VSShieldsModClient.
 */
public class AethericCompassItem extends Item {

    public AethericCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (level == null || !level.isClientSide()) return;

        if (!ClientAnomalyData.exists()) {
            tooltip.add(Component.translatable("item.vs_shields.aetheric_compass.searching")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        double dx = ClientAnomalyData.getWorldX() - mc.player.getX();
        double dz = ClientAnomalyData.getWorldZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int chaosRadius = ShieldConfig.get().getAnomaly().compassChaosRadius;

        if (dist <= chaosRadius) {
            tooltip.add(Component.translatable("item.vs_shields.aetheric_compass.interference")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else {
            tooltip.add(Component.translatable("item.vs_shields.aetheric_compass.signal")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    /**
     * Compute the compass needle angle (0.0-1.0) for the item model property.
     * Called from client-side ClampedItemPropertyFunction.
     */
    public static float computeAngle(Level level, net.minecraft.world.entity.LivingEntity entity, ItemStack stack) {
        if (level == null || entity == null) return 0f;

        long gameTick = level.getGameTime();

        if (!ClientAnomalyData.exists()) {
            // No anomaly → slow steady spin
            return (float) ((gameTick * 3L) % 360L) / 360f;
        }

        double dx = ClientAnomalyData.getWorldX() - entity.getX();
        double dz = ClientAnomalyData.getWorldZ() - entity.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        int chaosRadius = ShieldConfig.get().getAnomaly().compassChaosRadius;

        if (dist <= chaosRadius) {
            // Interference zone → wild erratic spin
            double noise = Math.sin(gameTick * 0.7) * 47 + Math.cos(gameTick * 1.3) * 31;
            return (float) (((gameTick * 17L + (long) noise) % 360L + 360L) % 360L) / 360f;
        }

        // Wobble zone: compass oscillates increasingly as player approaches chaos radius
        // Wobble starts at 2× chaosRadius and intensifies toward chaosRadius boundary
        double wobbleStartDist = chaosRadius * 2.0;
        double wobbleAngle = 0;
        if (dist < wobbleStartDist) {
            // 0 at wobbleStartDist, 1 at chaosRadius
            double wobbleIntensity = 1.0 - (dist - chaosRadius) / (wobbleStartDist - chaosRadius);
            wobbleIntensity = wobbleIntensity * wobbleIntensity; // ease-in (quadratic)
            // Multi-frequency oscillation: max ±30° at boundary (60° total spread)
            double maxDeg = 30.0 * wobbleIntensity;
            wobbleAngle = Math.toRadians(maxDeg * (
                    Math.sin(gameTick * 0.4) * 0.6 + Math.sin(gameTick * 1.1) * 0.4));
        }

        // Normal: point toward anomaly
        double targetAngle = Math.atan2(dz, dx); // radians, east=0
        double playerAngle = Math.toRadians(entity.getYRot()); // yaw: south=0, west=90

        // Convert MC yaw to atan2(dz,dx) angle: south(yaw=0)→π/2, east(yaw=-90)→0
        double playerMathAngle = playerAngle + Math.PI / 2;

        double relAngle = targetAngle - playerMathAngle + wobbleAngle;
        // Normalize to 0..2π
        relAngle = ((relAngle % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);

        return (float) (relAngle / (Math.PI * 2));
    }
}
