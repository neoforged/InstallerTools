/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.binarypatcher;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.nothome.delta.GDiffPatcher;

import net.neoforged.cliutils.progress.ProgressReporter;
import org.tukaani.xz.LZMAInputStream;

public class Patcher {
    public static final String EXTENSION = ".lzma";
    private static final byte[] EMPTY_DATA = new byte[0];
    private static final GDiffPatcher PATCHER = new GDiffPatcher();
    private static final ProgressReporter PROGRESS = ProgressReporter.getDefault();

    private final Map<String, List<Patch>> patches = new TreeMap<>();

    private final File clean;
    private final File output;
    private boolean keepData = false;
    private boolean patchedOnly = false;

    public Patcher(File clean, File output) {
        this.clean = clean;
        this.output = output;
    }

    public Patcher keepData(boolean value) {
        this.keepData = value;
        return this;
    }

    public Patcher includeUnpatched(boolean value) {
        this.patchedOnly = !value;
        return this;
    }

    // This can be called multiple times, if patchsets are built on top of eachother.
    // They will be applied in the order that the patch files were loaded.
    public void loadPatches(File file, String prefix) throws IOException {
        log("Loading patches file: " + file);
        PROGRESS.setStep("Loading patch files");

        try (InputStream input = new FileInputStream(file)) {
            InputStream stream = new LZMAInputStream(new BufferedInputStream(input));

            JarInputStream jar = new JarInputStream(new BufferedInputStream(stream));

            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".binpatch") && (prefix == null || name.startsWith(prefix + '/'))) {
                    debug("  Reading patch " + entry.getName());
                    Patch patch = Patch.from(jar, false);
                    debug("    Checksum: " + Integer.toHexString(patch.checksum) + " Exists: " + patch.exists);
                    patches.computeIfAbsent(patch.obf, k -> new ArrayList<>()).add(patch);
                }
            }
        }
    }

    public void process() throws IOException {
        debug("Processing: " + clean);
        if (output.exists() && !output.delete())
            throw new IOException("Failed to delete existing output file: " + output);

        PROGRESS.setStep("Patching input");
        try (ZipFile zclean = new ZipFile(clean);
             ZipOutputStream zpatched = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            PROGRESS.setMaxProgress(zclean.size());

            Set<String> processed = new HashSet<>();
            int amount = 0;
            Enumeration<? extends ZipEntry> zcleanEntries = zclean.entries();
            while (zcleanEntries.hasMoreElements()) {
                ZipEntry entry = zcleanEntries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String key = entry.getName().substring(0, entry.getName().length() - 6); //String .class
                    List<Patch> patchlist = patches.get(key);
                    if (patchlist != null) {
                        processed.add(key);
                        byte[] data = Util.toByteArray(zclean, entry);
                        for (int x = 0; x < patchlist.size(); x++) {
                            Patch patch = patchlist.get(x);
                            debug("  Patching " + patch.getName() + " " + (x + 1) + "/" + patchlist.size());
                            data = patch(data, patch);
                        }
                        if (data.length != 0) {
                            ZipEntry newEntry = getNewEntry(entry.getName());
                            newEntry.setSize(data.length);
                            zpatched.putNextEntry(newEntry);
                            zpatched.write(data);
                        }
                    } else if (!patchedOnly) {
                        debug("  Copying " + entry.getName());
                        zpatched.putNextEntry(getNewEntry(entry.getName()));
                        Util.copy(zclean, entry, zpatched);
                    }
                } else if (keepData) {
                    debug("  Copying " + entry.getName());
                    zpatched.putNextEntry(getNewEntry(entry.getName()));
                    Util.copy(zclean, entry, zpatched);
                }

                // Do updates in batches of 10 to avoid spam
                if ((++amount) % 10 == 0) {
                    PROGRESS.setProgress(amount);
                }
            }

            // Add new files
            PROGRESS.setStep("Adding new files");
            for (Entry<String, List<Patch>> e : patches.entrySet()) {
                String key = e.getKey();
                List<Patch> patchlist = e.getValue();

                if (processed.contains(key))
                    continue;

                byte[] data = new byte[0];
                for (int x = 0; x < patchlist.size(); x++) {
                    Patch patch = patchlist.get(x);
                    debug("  Patching " + patch.getName() + " " + (x + 1) + "/" + patchlist.size());
                    data = patch(data, patch);
                }
                if (data.length != 0) {
                    ZipEntry newEntry = getNewEntry(key + ".class");
                    newEntry.setSize(data.length);
                    zpatched.putNextEntry(newEntry);
                    zpatched.write(data);
                }
            }

            PROGRESS.setProgress(amount);
        }
    }

    private byte[] patch(byte[] data, Patch patch) throws IOException {
        if (patch.exists && data.length == 0)
            throw new IOException("Patch expected " + patch.getName() + " to exist, but received empty data");
        if (!patch.exists && data.length > 0)
            throw new IOException("Patch expected " + patch.getName() + " to not exist, but received " + data.length + " bytes");

        int checksum = patch.checksum(data);
        if (checksum != patch.checksum)
            throw new IOException("Patch expected " + patch.getName() + " to have the checksum " + Integer.toHexString(patch.checksum) + " but it was " + Integer.toHexString(checksum));

        if (patch.data.length == 0) //File removed
            return EMPTY_DATA;
        else
            return PATCHER.patch(data, patch.data);
    }

    private ZipEntry getNewEntry(String name) {
        ZipEntry ret = new ZipEntry(name);
        ret.setTime(ConsoleTool.ZIPTIME);
        return ret;
    }

    private void debug(String message) {
        if (ConsoleTool.DEBUG) {
            System.out.println(message);
        }
    }

    private void log(String message) {
        System.out.println(message);
    }

    // Public for testing
    public Map<String, List<Patch>> getPatches() {
        Map<String, List<Patch>> ret = new HashMap<>();
        patches.forEach((k, v) ->
                ret.computeIfAbsent(k, a -> new ArrayList<>()).addAll(v)
        );
        return ret;
    }

}
