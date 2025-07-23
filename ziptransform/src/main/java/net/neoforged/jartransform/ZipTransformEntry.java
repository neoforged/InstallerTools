package net.neoforged.jartransform;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import java.nio.file.attribute.FileTime;
import java.util.zip.ZipException;

public final class ZipTransformEntry {
    ZipArchiveEntry entry;

    ZipTransformEntry(ZipArchiveEntry entry) {
        this.entry = entry;
    }

    private ZipTransformEntry(ZipTransformEntry entry) {
        try {
            this.entry = new ZipArchiveEntry(entry.entry);
        } catch (ZipException e) {
            throw new IllegalArgumentException("Cannot copy the ZIP entry: " + e);
        }
    }

    public ZipTransformEntry(String name, boolean compressed, long size, int crc, FileTime lastModified) {
        this.entry = new ZipArchiveEntry(name);
        if (size != -1) {
            this.entry.setSize(size);
        }
        if (crc != -1) {
            this.entry.setCrc(crc);
        }
        this.entry.setMethod(compressed ? ZipArchiveEntry.DEFLATED : ZipArchiveEntry.STORED);
        if (lastModified != null) {
            this.entry.setLastModifiedTime(lastModified);
        }
    }

    public ZipTransformEntry(String name) {
        this(name, true, -1, -1, null);
    }

    /**
     * @return the name of the entry. Directories will end with a slash
     */
    public String getName() {
        return entry.getName();
    }

    /**
     * @return true if the entry is a directory
     */
    public boolean isDirectory() {
        return entry.isDirectory();
    }

    /**
     * @return the last modification time of the entry in the ZIP or null, if not stored in the ZIP
     */
    public FileTime getLastModified() {
        return entry.getLastModifiedTime();
    }

    /**
     * @return the uncompressed size of the data or -1 if unknown
     */
    public long getSize() {
        return entry.getSize();
    }

    /**
     * @return a copy of this entry
     */
    public ZipTransformEntry copy() {
        return new ZipTransformEntry(this);
    }
}
