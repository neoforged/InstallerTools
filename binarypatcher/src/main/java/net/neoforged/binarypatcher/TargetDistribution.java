package net.neoforged.binarypatcher;

import java.util.EnumSet;

/**
 * Target distribution enumeration used by both reader and writer.
 */
public enum TargetDistribution {
    CLIENT(0x01),
    SERVER(0x02),
    JOINED(0x04);

    private final int mask;

    TargetDistribution(int mask) {
        this.mask = mask;
    }

    int getMask() {
        return mask;
    }

    static int toBitfield(EnumSet<TargetDistribution> distributions) {
        int result = 0;
        for (TargetDistribution dist : distributions) {
            result |= dist.mask;
        }
        return result;
    }

    static EnumSet<TargetDistribution> fromBitfield(int bitfield) {
        EnumSet<TargetDistribution> result = EnumSet.noneOf(TargetDistribution.class);
        for (TargetDistribution dist : values()) {
            if ((bitfield & dist.mask) != 0) {
                result.add(dist);
            }
        }
        return result;
    }
}
