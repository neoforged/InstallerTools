package net.neoforged.installertools;

import com.nothome.delta.GDiffPatcher;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;
import net.neoforged.art.api.IdentifierFixerConfig;
import net.neoforged.art.api.Renamer;
import net.neoforged.art.api.SignatureStripperConfig;
import net.neoforged.art.api.SourceFixerConfig;
import net.neoforged.art.api.Transformer;
import net.neoforged.binarypatcher.Patch;
import net.neoforged.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ProcessMinecraftJar extends Task {
    public static final long NEW_ENTRY_ZIPTIME = 628041600000L;

    @Override
    public void process(String[] args) throws IOException {

        long start = System.nanoTime();

        OptionParser parser = new OptionParser();
        OptionSpecBuilder clientArgBuilder = parser.accepts("client", "The original Minecraft client jar. Either this or --server must be given.");
        OptionSpecBuilder serverArgBuilder = parser.accepts("server", "The original Minecraft server jar. Either this or --server must be given.");
        OptionSpec<File> clientArg = clientArgBuilder.availableUnless("server").requiredUnless("server").withOptionalArg().ofType(File.class);
        OptionSpec<File> clientMappingsArg = parser.accepts("client-mappings", "The original Minecraft mappings for the client jar. Required when --client is given.").availableIf("client").requiredIf("client").withRequiredArg().ofType(File.class);
        OptionSpec<File> serverArg = serverArgBuilder.availableUnless("client").requiredUnless("client").withOptionalArg().ofType(File.class);
        OptionSpec<File> serverMappingsArg = parser.accepts("server-mappings", "The original Minecraft mappings for the server jar. Required when --server is given.").availableIf("client").requiredIf("server").withRequiredArg().ofType(File.class);
        OptionSpec<File> neoformDataArg = parser.accepts("neoform-data", "The NeoForm data file used for getting SRG parameter names.").withRequiredArg().ofType(File.class);
        OptionSpec<File> outputArg = parser.accepts("output", "Where the resulting processed jar is written to.").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputLibrariesArg = parser.accepts("extract-libraries-to", "Path to an on-disk directory where any embedded libraries will be written to. Applies to the dedicated server.").withOptionalArg().ofType(File.class);
        OptionSpec<File> patchesArchiveArg = parser.accepts("apply-patches", "Path to a binpatch file with patches to apply").withOptionalArg().ofType(File.class);

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        File inputFile;
        File inputMappingsFile;
        InputDist inputDist;
        File clientFile = options.valueOf(clientArg);
        File serverFile = options.valueOf(serverArg);
        if (clientFile != null) {
            inputDist = InputDist.CLIENT;
            inputFile = clientFile;
            inputMappingsFile = Objects.requireNonNull(options.valueOf(clientMappingsArg), "client mappings");
        } else if (serverFile != null) {
            inputDist = InputDist.SERVER;
            inputFile = serverFile;
            inputMappingsFile = Objects.requireNonNull(options.valueOf(serverMappingsArg), "server mappings");
        } else {
            throw new IllegalStateException("Either client or server are missing.");
        }

        File librariesFolder = options.valueOf(outputLibrariesArg);
        File outputFile = options.valueOf(outputArg);
        File neoformDataFile = options.valueOf(neoformDataArg);
        File patchesArchiveFile = options.valueOf(patchesArchiveArg);

        processZip(inputFile, inputDist, inputMappingsFile, outputFile, librariesFolder, neoformDataFile, patchesArchiveFile);

        logElapsed("overall work", start);
    }

    private void processZip(File inputFile,
                            InputDist inputDist,
                            File inputMappingsFile,
                            File outputFile,
                            @Nullable File librariesFolder,
                            @Nullable File neoformDataFile,
                            @Nullable File patchesArchiveFile) {
        CompletableFuture<IMappingFile> mappings = supplyAsync("load mappings", () -> loadMappings(inputMappingsFile));
        if (neoformDataFile != null) {
            CompletableFuture<IMappingFile> parameterMappings = supplyAsync("load parameter mappings", () -> loadNeoformMappings(neoformDataFile));
            mappings = mappings.thenCombineAsync(parameterMappings, this::mergeMappings);
        }
        // TODO if dist == server -> extract server zip, unpack libraries, then read from temp-file or such
        CompletableFuture<Map<String, InputFileEntry>> outputEntries = supplyAsync("load input zip", () -> loadInputZip(inputFile));

        outputEntries = outputEntries.thenCombineAsync(mappings, this::deobfuscateJar);

        // If patches are supplied, apply them
        if (patchesArchiveFile != null) {
            CompletableFuture<List<Patch>> patches = supplyAsync("load patches", () -> loadPatches(patchesArchiveFile, null));

            outputEntries = outputEntries.thenCombineAsync(patches, this::applyPatches);
        }

        CompletableFuture<Void> outputFileFuture = outputEntries.thenAccept(outputFileEntries -> writeOutputFile(outputFile, outputFileEntries.values()));

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

    private List<Patch> loadPatches(File patchesArchiveFile, String prefix) throws IOException {
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

    private static Map<String, InputFileEntry> loadInputZip(File inputFile) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        try (ZipFile zipFile = new ZipFile(inputFile)) {
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

    private IMappingFile loadNeoformMappings(File neoformDataFile) {
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

    private Map<String, InputFileEntry> deobfuscateJar(Map<String, InputFileEntry> inputEntries, IMappingFile mappings) {
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

        List<Transformer.Entry> newEntries = renamer.run(entries);

        Map<String, InputFileEntry> result = newEntries.stream().map(entry -> new InputFileEntry(entry.getName(), entry.getTime(), entry.getData())).collect(Collectors.toMap(
                InputFileEntry::getName,
                e -> e,
                (x, y) -> x,
                LinkedHashMap::new
        ));
        logElapsed("deobfuscate jar", start);
        return result;
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

    enum InputDist {
        CLIENT,
        SERVER
    }

    private static <T> CompletableFuture<T> supplyAsync(String task, ThrowingSupplier<T> callable) {
        return CompletableFuture.supplyAsync(wrapTask(task, callable));
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
}
