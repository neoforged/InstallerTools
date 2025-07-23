package net.neoforged.jartransform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class CompressedEntry {
    private final long crc32;
    private final byte[] compressedData;
    private final long uncompressedSize;

    public CompressedEntry(long crc32, byte[] compressedData, long uncompressedSize) {
        if (crc32 < 0) {
            throw new IllegalArgumentException("Invalid crc32: " + crc32);
        }
        if (uncompressedSize < 0) {
            throw new IllegalArgumentException("Invalid uncompressed size: " + uncompressedSize);
        }
        this.crc32 = crc32;
        this.compressedData = compressedData;
        this.uncompressedSize = uncompressedSize;
    }

    public long getCrc32() {
        return crc32;
    }

    public byte[] getCompressedData() {
        return compressedData;
    }

    public long getUncompressedSize() {
        return uncompressedSize;
    }

    public static CompressedEntry compress(byte[] uncompressedData) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length);
        CRC32 crc32 = new CRC32();
        try (OutputStream out = new CheckedOutputStream(new DeflaterOutputStream(bos, deflater), crc32)) {
            out.write(uncompressedData);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compress data.", e);
        }

        return new CompressedEntry(crc32.getValue(), bos.toByteArray(), uncompressedData.length);
    }
}
