package org.valkyrienskies.mod.forge.mixin.compat.tfcambiental;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = "com.lumintorious.tfcambiental.api.EnvironmentalTemperatureProvider")
public interface MixinEnvironmentalTemperatureProvider {

    // Use an Overwrite because mixin in forge doesn't support injecting into interfaces (?!)
    @Overwrite(remap = false)
    static boolean calculateEnclosure(final Player player, final int radius) {
        // VS: Use player.blockPosition() instead of getOnPos() if getOnPos() is in a ship.
        BlockPos pos = player.m_20097_();
        if (VSGameUtilsKt.isBlockInShipyard(player.m_9236_(), pos)) {
            pos = player.m_20183_();
        }

        // Original method
        final PathNavigationRegion
            region = new PathNavigationRegion(player.m_9236_(), pos.m_7494_().m_7918_(-radius, -radius, -radius),
            pos.m_7494_().m_7918_(radius, 400, radius));
        final Bee guineaPig = new Bee(EntityType.f_20550_, player.m_9236_());
        guineaPig.m_146884_(player.m_20318_(0.0F));
        guineaPig.m_6863_(true);
        final FlyNodeEvaluator evaluator = new FlyNodeEvaluator();
        final PathFinder finder = new PathFinder(evaluator, 500);
        final Path path = finder.m_77427_(region, guineaPig, Set.of(pos.m_7494_().m_175288_(258)), 500.0F, 0, 12.0F);
        final boolean isIndoors = path == null || path.m_77398_() < 255 - pos.m_7494_().m_123342_();
        return isIndoors;
    }

}
