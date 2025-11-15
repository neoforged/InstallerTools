/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.binarypatcher;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Generator {
    private Generator() {
    }

    public static void createPatchBundle(
            Map<PatchBase, File> baseFiles,
            File modifiedFile,
            File patchBundleFile,
            DiffOptions diffOptions
    ) throws IOException {

        // Find all common filenames across all zips
        Map<PatchBase, ZipFile> baseZipFiles = new EnumMap<>(PatchBase.class);

        try (ZipFile modifiedZipFile = new ZipFile(modifiedFile);
             OutputStream bundleOut = new BufferedOutputStream(new FileOutputStream(patchBundleFile));
             PatchBundleWriter bundleWriter = new PatchBundleWriter(bundleOut, baseFiles.keySet())) {

            // Collect all unique filenames first, then process them
            Set<String> allTargetPaths = new TreeSet<>();
            collectFilenames(modifiedZipFile, allTargetPaths);
            for (Entry<PatchBase, File> entry : baseFiles.entrySet()) {
                ZipFile zipFile = new ZipFile(entry.getValue());
                baseZipFiles.put(entry.getKey(), zipFile);
                collectFilenames(zipFile, allTargetPaths);
            }

            log("Processing " + allTargetPaths.size() + " unique target paths");

            EnumMap<PatchBase, byte[]> baseData = new EnumMap<>(PatchBase.class);
            for (String targetPath : allTargetPaths) {
                baseData.clear();
                for (Entry<PatchBase, ZipFile> baseZipFileEntry : baseZipFiles.entrySet()) {
                    PatchBase base = baseZipFileEntry.getKey();
                    ZipFile baseZipFile = baseZipFileEntry.getValue();
                    ZipEntry baseEntry = baseZipFile.getEntry(targetPath);
                    if (baseEntry == null) {
                        baseData.put(base, null); // Mark file as not present in base
                    } else {
                        baseData.put(base, Util.toByteArray(baseZipFile, baseEntry));
                    }
                }

                // Collect modified data
                ZipEntry modifiedEntry = modifiedZipFile.getEntry(targetPath);
                byte[] modifiedData = modifiedEntry == null ? null : Util.toByteArray(modifiedZipFile, modifiedEntry);

                Patch.from(
                        targetPath,
                        baseData,
                        modifiedData,
                        diffOptions,
                        patch -> {
                            try {
                                bundleWriter.write(patch);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                );
            }

            // Close once done
            for (ZipFile zipFile : baseZipFiles.values()) {
                zipFile.close();
            }
        } catch (Exception e) {
            for (ZipFile zipFile : baseZipFiles.values()) {
                try {
                    zipFile.close();
                } catch (IOException ex) {
                    e.addSuppressed(ex);
                }
            }

            throw e;
        }
    }

    private static void collectFilenames(ZipFile zipFile, Set<String> filenames) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                filenames.add(entry.getName());
            }
        }
    }

    private static void log(String message) {
        ConsoleTool.log(message);
    }
}
