/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.binarypatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Util {
    public static byte[] toByteArray(ZipFile zf, ZipEntry entry) throws IOException {
        // The default is 32 bytes, which is almost never going to be enough. We should rather overestimate here.
        int estimatedSize;
        if (entry.getSize() != -1) {
            estimatedSize = (int) entry.getSize();
        } else if (entry.getCompressedSize() != -1) {
            // This is just an estimate
            estimatedSize = (int)(entry.getCompressedSize() * 2);
        } else {
            estimatedSize = 8192;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(estimatedSize);

        int nRead;
        byte[] data = new byte[8192];

        try (InputStream in = zf.getInputStream(entry)) {
            while ((nRead = in.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    public static void copy(ZipFile zipFile, ZipEntry entry, OutputStream target) throws IOException {
        try (InputStream in = zipFile.getInputStream(entry)) {
            copy(in, target);
        }
    }

    public static void copy(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }

    static ZipEntry copyEntry(ZipEntry entry) {
        ZipEntry newEntry = new ZipEntry(entry.getName());
        newEntry.setTime(entry.getTime());
        if (entry.getLastModifiedTime() != null) {
            newEntry.setLastModifiedTime(entry.getLastModifiedTime());
        }
        if (entry.getLastAccessTime() != null) {
            newEntry.setLastAccessTime(entry.getLastAccessTime());
        }
        if (entry.getCreationTime() != null) {
            newEntry.setCreationTime(entry.getCreationTime());
        }
        return newEntry;
    }
}
