package net.neoforged.binarypatcher;

import com.nothome.delta.Delta;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.Adler32;

/**
 * Represents a single entry in the patch bundle.
 */
public class Patch {
    private final PatchOperation operation;
    private final String targetPath;
    private final EnumSet<PatchBase> baseTypes;
    private final long baseChecksum; // -1 for non-modify entries
    private final byte[] data;

    Patch(PatchOperation operation, String targetPath, EnumSet<PatchBase> baseTypes,
          Long baseChecksum, byte[] data) {
        this.operation = operation;
        this.targetPath = targetPath;
        this.baseTypes = baseTypes;
        if (operation == PatchOperation.MODIFY) {
            this.baseChecksum = Objects.requireNonNull(baseChecksum, "baseChecksum");
        } else {
            this.baseChecksum = -1;
        }
        this.data = data;
    }

    public PatchOperation getOperation() {
        return operation;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public EnumSet<PatchBase> getBaseTypes() {
        return baseTypes;
    }

    /**
     * Checksum for the base file of the patch.
     * <p>
     * Only available for entries where {@link #getOperation()} is {@link PatchOperation#MODIFY}.
     * Otherwise, the value is -1.
     * </p>
     *
     * @return The base files checksum, or -1 if not available.
     */
    public long getBaseChecksum() {
        return baseChecksum;
    }

    /**
     * Returns the entry data. For CREATE entries, this is the file content.
     * For MODIFY entries, this is the xdelta patch data.
     * For REMOVE entries, this is an empty array.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Returns the base checksum as an unsigned long value.
     * Only valid for MODIFY entries.
     */
    public long getBaseChecksumUnsigned() {
        if (baseChecksum == -1) {
            throw new IllegalStateException("Base checksum not available for " + operation + " entries");
        }
        return baseChecksum & 0xFFFFFFFFL;
    }

    /**
     * @param baseData    Null values indicate that the patch base did not contain the target path.
     * @param patchedData Null indicates the target path has been removed from the patched jar.
     */
    public static void from(String targetPath,
                            EnumMap<PatchBase, byte[]> baseData,
                            byte @Nullable [] patchedData,
                            DiffOptions options,
                            Consumer<Patch> consumer) throws IOException {

        // Find all bases that contained the file
        EnumSet<PatchBase> basesWithFile = EnumSet.noneOf(PatchBase.class);
        for (Map.Entry<PatchBase, byte[]> entry : baseData.entrySet()) {
            if (entry.getValue() != null) {
                basesWithFile.add(entry.getKey());
            }
        }

        // If the file was removed, emit a patch record to remove it from distributions that had it
        if (patchedData == null) {
            if (!basesWithFile.isEmpty()) {
                consumer.accept(new Patch(
                        PatchOperation.REMOVE,
                        targetPath,
                        basesWithFile,
                        null,
                        null
                ));
            }
            return;
        }

        // If the file wasn't removed, emit a CREATE patch for all bases that did not contain the file
        EnumSet<PatchBase> basesWithoutFile = EnumSet.copyOf(baseData.keySet());
        basesWithoutFile.removeAll(basesWithFile);
        if (!basesWithoutFile.isEmpty()) {
            consumer.accept(new Patch(
                    PatchOperation.CREATE,
                    targetPath,
                    basesWithoutFile,
                    null,
                    patchedData
            ));
        }

        if (basesWithFile.isEmpty()) {
            return; // No need to start diffing
        }

        // Otherwise, create deltas for all bases, then group by the created patch content and emit grouped
        Collection<List<Map.Entry<PatchBase, byte[]>>> baseDataGroups = baseData
                .entrySet()
                .stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.groupingBy(e -> hashContent(e.getValue())))
                .values();

        for (List<Map.Entry<PatchBase, byte[]>> baseDataGroup : baseDataGroups) {
            // The content in the group is identical
            byte[] base = baseDataGroup.get(0).getValue();

            // Optimize the patch data if applicable
            byte[] actualPatchData = patchedData;
            if (options.isOptimizeConstantPool() && targetPath.endsWith(".class")) {
                actualPatchData = shrinkDirtyForPatch(base, actualPatchData);
            }

            // Collect which bases this group comes from
            EnumSet<PatchBase> baseTypes = EnumSet.noneOf(PatchBase.class);
            for (Map.Entry<PatchBase, byte[]> groupEntry : baseDataGroup) {
                baseTypes.add(groupEntry.getKey());
            }

            byte[] patchData = new Delta().compute(base, actualPatchData);
            long checksum = checksum(base);
            consumer.accept(new Patch(
                    PatchOperation.MODIFY,
                    targetPath,
                    baseTypes,
                    checksum,
                    patchData
            ));
        }
    }

    public static Patch createAdd(String targetPath,
                                  byte[] patchedData,
                                  EnumSet<PatchBase> bases) {
        return new Patch(
                PatchOperation.CREATE,
                targetPath,
                bases,
                null,
                patchedData
        );
    }

    public static Patch createRemove(String targetPath, EnumSet<PatchBase> bases) {
        return new Patch(
                PatchOperation.REMOVE,
                targetPath,
                bases,
                null,
                null
        );
    }

    public static Patch createModified(String targetPath,
                                       byte[] baseData,
                                       byte[] patchedData,
                                       EnumSet<PatchBase> bases,
                                       DiffOptions options) throws IOException {
        // Optimize the patch data if applicable
        byte[] actualPatchData = patchedData;
        if (options.isOptimizeConstantPool() && targetPath.endsWith(".class")) {
            actualPatchData = shrinkDirtyForPatch(baseData, actualPatchData);
        }

        byte[] patchData = new Delta().compute(baseData, actualPatchData);
        long checksum = checksum(baseData);
        return new Patch(
                PatchOperation.MODIFY,
                targetPath,
                bases,
                checksum,
                patchData
        );
    }

    private static String hashContent(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e); // Standard JCA algorithm is missing
        }
    }

    private static byte[] shrinkDirtyForPatch(byte[] clean, byte[] dirty) {
        if (clean.length == 0 || dirty.length == 0) {
            return dirty;
        }
        ClassReader cleanReader = new ClassReader(clean);
        ClassReader dirtyReader = new ClassReader(dirty);
        ClassWriter writer = new ClassWriter(cleanReader, 0);
        dirtyReader.accept(writer, 0);
        return writer.toByteArray();
    }

    public static long checksum(byte[] input) {
        Adler32 hasher = new Adler32();
        hasher.update(input);
        return hasher.getValue();
    }
}
