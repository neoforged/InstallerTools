package net.neoforged.jartransform;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import java.util.Enumeration;
import java.util.Iterator;

final class ZipInputEntryIterator implements Iterator<ZipTransformEntry> {
    private final Enumeration<ZipArchiveEntry> entries;
    private final ZipTransformEntry entry = new ZipTransformEntry((ZipArchiveEntry) null);

    ZipInputEntryIterator(Enumeration<ZipArchiveEntry> entries) {
        this.entries = entries;
    }

    @Override
    public boolean hasNext() {
        return entries.hasMoreElements();
    }

    @Override
    public ZipTransformEntry next() {
        entry.entry = entries.nextElement();
        return entry;
    }
}
