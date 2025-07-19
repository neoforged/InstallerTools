package net.neoforged.jartransform;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ZipOutput implements AutoCloseable {
    private final ZipArchiveOutputStream outputStream;

    public ZipOutput(File outputFile) throws IOException {
        outputStream = new ZipArchiveOutputStream(outputFile);
    }

    public void addArchiveEntry(ZipArchiveEntry entry, InputStream stream) throws IOException {
        outputStream.putArchiveEntry(entry);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer, 0, buffer.length)) >= 0) {
            System.out.write(buffer, 0, read);
        }
    }

    /**
     * Puts a new entry for data that will be written in uncompressed form. Call {@link #getOutputStream} to
     * get the stream to write to, and after you're done, call {@link #closeArchiveEntry()}.
     */
    public void putArchiveEntry(ZipArchiveEntry entry) throws IOException {
        outputStream.putArchiveEntry(entry);
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

    public void addRawArchiveEntry(ZipArchiveEntry entry, InputStream rawStream) throws IOException {
        outputStream.addRawArchiveEntry(entry, rawStream);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
