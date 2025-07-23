package net.neoforged.jartransform;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;

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
    public void addCompressedEntry(String name, FileTime lastModified, CompressedEntry compressedEntry) throws IOException {
        ZipArchiveEntry archiveEntry = new ZipArchiveEntry(name);
        if (lastModified != null) {
            archiveEntry.setLastModifiedTime(lastModified);
        }
        archiveEntry.setCrc(compressedEntry.getCrc32());
        byte[] compressedData = compressedEntry.getCompressedData();
        archiveEntry.setCompressedSize(compressedData.length);
        archiveEntry.setSize(compressedEntry.getUncompressedSize());
        archiveEntry.setMethod(ZipArchiveEntry.DEFLATED);

        outputStream.addRawArchiveEntry(archiveEntry, new ByteArrayInputStream(compressedData));
    }

    public void addArchiveEntry(ZipTransformEntry entry, byte[] uncompressedData) throws IOException {
        entry.entry.setSize(uncompressedData.length);
        outputStream.putArchiveEntry(entry.entry);
        outputStream.write(uncompressedData);
        outputStream.closeArchiveEntry();
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
