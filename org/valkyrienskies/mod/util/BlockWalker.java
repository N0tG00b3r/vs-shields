package org.valkyrienskies.mod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Modified from vanilla BlockGetter.traverseBlocks
 */
public final class BlockWalker {
    private static final double EPS = 1e-7;
    private BlockPos.MutableBlockPos nextValue = new BlockPos.MutableBlockPos();
    private final int xStep;
    private final int yStep;
    private final int zStep;
    private final double xPartialStep;
    private final double yPartialStep;
    private final double zPartialStep;
    private int x;
    private int y;
    private int z;
    private double xPartial;
    private double yPartial;
    private double zPartial;

    public BlockWalker(final Vec3 from, final Vec3 to) {
        if (from.equals(to)) {
            this.nextValue = null;
            this.xStep = 0;
            this.yStep = 0;
            this.zStep = 0;
            this.xPartialStep = 0;
            this.yPartialStep = 0;
            this.zPartialStep = 0;
            return;
        }

        final double afterToX = from.f_82479_ < to.f_82479_ ? to.f_82479_ + EPS : to.f_82479_ < from.f_82479_ ? to.f_82479_ - EPS : to.f_82479_;
        final double afterToY = from.f_82480_ < to.f_82480_ ? to.f_82480_ + EPS : to.f_82480_ < from.f_82480_ ? to.f_82480_ - EPS : to.f_82480_;
        final double afterToZ = from.f_82481_ < to.f_82481_ ? to.f_82481_ + EPS : to.f_82481_ < from.f_82481_ ? to.f_82481_ - EPS : to.f_82481_;
        final double beforeFromX = from.f_82479_ < to.f_82479_ ? from.f_82479_ - EPS : to.f_82479_ < from.f_82479_ ? from.f_82479_ + EPS : from.f_82479_;
        final double beforeFromY = from.f_82480_ < to.f_82480_ ? from.f_82480_ - EPS : to.f_82480_ < from.f_82480_ ? from.f_82480_ + EPS : from.f_82480_;
        final double beforeFromZ = from.f_82481_ < to.f_82481_ ? from.f_82481_ - EPS : to.f_82481_ < from.f_82481_ ? from.f_82481_ + EPS : from.f_82481_;
        this.x = Mth.m_14107_(from.f_82479_);
        this.y = Mth.m_14107_(from.f_82480_);
        this.z = Mth.m_14107_(from.f_82481_);
        final double xDiff = afterToX - beforeFromX;
        final double yDiff = afterToY - beforeFromY;
        final double zDiff = afterToZ - beforeFromZ;
        this.xStep = Mth.m_14205_(xDiff);
        this.yStep = Mth.m_14205_(yDiff);
        this.zStep = Mth.m_14205_(zDiff);
        this.xPartialStep = this.xStep == 0 ? Double.MAX_VALUE : (double) (this.xStep) / xDiff;
        this.yPartialStep = this.yStep == 0 ? Double.MAX_VALUE : (double) (this.yStep) / yDiff;
        this.zPartialStep = this.zStep == 0 ? Double.MAX_VALUE : (double) (this.zStep) / zDiff;
        this.xPartial = this.xPartialStep * (this.xStep > 0 ? 1 - Mth.m_14185_(beforeFromX) : Mth.m_14185_(beforeFromX));
        this.yPartial = this.yPartialStep * (this.yStep > 0 ? 1 - Mth.m_14185_(beforeFromY) : Mth.m_14185_(beforeFromY));
        this.zPartial = this.zPartialStep * (this.zStep > 0 ? 1 - Mth.m_14185_(beforeFromZ) : Mth.m_14185_(beforeFromZ));
    }

    public BlockPos value() {
        return this.nextValue == null ? null : this.nextValue.m_122178_(this.x, this.y, this.z);
    }

    public boolean next() {
        if (this.nextValue == null) {
            return false;
        }
        if (this.xPartial > 1 && this.yPartial > 1 && this.zPartial > 1) {
            this.nextValue = null;
            return false;
        }
        if (this.xPartial < this.yPartial) {
            if (this.xPartial < this.zPartial) {
                this.x += this.xStep;
                this.xPartial += this.xPartialStep;
            } else {
                this.z += this.zStep;
                this.zPartial += this.zPartialStep;
            }
        } else if (this.yPartial < this.zPartial) {
            this.y += this.yStep;
            this.yPartial += this.yPartialStep;
        } else {
            this.z += this.zStep;
            this.zPartial += this.zPartialStep;
        }
        return true;
    }
}
