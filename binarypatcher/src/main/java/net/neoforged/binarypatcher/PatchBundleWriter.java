package net.neoforged.binarypatcher;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.zip.CRC32;

import static net.neoforged.binarypatcher.PatchBundleConstants.*;

/**
 * Writer for binary patch bundle files.
 * Usage:
 * 1. Create writer with output stream and target distributions
 * 2. Write entries using writeCreateEntry, writeModifyEntry, or writeRemoveEntry
 * 3. Call finish() to finalize the bundle
 */
public class PatchBundleWriter implements AutoCloseable {
    private final OutputStream output;
    private final EnumSet<TargetDistribution> bundleDistributions;
    private final ByteArrayOutputStream entryBuffer;
    private int entryCount;
    private boolean closed;
    
    public PatchBundleWriter(OutputStream output, EnumSet<TargetDistribution> bundleDistributions) {
        if (bundleDistributions.isEmpty()) {
            throw new IllegalArgumentException("Bundle must target at least one distribution");
        }
        this.output = output;
        this.bundleDistributions = bundleDistributions;
        this.entryBuffer = new ByteArrayOutputStream();
    }
    
    /**
     * Write an entry that creates a new file.
     */
    public void writeCreateEntry(String targetPath, byte[] fileContent, 
                                  EnumSet<TargetDistribution> entryDistributions) throws IOException {
        validateEntry(entryDistributions);
        int flags = ENTRY_TYPE_CREATE | TargetDistribution.toBitfield(entryDistributions);
        writeEntryInternal(flags, targetPath, 0, fileContent);
    }
    
    /**
     * Write an entry that modifies an existing file using a patch.
     */
    public void writeModifyEntry(String targetPath, long baseChecksum, byte[] patchData,
                                  EnumSet<TargetDistribution> entryDistributions) throws IOException {
        validateEntry(entryDistributions);
        if (baseChecksum < 0 || baseChecksum > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Base checksum must be a valid 32-bit unsigned value");
        }
        int flags = ENTRY_TYPE_MODIFY | TargetDistribution.toBitfield(entryDistributions);
        writeEntryInternal(flags, targetPath, baseChecksum, patchData);
    }
    
    /**
     * Write an entry that removes a file.
     */
    public void writeRemoveEntry(String targetPath, EnumSet<TargetDistribution> entryDistributions) 
            throws IOException {
        validateEntry(entryDistributions);
        int flags = ENTRY_TYPE_REMOVE | TargetDistribution.toBitfield(entryDistributions);
        writeEntryInternal(flags, targetPath, 0, new byte[0]);
    }
    
    /**
     * Calculate CRC32 checksum for a file (utility method).
     */
    public static long calculateChecksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            // Write header
            DataOutputStream dos = new DataOutputStream(output);
            dos.write(BUNDLE_SIGNATURE);
            dos.writeInt(entryCount);
            dos.writeByte(TargetDistribution.toBitfield(bundleDistributions));

            // Write all buffered entries
            entryBuffer.writeTo(output);

            closed = true;
        }
    }
    
    private void validateEntry(EnumSet<TargetDistribution> entryDistributions) {
        if (closed) {
            throw new IllegalStateException("Bundle already closed");
        }
        if (entryDistributions.isEmpty()) {
            throw new IllegalArgumentException("Entry must target at least one distribution");
        }
        for (TargetDistribution dist : entryDistributions) {
            if (!bundleDistributions.contains(dist)) {
                throw new IllegalArgumentException(
                    "Entry targets distribution " + dist + " not declared in bundle");
            }
        }
    }
    
    private void writeEntryInternal(int flags, String targetPath, long baseChecksum, byte[] data) 
            throws IOException {
        validatePath(targetPath);
        
        DataOutputStream dos = new DataOutputStream(entryBuffer);
        
        // Write entry flags
        dos.writeByte(flags);
        
        // Write target path
        writeString(dos, targetPath);
        
        // Write base checksum if this is a modify entry
        if ((flags & ENTRY_TYPE_MASK) == ENTRY_TYPE_MODIFY) {
            dos.writeInt((int) baseChecksum);
        }
        
        // Write data length and data
        dos.writeInt(data.length);
        dos.write(data);
        
        entryCount++;
    }
    
    private void writeString(DataOutputStream dos, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.US_ASCII);
        
        // Validate string
        if (bytes.length > 65535) {
            throw new IllegalArgumentException("String too long: " + str);
        }
        for (byte b : bytes) {
            if (b < MIN_CHAR || b > MAX_CHAR) {
                throw new IllegalArgumentException("String contains invalid characters: " + str);
            }
        }
        
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }
    
    private void validatePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Path contains empty segment: " + path);
            }
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Path contains . or .. segment: " + path);
            }
        }
    }
}