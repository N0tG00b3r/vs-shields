package org.valkyrienskies.mod.mixin.feature.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(GameEventDispatcher.class)
public abstract class MixinGameEventDispatcher {
    @Shadow
    ServerLevel level;

    @Shadow
    abstract void handleGameEventMessagesInQueue(List<GameEvent.ListenerInfo> list);

    @WrapMethod(method = "post")
    private void visitShipListeners(GameEvent gameEvent, Vec3 vec3, GameEvent.Context context, Operation original) {
        int i = gameEvent.m_157827_();

        // Treat on-ship events as in-world events.
        Ship sourceShip = VSGameUtilsKt.getShipManagingPos(level, vec3);
        if (sourceShip != null) {
            vec3 = VSGameUtilsKt.toWorldCoordinates(sourceShip, vec3);
        }
        Vec3 finalVec = vec3;

        // Wrap original behavior
        original.call(gameEvent, finalVec, context);

        // Then check the ships
        BlockPos blockPos = BlockPos.m_274446_(vec3);
        AABBd sourceAABB = new AABBd(
                blockPos.m_123341_() - i, blockPos.m_123342_() - i, blockPos.m_123343_() - i,
                blockPos.m_123341_() + i, blockPos.m_123342_() + i, blockPos.m_123343_() + i
        );
        VSGameUtilsKt.getAllShips(level).stream().filter(
                ship -> ship.getWorldAABB().intersectsSphere(finalVec.f_82479_, finalVec.f_82480_, finalVec.f_82481_, Math.pow(i, 2))
        ).forEach(
                ship -> {
                    AABBic temp = ship.getShipAABB();
                    if (temp == null) return;
                    AABBd shipAABB = new AABBd(
                            temp.minX(), temp.minY(), temp.minZ(),
                            temp.maxX(), temp.maxY(), temp.maxZ()
                    );
                    // When dealing with ships in a loop, always use .transform(..., dest) instead of .transform(...)
                    // Otherwise you'll modify your out-of-loop AABBs and stuff like that!
                    AABBd intersection = shipAABB.intersection(sourceAABB.transform(ship.getWorldToShip(), new AABBd()), new AABBd());
                    BlockPos minB = BlockPos.m_274561_(intersection.minX, intersection.minY, intersection.minZ);
                    BlockPos maxB = BlockPos.m_274561_(intersection.maxX, intersection.maxY, intersection.maxZ);
                    int j = SectionPos.m_123171_(minB.m_123341_());
                    int k = SectionPos.m_123171_(minB.m_123342_());
                    int l = SectionPos.m_123171_(minB.m_123343_());
                    int m = SectionPos.m_123171_(maxB.m_123341_());
                    int n = SectionPos.m_123171_(maxB.m_123342_());
                    int o = SectionPos.m_123171_(maxB.m_123343_());

                    // Copy-paste of original code, except we iterate through ship chunks

                    List<GameEvent.ListenerInfo> list = new ArrayList<>();
                    GameEventListenerRegistry.ListenerVisitor listenerVisitor = (gameEventListener, vec32) -> {
                        if (gameEventListener.m_247514_() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                            list.add(new GameEvent.ListenerInfo(gameEvent, finalVec, context, gameEventListener, vec32));
                        } else {
                            gameEventListener.m_214068_(this.level, gameEvent, context, finalVec);
                        }

                    };
                    boolean bl = false;

                    for (int p = j; p <= m; ++p) {
                        for (int q = l; q <= o; ++q) {
                            ChunkAccess chunkAccess = this.level.m_7726_().m_7131_(p, q);
                            if (chunkAccess != null) {
                                for (int r = k; r <= n; ++r) {
                                    bl |= chunkAccess.m_246686_(r).m_245521_(gameEvent, finalVec, context, listenerVisitor);
                                }
                            }
                        }
                    }

                    if (!list.isEmpty()) {
                        this.handleGameEventMessagesInQueue(list);
                    }
                });
    }
}
