package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Contraption.class)
public class MixinContraption {
    @Redirect(method = "onEntityCreated", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean wrapOp(Level level, Entity entity) {
        // BlockPos anchor = blockFace.getConnectedPos();
        // movedContraption.setPos(anchor.getX() + .5f, anchor.getY(), anchor.getZ() + .5f);
        //
        // Derive anchor from the code above
        final BlockPos anchor = BlockPos.m_274561_((int) Math.floor(entity.m_20185_()), (int) Math.floor(entity.m_20186_()), (int) Math.floor(entity.m_20189_()));
        boolean added = level.m_7967_(entity);
        if (added) {
            entity.m_6027_(anchor.m_123341_() + .5, anchor.m_123342_(), anchor.m_123343_() + .5);
        }
        return added;
    }
}
