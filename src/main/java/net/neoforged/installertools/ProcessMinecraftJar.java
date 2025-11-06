/*
 * InstallerTools
 * Copyright (c) 2019-2025.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.neoforged.installertools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nothome.delta.GDiffPatcher;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.neoforged.art.api.IdentifierFixerConfig;
import net.neoforged.art.api.Renamer;
import net.neoforged.art.api.SignatureStripperConfig;
import net.neoforged.art.api.SourceFixerConfig;
import net.neoforged.art.api.Transformer;
import net.neoforged.binarypatcher.Patch;
import net.neoforged.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.tukaani.xz.LZMAInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * This task takes the released Minecraft artifacts (client jar, server jar, official mappings), as well as
 * the NeoForm and NeoForge release artifacts (NeoForm parameter mappings, NeoForge binary patches), and processes
 * them to a point to get a usable Minecraft jar for either generating binary patches (when no binary patches are
 * given to this task), or for use when playing NeoForge in production.
 */
public class ProcessMinecraftJar extends Task {

    private static final String DIST_SERVER = "server";
    private static final String DIST_CLIENT = "client";

    static final long STABLE_TIMESTAMP = 0x386D4380; //01/01/2000 00:00:00 java 8 breaks when using 0.

    public static final long NEW_ENTRY_ZIPTIME = 628041600000L;
    private ExecutorService executorService;

    @Override
    public void process(String[] args) throws IOException {

        long start = System.nanoTime();

        OptionParser parser = new OptionParser();
        OptionSpec<File> inputArg = parser.accepts("input", "The original Minecraft jar. Either a server or client jar can be given. You can also pass both client and server to create a joined distribution.").withRequiredArg().ofType(File.class);
        OptionSpec<File> inputMappingsArg = parser.accepts("input-mappings", "The official Mappings text-file matching the input jar.").withRequiredArg().ofType(File.class);
        OptionSpec<File> neoformDataArg = parser.accepts("neoform-data", "The NeoForm data file used for getting SRG parameter names, or a LZMA compressed NeoForm mappings file.").withRequiredArg().ofType(File.class);
        OptionSpec<File> outputArg = parser.accepts("output", "Where the resulting processed jar is written to.").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputLibrariesArg = parser.accepts("extract-libraries-to", "Path to an on-disk directory where any embedded libraries will be written to. Applies to the dedicated server.").withOptionalArg().ofType(File.class);
        OptionSpec<File> patchesArchiveArg = parser.accepts("apply-patches", "Path to a binpatch file with patches to apply. Multiple can be specified and will be applied in-order.").withOptionalArg().ofType(File.class);
        OptionSpec<Void> noModManifest = parser.accepts("no-mod-manifest", "Disables adding a neoforge.mods.toml mod manifest");

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        List<File> inputFiles = options.valuesOf(inputArg);
        if (inputFiles.isEmpty() || inputFiles.size() > 2) {
            System.err.println("Can only pass one or two --input arguments.");
            System.exit(1);
            return;
        }
        File inputFile = inputFiles.get(0);
        File mergeInputFile = inputFiles.size() > 1 ? inputFiles.get(1) : null;
        File inputMappingsFile = options.valueOf(inputMappingsArg);

        File librariesFolder = options.valueOf(outputLibrariesArg);
        File outputFile = options.valueOf(outputArg);
        File neoformDataFile = options.valueOf(neoformDataArg);
        List<File> patchesArchiveFiles = options.valuesOf(patchesArchiveArg);

        boolean addModManifest = !options.has(noModManifest);

        executorService = ForkJoinPool.commonPool();
        try {
            processZip(inputFile, inputMappingsFile, mergeInputFile, outputFile, librariesFolder, neoformDataFile, patchesArchiveFiles, addModManifest);
        } finally {
            if (executorService != ForkJoinPool.commonPool()) {
                executorService.shutdown();
            }
            executorService = null;
        }

        logElapsed("overall work", start);
    }

    private void processZip(File inputFile,
                            @Nullable
                            File inputMappingsFile,
                            @Nullable File mergeInputFile,
                            File outputFile,
                            @Nullable File librariesFolder,
                            @Nullable File neoformDataFile,
                            List<File> patchesArchiveFiles,
                            boolean addModManifest) {

        CompletableFuture<Map<String, InputFileEntry>> outputEntries;
        if (mergeInputFile == null) {
            outputEntries = supplyAsync("load input zip", () -> loadInputZip(inputFile, librariesFolder));
        } else {
            CompletableFuture<Map<String, InputFileEntry>> inputEntriesFuture = supplyAsync("load " + inputFile.getName(), () -> loadInputZip(inputFile, librariesFolder));
            CompletableFuture<Map<String, InputFileEntry>> mergeInputEntriesFuture = supplyAsync("load " + mergeInputFile.getName(), () -> loadInputZip(mergeInputFile, librariesFolder));

            outputEntries = inputEntriesFuture.thenCombine(mergeInputEntriesFuture, this::merge);
        }

        if (inputMappingsFile != null) {
            CompletableFuture<IMappingFile> mappings = supplyAsync("load mappings", () -> loadMappings(inputMappingsFile));
            if (neoformDataFile != null) {
                CompletableFuture<IMappingFile> parameterMappings = supplyAsync("load parameter mappings", () -> loadNeoformMappings(neoformDataFile));
                mappings = mappings.thenCombineAsync(parameterMappings, this::mergeMappings);
            }
            outputEntries = allOfThenCompose(outputEntries, mappings, this::deobfuscateJar);
        }

        // If patches are supplied, apply them
        if (!patchesArchiveFiles.isEmpty()) {
            CompletableFuture<List<Patch>> patches = loadPatchLists(patchesArchiveFiles);

            outputEntries = outputEntries.thenCombineAsync(patches, this::applyPatches);
        }

        CompletableFuture<Void> outputFileFuture = outputEntries.thenAccept(outputFileEntries -> {
            // Add a mod manifest if requested
            if (addModManifest) {
                String minecraftVersion = getMinecraftVersion(outputFileEntries);
                InputFileEntry manifestEntry = createMinecraftModManifest(minecraftVersion);
                outputFileEntries.put(manifestEntry.name, manifestEntry);
            }

            writeOutputFile(outputFile, outputFileEntries.values());
        });

        try {
            outputFileFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }

    private static InputFileEntry applyClassTransform(InputFileEntry entry, Consumer<ClassNode> transformer) {
        ClassReader classReader = new ClassReader(entry.getContent());
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        transformer.accept(classNode);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return new InputFileEntry(entry.getName(), entry.getLastModified(), writer.toByteArray());
    }

    private static String getMinecraftVersion(Map<String, InputFileEntry> entries) {
        InputFileEntry versionJsonEntry = entries.get("version.json");
        if (versionJsonEntry == null) {
            return "unknown";
        }
        JsonObject versionManifest = new Gson().fromJson(new String(versionJsonEntry.getContent(), StandardCharsets.UTF_8), JsonObject.class);
        return versionManifest.getAsJsonPrimitive("id").getAsString();
    }

    private CompletableFuture<List<Patch>> loadPatchLists(List<File> patchesArchiveFiles) {
        if (patchesArchiveFiles.size() == 1) {
            // Simplified form if only a single patch list is given
            File archiveFile = patchesArchiveFiles.get(0);
            return supplyAsync("load patches " + archiveFile.getName(), () -> loadPatchList(archiveFile, null));
        }

        List<List<Patch>> patchLists = new ArrayList<>(patchesArchiveFiles.size());
        for (int i = 0; i < patchesArchiveFiles.size(); i++) {
            patchLists.add(null); // Pre-allocate the list
        }

        CompletableFuture<?>[] patchesLoadFutures = new CompletableFuture[patchesArchiveFiles.size()];
        for (int i = 0; i < patchesArchiveFiles.size(); i++) {
            int patchListIndex = i;
            File archiveFile = patchesArchiveFiles.get(i);
            patchesLoadFutures[i] = supplyAsync("load patches " + archiveFile.getName(), () -> loadPatchList(archiveFile, null))
                    .thenAccept(patchList -> patchLists.set(patchListIndex, patchList));
        }
        // Merge the patch lists into a single list
        return CompletableFuture.allOf(patchesLoadFutures).thenApply(unused -> {
            // Merge the patch lists
            int overallSize = patchLists.stream().mapToInt(List::size).sum();
            List<Patch> patchList = new ArrayList<>(overallSize);
            patchLists.forEach(patchList::addAll);
            return patchList;
        });
    }

    private Map<String, InputFileEntry> applyPatches(Map<String, InputFileEntry> entries, List<Patch> patches) {
        long start = System.nanoTime();

        GDiffPatcher patcher = new GDiffPatcher();

        for (Patch patch : patches) {
            try {
                applyPatch(entries, patch, patcher);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to apply patch file " + patch.getName(), e);
            }
        }

        logElapsed("applying patches", start);
        return entries;
    }

    private static void applyPatch(Map<String, InputFileEntry> entries, Patch patch, GDiffPatcher patcher) throws IOException {
        String patchedPath = patch.obf + ".class";

        InputFileEntry entry = entries.get(patchedPath);
        if (entry == null) {
            if (patch.exists) {
                throw new IOException("Patch expected " + patch.getName() + " to exist, but received empty data");
            }

            // Create a new synthetic entry
            entry = new InputFileEntry(patchedPath, NEW_ENTRY_ZIPTIME, new byte[0]);
        } else {
            if (!patch.exists) {
                throw new IOException("Patch expected " + patch.getName() + " to not exist, but entry exists");
            }
        }

        int checksum = patch.checksum(entry.content);
        if (checksum != patch.checksum) {
            throw new IOException("Patch expected " + patch.getName() + " to have the checksum " + Integer.toHexString(patch.checksum) + " but it was " + Integer.toHexString(checksum));
        }

        if (patch.data.length == 0) {
            entries.remove(patchedPath); //File removed
        } else {
            entry = new InputFileEntry(entry.name, NEW_ENTRY_ZIPTIME, patcher.patch(entry.content, patch.data));
            entries.put(entry.name, entry);
        }
    }

    private List<Patch> loadPatchList(File patchesArchiveFile, String prefix) throws IOException {
        List<Patch> patches = new ArrayList<>();

        try (InputStream input = new FileInputStream(patchesArchiveFile)) {
            InputStream stream = new LZMAInputStream(new BufferedInputStream(input));
            ZipInputStream zip = new ZipInputStream(new BufferedInputStream(stream));

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".binpatch") && (prefix == null || name.startsWith(prefix + '/'))) {
                    Patch patch = Patch.from(zip, false);
                    patches.add(patch);
                }
            }
        }

        return patches;
    }

    private static IMappingFile loadMappings(File inputMappingsFile) throws IOException {
        return IMappingFile.load(inputMappingsFile).reverse();
    }

    private void writeOutputFile(File outputFile, Collection<InputFileEntry> outputFileEntries) {
        long start = System.nanoTime();

        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        Set<String> writtenDirectories = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)))) {

            for (InputFileEntry outputFileEntry : outputFileEntries) {
                int lastSlash = outputFileEntry.name.lastIndexOf('/');
                if (lastSlash != -1) {
                    createFolder(zos, writtenDirectories, outputFileEntry.name.substring(0, lastSlash + 1));
                }

                ZipEntry zipEntry = new ZipEntry(outputFileEntry.name);
                zipEntry.setTime(outputFileEntry.lastModified);
                zos.putNextEntry(zipEntry);
                zos.write(outputFileEntry.content);
                zos.closeEntry();
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Unexpected I/O exception while writing the output file " + outputFile, e);
        }

        logElapsed("write output file", start);
    }

    private void createFolder(ZipOutputStream zos, Set<String> writtenDirectories, String folderName) throws IOException {
        if (!writtenDirectories.add(folderName))
            return;

        // Add parent recursively
        int idx = folderName.lastIndexOf('/');
        if (idx != -1)
            createFolder(zos, writtenDirectories, folderName.substring(0, idx + 1));

        ZipEntry dir = new ZipEntry(folderName);
        dir.setTime(Transformer.Entry.STABLE_TIMESTAMP);
        zos.putNextEntry(dir);
        zos.closeEntry();
    }

    private IMappingFile mergeMappings(IMappingFile mappings, IMappingFile parameterMappings) {
        long start = System.nanoTime();
        IMappingFile merged = mappings.merge(parameterMappings);
        logElapsed("merge mappings", start);
        return merged;
    }

    private static Map<String, InputFileEntry> loadInputZip(File inputFile, @Nullable File librariesFolder) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        try (ZipFile zipFile = openMinecraftJar(inputFile, librariesFolder)) {
            Map<String, InputFileEntry> result = new LinkedHashMap<>();

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (zipEntry.isDirectory()) {
                    continue;
                }

                bout.reset();
                try (InputStream in = zipFile.getInputStream(zipEntry)) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        bout.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read " + zipEntry.getName() + " from input zip", e);
                }

                result.put(zipEntry.getName(), new InputFileEntry(zipEntry, bout.toByteArray()));
            }

            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open input zip " + inputFile, e);
        }
    }

    private static ZipFile openMinecraftJar(File inputFile, @Nullable File librariesFolder) throws IOException {
        ZipFile zf = new ZipFile(inputFile);

        // Before doing anything else, we will check if the input ZIP is a bundle
        try {
            BundleInfo bundleInfo = BundleInfo.of(zf);
            if (bundleInfo != null) {
                // Extract the bundle, close the original zip and return the inner jar
                File mainJar = bundleInfo.extractAndReturnPrimaryJar(zf, librariesFolder);
                mainJar.deleteOnExit();
                zf.close();
                return new ZipFile(mainJar);
            }
        } catch (Exception e) {
            zf.close();
            throw e;
        }

        return zf;
    }

    private static String detectDistribution(Map<String, InputFileEntry> entries) throws IOException {
        boolean hasClientEntrypoint = entries.containsKey("net/minecraft/client/main/Main.class");
        boolean hasServerEntrypoint = entries.containsKey("net/minecraft/server/Main.class");

        if (!hasClientEntrypoint && hasServerEntrypoint) {
            return DIST_SERVER;
        } else if (hasClientEntrypoint) {
            return DIST_CLIENT;
        } else {
            throw new IOException("Input has neither client nor server entrypoint.");
        }
    }

    private IMappingFile loadNeoformMappings(File neoformDataFile) {
        // Support both the version where we have to read the NeoForm file, and the version where we get the mapping file directly.
        if (neoformDataFile.getName().endsWith(".lzma")) {
            try (InputStream input = new FileInputStream(neoformDataFile);
                 InputStream stream = new LZMAInputStream(new BufferedInputStream(input))) {
                return IMappingFile.load(stream);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read LZMA-compressed NeoForm mappings " + neoformDataFile, e);
            }
        }

        try (ZipFile zipFile = new ZipFile(neoformDataFile)) {
            String mappingsPath = McpData.readDataEntry(zipFile, "mappings");

            if (mappingsPath.endsWith("/")) {
                throw new RuntimeException("The mappings in " + neoformDataFile + " point to a folder rather than a file: " + mappingsPath);
            }

            ZipEntry entry = zipFile.getEntry(mappingsPath);
            if (entry == null) {
                throw new RuntimeException("The mappings file " + mappingsPath + " in the NeoForm data file " + neoformDataFile + " is missing.");
            }

            try (InputStream in = zipFile.getInputStream(entry)) {
                return IMappingFile.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unexpected I/O exception when reading NeoForm data file " + neoformDataFile, e);
        }
    }

    private CompletableFuture<Map<String, InputFileEntry>> deobfuscateJar(Map<String, InputFileEntry> inputEntries, IMappingFile mappings) {
        long start = System.nanoTime();
        Renamer.Builder builder = Renamer.builder();
        builder.withJvmClasspath();
        // TODO: Log harmonization
        builder.add(Transformer.renamerFactory(mappings, true));
        builder.add(Transformer.parameterAnnotationFixerFactory());
        builder.add(Transformer.recordFixerFactory());
        builder.add(Transformer.identifierFixerFactory(IdentifierFixerConfig.ALL));
        builder.add(Transformer.sourceFixerFactory(SourceFixerConfig.JAVA));
        builder.add(Transformer.signatureStripperFactory(SignatureStripperConfig.ALL));

        Renamer renamer = builder.build();

        List<Transformer.Entry> entries = inputEntries.values().stream()
                .map(entry -> Transformer.Entry.ofFile(entry.name, entry.lastModified, entry.content))
                .collect(Collectors.toList());

        return renamer.run(entries, executorService)
                .thenApply(entryList -> {
                    try {
                        renamer.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to close renamer.", e);
                    }

                    Map<String, InputFileEntry> result = entryList.stream().map(entry -> new InputFileEntry(entry.getName(), entry.getTime(), entry.getData())).collect(Collectors.toMap(
                            InputFileEntry::getName,
                            e -> e,
                            (x, y) -> x,
                            LinkedHashMap::new
                    ));
                    logElapsed("deobfuscate jar", start);
                    return result;
                });
    }

    /**
     * Merges a client and server Jar file.
     *
     * <p>Note that this applies a heuristic that assumes files are not *different* between two versions,
     * and if they are, the client version is used.
     */
    private Map<String, InputFileEntry> merge(Map<String, InputFileEntry> entriesLeft, Map<String, InputFileEntry> entriesRight) {
        long start = System.nanoTime();

        try {
            String distLeft = detectDistribution(entriesLeft);
            String distRight = detectDistribution(entriesRight);
            if (distLeft.equals(distRight)) {
                throw new IOException("Cannot create a merged jar from the same two distributions: " + distLeft + ", " + distRight);
            }

            Map<String, InputFileEntry> serverEntries = DIST_SERVER.equals(distLeft) ? entriesLeft : entriesRight;
            Map<String, InputFileEntry> clientEntries = DIST_CLIENT.equals(distLeft) ? entriesLeft : entriesRight;

            // We simply take files from left/right, while recording this in the MANIFEST, which we replace
            Manifest mergedManifest = new Manifest();
            mergedManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            mergedManifest.getMainAttributes().putValue("Minecraft-Dists", DIST_CLIENT + " " + DIST_SERVER);
            Attributes clientOnlyAttrs = new Attributes(1);
            clientOnlyAttrs.putValue("Minecraft-Dist", DIST_CLIENT);
            Attributes serverOnlyAttrs = new Attributes(1);
            serverOnlyAttrs.putValue("Minecraft-Dist", DIST_SERVER);

            // Detect client-only files
            int clientExclusiveFiles = 0;
            for (Map.Entry<String, InputFileEntry> entry : clientEntries.entrySet()) {
                InputFileEntry serverEntry = serverEntries.remove(entry.getKey());
                if (serverEntry == null) {
                    mergedManifest.getEntries().put(entry.getKey(), clientOnlyAttrs);
                    clientExclusiveFiles++;
                    entry.setValue(addSideAnnotation(entry.getValue(), DIST_CLIENT));
                }
            }

            // Merge over server-only files, after applying side-annotations
            int serverExclusiveFiles = serverEntries.size();
            for (Map.Entry<String, InputFileEntry> entry : serverEntries.entrySet()) {
                mergedManifest.getEntries().put(entry.getKey(), serverOnlyAttrs);
                entry.setValue(addSideAnnotation(entry.getValue(), DIST_SERVER));
            }
            clientEntries.putAll(serverEntries);

            log("Merged " + clientEntries.size() + " entries (" + clientExclusiveFiles + " client-only, " + serverExclusiveFiles + " server-only)");

            logElapsed("merge jars", start);

            // Replace the MANIFEST.MF
            ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
            mergedManifest.write(manifestOut);
            clientEntries.put("META-INF/MANIFEST.MF", new InputFileEntry("META-INF/MANIFEST.MF", STABLE_TIMESTAMP, manifestOut.toByteArray()));

            return clientEntries;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to merge jars", e);
        }
    }

    private InputFileEntry addSideAnnotation(InputFileEntry entry, String dist) {
        if (!entry.name.endsWith(".class")) {
            return entry;
        }

        String annotationValue;
        if (DIST_CLIENT.equals(dist)) {
            annotationValue = "CLIENT"; // OnlyIn.CLIENT
        } else {
            annotationValue = "DEDICATED_SERVER"; // OnlyIn.DEDICATED_SERVER
        }

        return applyClassTransform(entry, classNode -> {
            classNode.visitAnnotation("Lnet/neoforged/api/distmarker/OnlyIn;", true)
                    .visitEnum("value", "Lnet/neoforged/api/distmarker/Dist;", annotationValue);
        });
    }

    private static class InputFileEntry {
        private final String name;
        private final long lastModified;
        private final byte[] content;

        public InputFileEntry(String name, long lastModified, byte[] content) {
            this.name = name;
            this.lastModified = lastModified;
            this.content = content;
        }

        public InputFileEntry(ZipEntry zipEntry, byte[] content) {
            this(zipEntry.getName(), zipEntry.getLastModifiedTime().toMillis(), content);
        }

        public String getName() {
            return name;
        }

        public long getLastModified() {
            return lastModified;
        }

        public byte[] getContent() {
            return content;
        }
    }

    private <T> CompletableFuture<T> supplyAsync(String task, ThrowingSupplier<T> callable) {
        return CompletableFuture.supplyAsync(wrapTask(task, callable), executorService);
    }

    private <T1, T2, R> CompletableFuture<R> allOfThenCompose(CompletableFuture<T1> f1, CompletableFuture<T2> f2, BiFunction<T1, T2, CompletableFuture<R>> combiner) {
        return CompletableFuture.allOf(f1, f2).thenCompose(unused -> combiner.apply(f1.join(), f2.join()));
    }

    private static <T> Supplier<T> wrapTask(String task, ThrowingSupplier<T> callable) {
        return () -> {
            long start = System.nanoTime();
            try {
                T result = callable.call();
                logElapsed(task, start);
                return result;
            } catch (IOException e) {
                throw new UncheckedIOException(task + " failed unexpectedly.", e);
            } catch (Exception e) {
                throw new RuntimeException(task + " failed unexpectedly.", e);
            }
        };
    }

    private static void logElapsed(String task, long start) {
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println(task + " [" + elapsed + "ms]");
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T call() throws Exception;
    }

    private InputFileEntry createMinecraftModManifest(String version) {
        String modManifest = "modLoader=\"minecraft\"\n" +
                "license=\"Minecraft EULA\"\n" +
                "[[mods]]\n" +
                "modId=\"minecraft\"\n" +
                "version=\"" + version + "\"\n" +
                "displayName=\"Minecraft\"\n" +
                "authors=\"Mojang Studios\"\n" +
                "description=\"\"\n";

        return new InputFileEntry(
                "META-INF/neoforge.mods.toml",
                STABLE_TIMESTAMP,
                modManifest.getBytes(StandardCharsets.UTF_8)
        );
    }
}
