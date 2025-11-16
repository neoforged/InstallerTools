package net.neoforged.binarypatcher;

import org.tukaani.xz.LZMAInputStream;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static net.neoforged.binarypatcher.PatchBundleConstants.BUNDLE_SIGNATURE;
import static net.neoforged.binarypatcher.PatchBundleConstants.DISTRIBUTION_MASK;
import static net.neoforged.binarypatcher.PatchBundleConstants.ENTRY_TYPE_CREATE;
import static net.neoforged.binarypatcher.PatchBundleConstants.ENTRY_TYPE_MASK;
import static net.neoforged.binarypatcher.PatchBundleConstants.ENTRY_TYPE_MODIFY;
import static net.neoforged.binarypatcher.PatchBundleConstants.ENTRY_TYPE_REMOVE;
import static net.neoforged.binarypatcher.PatchBundleConstants.MAX_CHAR;
import static net.neoforged.binarypatcher.PatchBundleConstants.MIN_CHAR;

/**
 * Reader for binary patch bundle files.
 * Usage:
 * 1. Create reader with input stream
 * 2. Inspect getTargetDistributions() and getEntryCount()
 * 3. Iterate through entries using the iterator
 */
public class PatchBundleReader implements Iterable<Patch>, AutoCloseable {
    private final DataInputStream input;
    private final EnumSet<PatchBase> supportedBaseTypes;
    private final int entryCount;
    private int entriesRead;
    private boolean closed;

    public PatchBundleReader(File file) throws IOException {
        this(new BufferedInputStream(new FileInputStream(file)));
    }

    public PatchBundleReader(InputStream input) throws IOException {
        this.input = new DataInputStream(new LZMAInputStream(input));

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
        this.supportedBaseTypes = PatchBase.fromBitfield(distBitfield);

        this.entriesRead = 0;
        this.closed = false;
    }

    /**
     * Returns the target distributions declared in the bundle header.
     */
    public EnumSet<PatchBase> getSupportedBaseTypes() {
        return EnumSet.copyOf(supportedBaseTypes);
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
    public Iterator<Patch> iterator() {
        if (entriesRead > 0) {
            throw new IllegalStateException("Cannot create multiple iterators or iterate after manual read");
        }
        return new EntryIterator();
    }

    /**
     * Read the next entry from the bundle.
     *
     * @return the next entry, or null if all entries have been read
     */
    public Patch readEntry() throws IOException {
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

        EnumSet<PatchBase> entryDists = PatchBase.fromBitfield(distBitfield);

        // Validate entry distributions against bundle distributions
        for (PatchBase dist : entryDists) {
            if (!supportedBaseTypes.contains(dist)) {
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
        return new Patch(type, targetPath, entryDists, baseChecksum, data);
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

    private class EntryIterator implements Iterator<Patch> {
        @Override
        public boolean hasNext() {
            return hasMoreEntries();
        }

        @Override
        public Patch next() {
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
