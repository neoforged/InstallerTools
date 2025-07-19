package net.neoforged.jartransform;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public final class ZipInput implements AutoCloseable {
    private final ZipFile input;
    private final FileInputStream stream;

    public ZipInput(File inputFile) throws IOException {
        stream = new FileInputStream(inputFile);
        input = ZipFile.builder()
                .setIgnoreLocalFileHeader(true)
                .setSeekableByteChannel(stream.getChannel())
                .get();
    }

    public Iterator<ZipTransformEntry> getEntries() {
        return new ZipInputEntryIterator(input.getEntries());
    }

    public ZipTransformEntry getEntry(String name) {
        return new ZipTransformEntry(input.getEntry(name));
    }

    public void transferEntry(ZipTransformEntry entry, ZipOutput output) throws IOException {
        try (InputStream rawInput = input.getRawInputStream(entry.entry)) {
            output.addRawArchiveEntry(entry.entry, rawInput);
        }
    }

    public void transferEntry(ZipTransformEntry entry, ZipTransformEntry destEntry, ZipOutput output) throws IOException {
        try (InputStream rawInput = input.getRawInputStream(entry.entry)) {
            output.addRawArchiveEntry(destEntry.entry, rawInput);
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
        stream.close();
    }
}
