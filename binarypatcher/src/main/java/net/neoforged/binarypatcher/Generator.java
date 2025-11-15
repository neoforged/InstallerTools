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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Generator {
    private Generator() {
    }

    private static class DiffTask {
        private final String targetPath;
        private final int hashCode;
        private final PatchOperation operation;
        private final byte[] baseContent;
        private final byte[] modifiedContent;
        private final EnumSet<PatchBase> bases = EnumSet.noneOf(PatchBase.class);

        public DiffTask(String targetPath, PatchOperation operation, byte[] baseContent, byte[] modifiedContent) {
            this.targetPath = targetPath;
            this.operation = operation;
            this.baseContent = baseContent;
            this.modifiedContent = modifiedContent;
            this.hashCode = Objects.hash(targetPath, operation, Arrays.hashCode(baseContent), Arrays.hashCode(modifiedContent));
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            DiffTask other = (DiffTask) o;
            return operation == other.operation
                    && targetPath.equals(other.targetPath)
                    && Objects.deepEquals(baseContent, other.baseContent)
                    && Objects.deepEquals(modifiedContent, other.modifiedContent);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        public Patch createPatch(DiffOptions diffOptions) throws IOException {
            switch (operation) {
                case CREATE:
                    return Patch.createAdd(targetPath, modifiedContent, bases);
                case REMOVE:
                    return Patch.createRemove(targetPath, bases);
                case MODIFY:
                    return Patch.createModified(targetPath, baseContent, modifiedContent, bases, diffOptions);
                default:
                    throw new IllegalStateException("Unknown task operation: " + operation);
            }
        }
    }

    public static void createPatchBundle(
            Map<PatchBase, File> baseFiles,
            Map<PatchBase, File> modifiedFiles,
            File patchBundleFile,
            DiffOptions diffOptions
    ) throws IOException {

        Set<PatchBase> bases = baseFiles.keySet();
        if (!bases.equals(modifiedFiles.keySet())) {
            throw new IllegalArgumentException("The same set of base and modified files must be provided: "
                    + baseFiles.keySet() + " != " + modifiedFiles.keySet());
        }

        Map<DiffTask, DiffTask> tasks = new LinkedHashMap<>();

        // This will be memory intensive.
        for (PatchBase base : bases) {
            File baseFile = baseFiles.get(base);
            File modifiedFile = modifiedFiles.get(base);

            try (ZipFile baseZip = new ZipFile(baseFile); ZipFile modifiedZip = new ZipFile(modifiedFile)) {
                Enumeration<? extends ZipEntry> baseEntries = baseZip.entries();
                while (baseEntries.hasMoreElements()) {
                    ZipEntry baseEntry = baseEntries.nextElement();
                    // We ignore directories
                    if (baseEntry.isDirectory()) {
                        continue;
                    }

                    ZipEntry modifiedEntry = modifiedZip.getEntry(baseEntry.getName());

                    // Determine operation
                    DiffTask task;
                    if (modifiedEntry == null) {
                        task = new DiffTask(
                                baseEntry.getName(),
                                PatchOperation.REMOVE,
                                null,
                                null
                        );
                    } else {
                        byte[] baseContent = Util.toByteArray(baseZip, baseEntry);
                        byte[] modifiedContent = Util.toByteArray(modifiedZip, modifiedEntry);
                        if (Arrays.equals(baseContent, modifiedContent)) {
                            continue; // The content matches, no need to diff
                        }
                        task = new DiffTask(
                                baseEntry.getName(),
                                PatchOperation.MODIFY,
                                baseContent,
                                modifiedContent
                        );
                    }

                    // De-Dupe Tasks
                    DiffTask previousTask = tasks.putIfAbsent(task, task);
                    if (previousTask != null) {
                        task = previousTask;
                    }
                    task.bases.add(base);
                }

                // Find new entries
                Enumeration<? extends ZipEntry> modifiedEntries = modifiedZip.entries();
                while (modifiedEntries.hasMoreElements()) {
                    ZipEntry modifiedEntry = modifiedEntries.nextElement();
                    if (modifiedEntry.isDirectory() || baseZip.getEntry(modifiedEntry.getName()) != null) {
                        continue; // We ignore directories and modified entries were already processed
                    }

                    DiffTask task = new DiffTask(
                            modifiedEntry.getName(),
                            PatchOperation.CREATE,
                            null,
                            Util.toByteArray(modifiedZip, modifiedEntry)
                    );
                    // De-Dupe Tasks
                    DiffTask previousTask = tasks.putIfAbsent(task, task);
                    if (previousTask != null) {
                        task = previousTask;
                    }
                    task.bases.add(base);
                }
            }
        }

        try (OutputStream bundleOut = new BufferedOutputStream(new FileOutputStream(patchBundleFile));
             PatchBundleWriter bundleWriter = new PatchBundleWriter(bundleOut, baseFiles.keySet())) {

            log("Processing " + tasks.size() + " diff tasks");

            for (DiffTask task : tasks.values()) {
                bundleWriter.write(task.createPatch(diffOptions));
            }
        }
    }

    private static void log(String message) {
        ConsoleTool.log(message);
    }
}
