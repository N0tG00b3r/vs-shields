package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SymmetryMirror.class)
public class MixinSymmetryMirror {
    @Shadow
    protected Vec3 position;

    @Inject(
        method = "writeToNbt",
        at = @At("RETURN")
    )
    private void writeWithDouble(CallbackInfoReturnable<CompoundTag> cir){
        ListTag doubleList = new ListTag();
        doubleList.add(DoubleTag.m_128500_(position.f_82479_));
        doubleList.add(DoubleTag.m_128500_(position.f_82480_));
        doubleList.add(DoubleTag.m_128500_(position.f_82481_));
        cir.getReturnValue().m_128365_("vsDoublePos", doubleList);
    }

    @ModifyVariable(
        method = "fromNBT",
        at = @At(value = "STORE"),
        name = "pos"
    )
    private static Vec3 readWithDouble(Vec3 instance, @Local(argsOnly = true) CompoundTag tag){
        final ListTag doubleList = tag.m_128437_("vsDoublePos", 6);
        return new Vec3(doubleList.m_128772_(0), doubleList.m_128772_(1), doubleList.m_128772_(2));
    }
}
