package net.neoforged.binarypatcher;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/**
 * Package-private constants shared between PatchBundleWriter and PatchBundleReader.
 */
class PatchBundleConstants {
    // File signature
    static final byte[] BUNDLE_SIGNATURE = "NFPATCHBUNDLE001".getBytes(StandardCharsets.US_ASCII);
    
    // Entry type constants
    static final int ENTRY_TYPE_MASK = 0x18;
    static final int ENTRY_TYPE_CREATE = 0x00;
    static final int ENTRY_TYPE_MODIFY = 0x08;
    static final int ENTRY_TYPE_REMOVE = 0x10;
    
    // Distribution bitfield mask
    static final int DISTRIBUTION_MASK = 0x07;
    
    // String validation constants
    static final int MIN_CHAR = 0x20;
    static final int MAX_CHAR = 0x7E;

    // Private constructor to prevent instantiation
    private PatchBundleConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}