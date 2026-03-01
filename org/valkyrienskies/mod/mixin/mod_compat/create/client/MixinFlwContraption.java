package org.valkyrienskies.mod.mixin.mod_compat.create.client;


import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ContraptionMatrices.class)
public abstract class MixinFlwContraption {


    @Inject(at = @At("HEAD"), method = "translateToEntity", cancellable = true, remap = false)
    private static void beforeSetupModelViewPartial(Matrix4f matrix, Entity entity, float pt, CallbackInfo ci) {

        if (VSGameUtilsKt.getShipManaging(entity) instanceof final ClientShip ship) {
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(),
                matrix,
                Mth.m_14139_(pt, entity.f_19790_, entity.m_20185_()),
                Mth.m_14139_(pt, entity.f_19791_, entity.m_20186_()),
                Mth.m_14139_(pt, entity.f_19792_, entity.m_20189_()),
                // TODO: make sure this fix worked
                matrix.get(3,0),
                matrix.get(3,1),
                matrix.get(3,2)
            );

            ci.cancel();
        }
    }
}

