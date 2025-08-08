/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.binarypatcher;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Generator {
    private static final byte[] EMPTY_DATA = new byte[0];
    private final Set<String> includedClasses = new TreeSet<>();

    private final File output;
    private final List<PatchSet> sets = new ArrayList<>();
    private boolean minimizePatches = false;
    private boolean debug;

    public Generator(File output) {
        this.output = output;
    }

    public Generator addSet(File clean, File dirty, String prefix) {
        if (!sets.isEmpty()) {
            String oldPre = sets.get(0).prefix;
            if (oldPre == null || oldPre.isEmpty() || prefix == null || prefix.isEmpty())
                throw new IllegalArgumentException("Must specify a prefix when creating multiple patchsets in a single output");
            if (sets.stream().map(e -> e.prefix).anyMatch(prefix::equals))
                throw new IllegalArgumentException("Invalid duplicate prefix " + prefix);
        }
        if (prefix != null && prefix.isEmpty())
            throw new IllegalArgumentException("Invalid empty prefix");

        sets.add(new PatchSet(clean, dirty, prefix));
        return this;
    }

    public Generator debug() {
        this.debug = true;
        return this;
    }

    public Generator minimizePatches() {
        return this.minimizePatches(true);
    }

    public Generator minimizePatches(boolean value) {
        this.minimizePatches = value;
        return this;
    }

    public void loadPatches(File root) throws IOException {
        int base = root.getAbsolutePath().length();
        int suffix = ".java.patch".length();
        try (Stream<Path> stream = Files.walk(root.toPath())) {
            stream.filter(Files::isRegularFile).map(p -> p.toAbsolutePath().toString()).filter(p -> p.endsWith(".java.patch")).forEach(path -> {
                String relative = path.substring(base + 1).replace('\\', '/');
                includedClasses.add(relative.substring(0, relative.length() - suffix));
            });
        }
    }

    public void loadIncludeClasses(File archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".class") && !name.contains("$")) {
                    String className = name.substring(0, name.length() - ".class".length());
                    includedClasses.add(className);
                }
            }
        }
    }

    public void create() throws IOException {
        Map<String, byte[]> binpatches = new TreeMap<>();
        for (PatchSet set : sets) {
            Map<String, byte[]> tmp = gatherPatches(set.clean, set.dirty);
            if (set.prefix == null)
                binpatches.putAll(tmp);
            else
                tmp.forEach((key, value) -> binpatches.put(set.prefix + '/' + key, value));
        }

        byte[] data = createJar(binpatches);
        data = lzma(data);
        try (FileOutputStream fos = new FileOutputStream(output)) {
            fos.write(data);
        }
    }

    private static String getOutermostClassName(String className) {
        int idx = className.indexOf('$');
        return idx == -1 ? className : className.substring(0, idx);
    }

    private static String getClassNameFromEntry(ZipEntry entry) {
        String path = entry.getName();
        return !entry.isDirectory() && path.endsWith(".class") ? path.substring(0, path.length() - 6) : null;
    }

    private Map<String, byte[]> gatherPatches(File clean, File dirty) throws IOException {
        Map<String, byte[]> binpatches = new TreeMap<>();
        try (ZipFile zclean = new ZipFile(clean);
             ZipFile zdirty = new ZipFile(dirty)) {

            Map<String, Set<String>> entries = new HashMap<>();
            Stream.concat(zclean.stream(), zdirty.stream()).map(Generator::getClassNameFromEntry).filter(Objects::nonNull).forEach(e -> {
                String outermostClass = getOutermostClassName(e);
                entries.computeIfAbsent(outermostClass, k -> new HashSet<>()).add(e);
            });

            log("Creating patches:");
            log("  Clean: " + clean);
            log("  Dirty: " + dirty);
            if (includedClasses.isEmpty()) { //No patches, assume full set!
                for (Map.Entry<String, Set<String>> entry : entries.entrySet()) {
                    String path = entry.getKey();
                    for (String cls : entry.getValue()) {
                        byte[] cleanData = getData(zclean, cls);
                        byte[] dirtyData = getData(zdirty, cls);
                        if (!Arrays.equals(cleanData, dirtyData)) {
                            byte[] patch = process(path, cleanData, dirtyData);
                            binpatches.put(path, patch);
                        }
                    }
                }
            } else {
                for (String path : includedClasses) {
                    if (entries.containsKey(path)) {
                        for (String cls : entries.get(path)) {
                            byte[] cleanData = getData(zclean, cls);
                            byte[] dirtyData = getData(zdirty, cls);
                            if (!Arrays.equals(cleanData, dirtyData)) {
                                byte[] patch = process(cls, cleanData, dirtyData);
                            }
                        }
                    } else {
                        log("  Failed: no source for patch? " + path + " " + path);
                    }
                }
            }
        }
        return binpatches;
    }

    private byte[] getData(ZipFile zip, String cls) throws IOException {
        ZipEntry entry = zip.getEntry(cls + ".class");
        return entry == null ? EMPTY_DATA : Util.toByteArray(zip, entry);
    }

    // public for testing
    public byte[] createJar(Map<String, byte[]> patches) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream zout = new JarOutputStream(out)) {
            zout.setLevel(Deflater.NO_COMPRESSION); //Don't deflate-compress, otherwise LZMA won't be as effective
            for (Entry<String, byte[]> e : patches.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(ConsoleTool.ZIPTIME);
                zout.putNextEntry(entry);
                zout.write(e.getValue());
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    // public for testing
    public byte[] lzma(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LZMA2Options options = new LZMA2Options();
        try (OutputStream lzma = new LZMAOutputStream(out, options, data.length)) {
            lzma.write(data);
        }
        byte[] ret = out.toByteArray();
        log("LZMA: " + data.length + " -> " + ret.length);
        return ret;
    }

    private byte[] process(String path, byte[] clean, byte[] dirty) throws IOException {
        if (debug) {
            log("  Processing " + path);
        }

        Patch patch = Patch.from(path, path, clean, dirty, this.minimizePatches);
        if (debug) {
            log("    Clean: " + Integer.toHexString(patch.checksum(clean)) + " Dirty: " + Integer.toHexString(patch.checksum(dirty)));
        }
        return patch.toBytes(false);
    }

    private void log(String message) {
        ConsoleTool.log(message);
    }

    private static class PatchSet {
        private final String prefix;
        private final File clean;
        private final File dirty;

        private PatchSet(File clean, File dirty, String prefix) {
            this.clean = clean;
            this.dirty = dirty;
            this.prefix = prefix;
        }
    }
}
