package net.neoforged.jartransform;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

@FunctionalInterface
public interface ZipProcessingStrategy {
    /**
     * Optionally return content to be inserted at the start of the resulting ZIP file.
     */
    default List<CompletableFuture<TransformedContent>> getLeadingContent(ZipInput inputFile) {
        return Collections.emptyList();
    }

    /**
     * Optionally return content to be appended to the end of the resulting ZIP file.
     */
    default List<CompletableFuture<TransformedContent>> getTrailingContent(ZipInput inputFile) {
        return Collections.emptyList();
    }

    /**
     * Asynchronously transforms a single entry from the input.
     * The future may return {@code null} to signal the entry should be skipped, or the future itself may be {@code null}
     * with the same effect.
     */
    CompletableFuture<TransformedContent> transformContent(ZipInput inputFile, ZipTransformEntry entry);

    static TransformedContent uncompressedContent(String path, FileTime lastModified, byte[] uncompressedContent) {
        ZipTransformEntry entry = new ZipTransformEntry(path);
        if (lastModified != null) {
            entry.entry.setLastModifiedTime(lastModified);
        }
        return uncompressedContent(entry, uncompressedContent);
    }

    static TransformedContent uncompressedContent(ZipTransformEntry entry, byte[] uncompressedContent) {
        TransformedContent content = new TransformedContent();
        content.entry = entry.copy();

        CRC32 crc32 = new CRC32();
        crc32.update(uncompressedContent);
        content.rawContent = uncompressedContent;
        content.entry.entry.setCrc(crc32.getValue());
        content.entry.entry.setSize(uncompressedContent.length);
        content.entry.entry.setCompressedSize(uncompressedContent.length);
        content.entry.entry.setMethod(ZipArchiveEntry.STORED);
        return content;
    }

    static TransformedContent compressedContent(String name, FileTime lastModified, byte[] uncompressedContent) {
        ZipTransformEntry entry = new ZipTransformEntry(name);
        if (lastModified != null) {
            entry.entry.setLastModifiedTime(lastModified);
        }
        return compressedContent(entry, uncompressedContent);
    }

    static TransformedContent compressedContent(ZipTransformEntry entry, byte[] uncompressedContent) {
        TransformedContent content = new TransformedContent();
        content.entry = entry.copy();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedContent.length);
        CRC32 crc32 = new CRC32();
        Deflater deflater = DeflaterPool.borrow();
        try {
            try (OutputStream out = new CheckedOutputStream(new DeflaterOutputStream(bos, deflater), crc32)) {
                out.write(uncompressedContent);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to compress data for entry " + entry.getName(), e);
            }
        } finally {
            DeflaterPool.release(deflater);
        }

        content.rawContent = bos.toByteArray();
        content.entry.entry.setCrc(crc32.getValue());
        content.entry.entry.setSize(uncompressedContent.length);
        content.entry.entry.setCompressedSize(content.rawContent.length);
        content.entry.entry.setMethod(ZipArchiveEntry.DEFLATED);
        return content;
    }

    /**
     * Copy the entries content without modifying any of the entries other attributes.
     */
    static TransformedContent copiedContent(ZipInput input, ZipTransformEntry entry) {
        long compressedSize = entry.entry.getCompressedSize();
        if (compressedSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Cannot copy entries that exceed 2GB");
        }
        byte[] data;
        try (InputStream in = input.openRawEntry(entry)) {
            if (compressedSize != -1) {
                data = new byte[(int) compressedSize];
                int offset = 0;
                while (offset < data.length) {
                    int read = in.read(data);
                    if (read == -1) {
                        throw new IOException("Premature end of file encountered. Read " + offset + " bytes of " + data.length + " for entry " + entry.getName());
                    }
                    offset += read;
                }
                // Check for trailing data
                if (in.read() != -1) {
                    throw new IOException("Found trailing data after reading " + offset + " bytes of entry " + entry.getName());
                }
            } else {
                byte[] buffer = new byte[8192];
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int read;
                while ((read = in.read(buffer)) > 0) {
                    bos.write(buffer, 0, read);
                }
                data = bos.toByteArray();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy content of entry " + entry.getName(), e);
        }

        TransformedContent content = new TransformedContent();
        content.entry = entry.copy();
        content.rawContent = data;
        return content;
    }

    final class TransformedContent {
        ZipTransformEntry entry;
        byte[] rawContent;

        private TransformedContent() {
        }
    }
}
