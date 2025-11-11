/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.binarypatcher;

import com.nothome.delta.GDiffPatcher;
import net.neoforged.cliutils.progress.ProgressReporter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Patcher {
    static final byte[] DELETION_MARKER = new byte[0];
    private static final ProgressReporter PROGRESS = ProgressReporter.getDefault();
    private static final long ZIPTIME = 628041600000L;

    private Patcher() {
    }

    public static void patch(File baseFile, PatchBase baseType, List<File> patchBundleFiles, File outputFile) {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT")); //Fix Java stupidity that causes timestamps in zips to depend on user's timezone!

        try (ZipFile baseZip = new ZipFile(baseFile)) {
            // Just keep content in-memory that has been touched
            Map<String, byte[]> patchedContent = new HashMap<>();

            for (File patchBundleFile : patchBundleFiles) {
                applyPatchBundle(baseFile, baseType, patchBundleFile, patchedContent, baseZip);
            }

            // Now stream out the new entries
            try (ZipOutputStream zOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {
                Enumeration<? extends ZipEntry> entries = baseZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }

                    byte[] patched = patchedContent.remove(entry.getName());
                    if (patched == DELETION_MARKER) {
                        debug("Deleting " + entry.getName());
                        continue; // Skip deleted file
                    } else  {
                        zOut.putNextEntry(entry);
                        if (patched != null) {
                            zOut.write(patched); // Write patched content
                        } else {
                            Util.copy(baseZip, entry, zOut); // Stream through unchanged
                        }
                        zOut.closeEntry();
                    }
                }

                // Append newly created entries
                for (Map.Entry<String, byte[]> entry : patchedContent.entrySet()) {
                    if (entry.getValue() == DELETION_MARKER) {
                        throw new IllegalStateException("Somehow " + entry.getKey() + " was deleted although it does not exist.");
                    }

                    zOut.putNextEntry(getNewEntry(entry.getKey()));
                    zOut.write(entry.getValue());
                    zOut.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    private static void applyPatchBundle(File baseFile, PatchBase baseType, File patchBundleFile, Map<String, byte[]> patchedContent, ZipFile baseZip) throws IOException {
        try (PatchBundleReader patchBundle = new PatchBundleReader(patchBundleFile)) {
            if (!patchBundle.getSupportedBaseTypes().contains(baseType)) {
                throw new IllegalArgumentException("Cannot apply patch bundle " + patchBundleFile + " to " + baseFile + " because it only applies to the base types " + patchBundle.getSupportedBaseTypes());
            }

            for (Patch patch : patchBundle) {
                // Skip patches not applying to the current base
                if (!patch.getBaseTypes().contains(baseType)) {
                    continue;
                }

                switch (patch.getOperation()) {
                    case CREATE:
                        patchedContent.put(patch.getTargetPath(), patch.getData());
                        break;
                    case MODIFY:
                        applyPatch(patch, baseZip, patchedContent);
                        break;
                    case REMOVE:
                        patchedContent.put(patch.getTargetPath(), DELETION_MARKER);
                        break;
                }
            }
        }
    }

    private static void applyPatch(Patch patch, ZipFile baseZip, Map<String, byte[]> patchedContent) throws IOException {
        byte[] currentData = patchedContent.get(patch.getTargetPath());
        if (currentData == null) {
            ZipEntry entry = baseZip.getEntry(patch.getTargetPath());
            if (entry == null) {
                throw new IllegalStateException("Patch targets " + patch.getTargetPath() + ", but it does not exist in the base.");
            }
            currentData = Util.toByteArray(baseZip, entry);
        } else if (currentData == DELETION_MARKER) {
            throw new IllegalStateException("Patch targets " + patch.getTargetPath() + ", but it was deleted by an earlier patch bundle.");
        }

        long checksum = Patch.checksum(currentData);
        if (checksum != patch.getBaseChecksumUnsigned())
            throw new IOException("Patch expected " + patch.getTargetPath() + " to have the checksum "
                    + Long.toHexString(patch.getBaseChecksumUnsigned()) + " but it was " + Long.toHexString(checksum));

        byte[] patchedData = new GDiffPatcher().patch(currentData, patch.getData());
        patchedContent.put(patch.getTargetPath(), patchedData);
    }

    private static ZipEntry getNewEntry(String name) {
        ZipEntry ret = new ZipEntry(name);
        ret.setTime(ZIPTIME);
        return ret;
    }

    private static void debug(String message) {
        if (ConsoleTool.DEBUG) {
            System.out.println(message);
        }
    }

    private void log(String message) {
        System.out.println(message);
    }

}
