package net.neoforged.binarypatcher;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static net.neoforged.binarypatcher.PatchBundleConstants.*;

/**
 * Reader for binary patch bundle files.
 * Usage:
 * 1. Create reader with input stream
 * 2. Inspect getTargetDistributions() and getEntryCount()
 * 3. Iterate through entries using the iterator
 */
public class PatchBundleReader implements Iterable<PatchBundleReader.Entry>, AutoCloseable {
    private final DataInputStream input;
    private final EnumSet<TargetDistribution> bundleDistributions;
    private final int entryCount;
    private int entriesRead;
    private boolean closed;

    /**
     * Represents a single entry in the patch bundle.
     */
    public static class Entry {
        private final PatchOperation type;
        private final String targetPath;
        private final EnumSet<TargetDistribution> distributions;
        private final Long baseChecksum; // null for non-modify entries
        private final byte[] data;

        Entry(PatchOperation type, String targetPath, EnumSet<TargetDistribution> distributions,
              Long baseChecksum, byte[] data) {
            this.type = type;
            this.targetPath = targetPath;
            this.distributions = distributions;
            this.baseChecksum = baseChecksum;
            this.data = data;
        }

        public PatchOperation getType() {
            return type;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public EnumSet<TargetDistribution> getDistributions() {
            return distributions;
        }

        /**
         * Returns the base checksum for MODIFY entries, null otherwise.
         */
        public Long getBaseChecksum() {
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
            if (baseChecksum == null) {
                throw new IllegalStateException("Base checksum not available for " + type + " entries");
            }
            return baseChecksum & 0xFFFFFFFFL;
        }
    }

    public PatchBundleReader(InputStream input) throws IOException {
        this.input = new DataInputStream(input);

        // Read and validate signature
        byte[] signature = new byte[BUNDLE_SIGNATURE.length];
        this.input.readFully(signature);
        if (!java.util.Arrays.equals(signature, BUNDLE_SIGNATURE)) {
            throw new IOException("Invalid bundle signature");
        }

        // Read entry count
        this.entryCount = this.input.readInt();
        if (this.entryCount < 0) {
            throw new IOException("Invalid entry count: " + this.entryCount);
        }

        // Read target distributions
        int distBitfield = this.input.readUnsignedByte();
        this.bundleDistributions = TargetDistribution.fromBitfield(distBitfield);

        this.entriesRead = 0;
        this.closed = false;
    }

    /**
     * Returns the target distributions declared in the bundle header.
     */
    public EnumSet<TargetDistribution> getTargetDistributions() {
        return EnumSet.copyOf(bundleDistributions);
    }

    /**
     * Returns the total number of entries in the bundle.
     */
    public int getEntryCount() {
        return entryCount;
    }

    /**
     * Returns the number of entries that have been read so far.
     */
    public int getEntriesRead() {
        return entriesRead;
    }

    /**
     * Returns true if all entries have been read.
     */
    public boolean hasMoreEntries() {
        return entriesRead < entryCount;
    }

    @Override
    public Iterator<Entry> iterator() {
        if (entriesRead > 0) {
            throw new IllegalStateException("Cannot create multiple iterators or iterate after manual read");
        }
        return new EntryIterator();
    }

    /**
     * Read the next entry from the bundle.
     * @return the next entry, or null if all entries have been read
     */
    public Entry readEntry() throws IOException {
        if (closed) {
            throw new IllegalStateException("Reader is closed");
        }
        if (!hasMoreEntries()) {
            return null;
        }

        // Read entry flags
        int flags = input.readUnsignedByte();
        int entryTypeBits = flags & ENTRY_TYPE_MASK;
        int distBitfield = flags & DISTRIBUTION_MASK;

        PatchOperation type;
        switch (entryTypeBits) {
            case ENTRY_TYPE_CREATE:
                type = PatchOperation.CREATE;
                break;
            case ENTRY_TYPE_MODIFY:
                type = PatchOperation.MODIFY;
                break;
            case ENTRY_TYPE_REMOVE:
                type = PatchOperation.REMOVE;
                break;
            default:
                throw new IOException("Unknown entry type: " + entryTypeBits);
        }

        EnumSet<TargetDistribution> entryDists = TargetDistribution.fromBitfield(distBitfield);

        // Validate entry distributions against bundle distributions
        for (TargetDistribution dist : entryDists) {
            if (!bundleDistributions.contains(dist)) {
                throw new IOException("Entry targets distribution " + dist +
                        " not declared in bundle");
            }
        }

        // Read target path
        String targetPath = readString();

        // Read base checksum if this is a modify entry
        Long baseChecksum = null;
        if (type == PatchOperation.MODIFY) {
            baseChecksum = (long) input.readInt();
        }

        // Read data length and data
        int dataLength = input.readInt();
        if (dataLength < 0) {
            throw new IOException("Invalid data length: " + dataLength);
        }
        if (type == PatchOperation.REMOVE && dataLength != 0) {
            throw new IOException("REMOVE entry must have data length of 0");
        }

        byte[] data = new byte[dataLength];
        if (dataLength > 0) {
            input.readFully(data);
        }

        entriesRead++;
        return new Entry(type, targetPath, entryDists, baseChecksum, data);
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            input.close();
            closed = true;
        }
    }

    private String readString() throws IOException {
        int length = input.readUnsignedShort();
        byte[] bytes = new byte[length];
        input.readFully(bytes);

        // Validate characters
        for (byte b : bytes) {
            if (b < MIN_CHAR || b > MAX_CHAR) {
                throw new IOException("String contains invalid character: 0x" +
                        Integer.toHexString(b & 0xFF));
            }
        }

        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private class EntryIterator implements Iterator<Entry> {
        @Override
        public boolean hasNext() {
            return hasMoreEntries();
        }

        @Override
        public Entry next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more entries");
            }
            try {
                return readEntry();
            } catch (IOException e) {
                throw new UncheckedIOException("Error reading entry", e);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported");
        }
    }
}
