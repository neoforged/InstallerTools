package net.neoforged.jartransform;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ZipOutput implements AutoCloseable {
    private final ZipArchiveOutputStream outputStream;

    public ZipOutput(File outputFile) throws IOException {
        outputStream = new ZipArchiveOutputStream(outputFile);
    }

    /**
     * Adds an entry to the zip file with data that has already been compressed with the deflate algorithm.
     * This is useful in scenarios where you'd want to offload the deflate compression to worker threads,
     * but write the ZIP in a single final thread.
     */
    public void addPrecompressedEntry(ZipTransformEntry entry, int crc32, byte[] precompressedData, long uncompressedSize) throws IOException {
        ZipArchiveEntry archiveEntry = new ZipArchiveEntry(entry.entry);
        archiveEntry.setCrc(crc32);
        archiveEntry.setCompressedSize(precompressedData.length);
        archiveEntry.setSize(uncompressedSize);
        archiveEntry.setMethod(ZipArchiveEntry.DEFLATED);

        outputStream.addRawArchiveEntry(archiveEntry, new ByteArrayInputStream(precompressedData));
    }

    public void addArchiveEntry(ZipTransformEntry entry, InputStream stream) throws IOException {
        outputStream.putArchiveEntry(entry.entry);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer, 0, buffer.length)) >= 0) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.closeArchiveEntry();
    }

    /**
     * Puts a new entry for data that will be written in uncompressed form. Call {@link #getOutputStream} to
     * get the stream to write to, and after you're done, call {@link #closeArchiveEntry()}.
     */
    public void putArchiveEntry(ZipTransformEntry entry) throws IOException {
        outputStream.putArchiveEntry(entry.entry);
    }

    /**
     * @return the output stream for writing <strong>uncompressed</strong> data to after calling {@link #putArchiveEntry}.
     * Do not close this stream.
     */
    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void closeArchiveEntry() throws IOException {
        outputStream.closeArchiveEntry();
    }

    public void addRawArchiveEntry(ZipTransformEntry entry, InputStream rawStream) throws IOException {
        outputStream.addRawArchiveEntry(entry.entry, rawStream);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
