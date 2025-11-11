package net.neoforged.binarypatcher;

import java.util.EnumSet;

/**
 * Target distribution enumeration used by both reader and writer.
 */
public enum PatchBase {
    CLIENT(0x01),
    SERVER(0x02),
    JOINED(0x04);

    private final int mask;

    PatchBase(int mask) {
        this.mask = mask;
    }

    int getMask() {
        return mask;
    }

    static int toBitfield(EnumSet<PatchBase> distributions) {
        int result = 0;
        for (PatchBase dist : distributions) {
            result |= dist.mask;
        }
        return result;
    }

    static EnumSet<PatchBase> fromBitfield(int bitfield) {
        EnumSet<PatchBase> result = EnumSet.noneOf(PatchBase.class);
        for (PatchBase dist : values()) {
            if ((bitfield & dist.mask) != 0) {
                result.add(dist);
            }
        }
        return result;
    }
}
