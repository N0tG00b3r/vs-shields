package org.valkyrienskies.mod.mixin.feature.render_pathfinding;

import io.netty.buffer.Unpooled;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Target;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder.PathAccessor;

@Mixin(DebugPackets.class)
public class MixinDebugPackets {

    @Inject(method = "sendPathFindingPacket", at = @At("HEAD"))
    private static void sendPathFindingPacket(
        final Level level,
        final Mob mob,
        final Path path,
        final float maxDistanceToWaypoint,
        final CallbackInfo ci
    ) {
        if (path == null || level.f_46443_ || !VSGameConfig.COMMON.ADVANCED.getRenderPathfinding()) {
            return;
        }

        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        buf.writeInt(mob.m_19879_());
        buf.writeFloat(maxDistanceToWaypoint);
        writePath(buf, path);

        final ClientboundCustomPayloadPacket lv =
            new ClientboundCustomPayloadPacket(ClientboundCustomPayloadPacket.f_132013_, buf);
        for (final Player player : level.m_6907_()) {
            ((ServerPlayer) player).f_8906_.m_9829_(lv);
        }
    }

    private static void writePath(final FriendlyByteBuf buffer, final Path path) {
        buffer.writeBoolean(path.m_77403_());
        buffer.writeInt(path.m_77399_());

        final Set<Target> targetSet = ((PathAccessor) path).getTargetNodes();
        if (targetSet != null) {
            buffer.writeInt(targetSet.size());

            targetSet.forEach((node) -> {
                writeNode(buffer, node);
            });
        } else {
            buffer.writeInt(0);
        }

        buffer.writeInt(path.m_77406_().m_123341_());
        buffer.writeInt(path.m_77406_().m_123342_());
        buffer.writeInt(path.m_77406_().m_123343_());

        buffer.writeInt(path.m_77398_());

        for (int i = 0; i < path.m_77398_(); ++i) {
            final Node node = path.m_77375_(i);
            writeNode(buffer, node);
        }

        buffer.writeInt(0);
        /*
        buffer.writeInt(path.debugNodes.length);
        PathNode[] var6 = path.debugNodes;
        int var7 = var6.length;

        int var4;
        PathNode pathNode2;
        for (var4 = 0; var4 < var7; ++var4) {
            pathNode2 = var6[var4];
            pathNode2.toBuffer(buffer);
        }
         */

        buffer.writeInt(0);
        /*
        var6 = path.debugSecondNodes;
        var7 = var6.length;

        for (var4 = 0; var4 < var7; ++var4) {
            pathNode2 = var6[var4];
            pathNode2.toBuffer(buffer);
        }
        */
    }

    private static void writeNode(final FriendlyByteBuf buffer, final Node node) {
        buffer.writeInt(node.f_77271_);
        buffer.writeInt(node.f_77272_);
        buffer.writeInt(node.f_77273_);
        buffer.writeFloat(node.f_77280_);
        buffer.writeFloat(node.f_77281_);
        buffer.writeBoolean(node.f_77279_);
        buffer.writeInt(node.f_77282_.ordinal());
        buffer.writeFloat(node.f_77277_);
    }
}
