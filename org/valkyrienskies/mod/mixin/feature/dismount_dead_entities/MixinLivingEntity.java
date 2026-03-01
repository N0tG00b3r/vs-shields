package org.valkyrienskies.mod.mixin.feature.dismount_dead_entities;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {
    @Unique
    private static final Logger VS$LOGGER = LogManager.getLogger();

    public MixinLivingEntity(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    /**
     * Fix dismounting dead chairs on ships teleporting entities into the sky
     */
    @Inject(method = "dismountVehicle", at = @At("HEAD"), cancellable = true)
    private void preDismountVehicle(final Entity entity, final CallbackInfo ci) {
        if ((!this.m_213877_() && entity.m_213877_()) || (this.m_9236_().m_46749_(entity.m_20183_()) && this.m_9236_().m_8055_(entity.m_20183_()).m_204336_(BlockTags.f_13075_))) {
            if (VSGameUtilsKt.isBlockInShipyard(m_9236_(), entity.m_20182_())) {
                final Ship ship = VSGameUtilsKt.getShipManagingPos(m_9236_(), entity.m_20182_());
                if (ship != null) {
                    final Vector3dc transformedPos = ship.getTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(entity.m_20182_()));
                    final double d = Math.max(this.m_20186_(), transformedPos.y());
                    final Vec3 vec3 = new Vec3(this.m_20185_(), d, this.m_20189_());
                    this.m_142098_(vec3.f_82479_, vec3.f_82480_, vec3.f_82481_);
                    ci.cancel();
                } else {
                    // We're in the shipyard but no ship?
                    VS$LOGGER.debug("Modifying strange dismount");
                    final Vec3 vec3 = new Vec3(this.m_20185_(), this.m_20186_(), this.m_20189_());
                    this.m_142098_(vec3.f_82479_, vec3.f_82480_, vec3.f_82481_);
                    ci.cancel();
                }
            }
        }
    }
}
