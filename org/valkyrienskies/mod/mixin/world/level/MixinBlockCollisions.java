package org.valkyrienskies.mod.mixin.world.level;

import java.util.function.BiFunction;
import net.minecraft.core.Cursor3D;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.util.BugFixUtil;

/**
 * Fix game freezing when a too-large AABB is used in a BlockCollisions object
 */
@Mixin(BlockCollisions.class)
public class MixinBlockCollisions {
    @Shadow
    @Final
    @Mutable
    private AABB box;
    @Shadow
    @Final
    @Mutable
    private Cursor3D cursor;
    @Shadow
    @Final
    @Mutable
    private VoxelShape entityShape;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void postInit(CollisionGetter collisionGetter, Entity entity, AABB aabb, boolean bl, BiFunction biFunction, CallbackInfo ci) {
        if (BugFixUtil.INSTANCE.isCollisionBoxTooBig(aabb)) {
            final AABB newBox = new AABB(aabb.f_82288_, aabb.f_82289_, aabb.f_82290_, aabb.f_82288_, aabb.f_82289_, aabb.f_82290_);
            this.entityShape = Shapes.m_83064_(newBox);
            this.box = newBox;
            final int i = Mth.m_14107_(newBox.f_82288_ - 1.0E-7) - 1;
            final int j = Mth.m_14107_(newBox.f_82291_ + 1.0E-7) + 1;
            final int k = Mth.m_14107_(newBox.f_82289_ - 1.0E-7) - 1;
            final int l = Mth.m_14107_(newBox.f_82292_ + 1.0E-7) + 1;
            final int m = Mth.m_14107_(newBox.f_82290_ - 1.0E-7) - 1;
            final int n = Mth.m_14107_(newBox.f_82293_ + 1.0E-7) + 1;
            this.cursor = new Cursor3D(i, k, m, j, l, n);
        }
    }
}
