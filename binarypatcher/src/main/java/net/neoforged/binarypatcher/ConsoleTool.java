/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.binarypatcher;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.OptionSpecBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public class ConsoleTool {
    public static final boolean DEBUG = Boolean.getBoolean("net.neoforged.binarypatcher.debug");

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        //Mode flags
        OptionSpecBuilder diffO = parser.accepts("diff");
        OptionSpecBuilder patchO = parser.accepts("patch");
        OptionSpecBuilder listO = parser.accepts("list");

        parser.mutuallyExclusive(diffO, patchO, listO);

        // Diff arguments
        OptionSpec<File> clientBaseO = parser.accepts("base-client").availableIf(diffO).withRequiredArg().ofType(File.class);
        OptionSpec<File> serverBaseO = parser.accepts("base-server").availableIf(diffO).withRequiredArg().ofType(File.class);
        OptionSpec<File> joinedBaseO = parser.accepts("base-joined").availableIf(diffO).withRequiredArg().ofType(File.class);
        OptionSpec<File> clientModifiedO = parser.accepts("modified-client").availableIf(diffO, clientBaseO).requiredIf(clientBaseO).withRequiredArg().ofType(File.class);
        OptionSpec<File> serverModifiedO = parser.accepts("modified-server").availableIf(diffO, serverBaseO).requiredIf(serverBaseO).withRequiredArg().ofType(File.class);
        OptionSpec<File> joinedModifiedO = parser.accepts("modified-joined").availableIf(diffO, joinedBaseO).requiredIf(joinedBaseO).withRequiredArg().ofType(File.class);
        OptionSpec<Void> optimizeConstantPoolO = parser.accepts("optimize-constantpool").availableIf(diffO);
        OptionSpec<String> includeO = parser.accepts("include").availableIf(diffO).withRequiredArg().ofType(String.class);
        OptionSpec<String> excludeO = parser.accepts("exclude").availableIf(diffO).withRequiredArg().ofType(String.class);

        // Apply arguments
        OptionSpec<File> patchesO = parser.accepts("patches").requiredIf(patchO, listO).withRequiredArg().ofType(File.class);
        OptionSpec<File> baseFileO = parser.accepts("base").requiredIf(patchO).withRequiredArg().ofType(File.class);
        OptionSpec<PatchBase> baseTypeO = parser.accepts("base-type").requiredIf(patchO).withRequiredArg().ofType(PatchBase.class);

        // Shared arguments
        OptionSpec<File> outputO = parser.accepts("output").availableIf(diffO, patchO).requiredIf(diffO, patchO).withRequiredArg().ofType(File.class);

        OptionSpec<Void> helpO = parser.acceptsAll(Arrays.asList("?", "help")).forHelp();

        try {
            OptionSet options = parser.parse(args);

            if (options.has(helpO)) {
                parser.printHelpOn(System.out);
                return;
            }

            if (options.has(listO)) {
                listPatchBundle(options.valueOf(patchesO));
                return;
            }

            File output = options.valueOf(outputO).getAbsoluteFile();

            if (output.exists() && !output.delete())
                err("Could not delete output file: " + output);

            if (!output.getParentFile().exists() && !output.getParentFile().mkdirs())
                err("Could not make output folders: " + output.getParentFile());

            if (options.has(diffO)) {
                boolean optimizeConstantPool = options.has(optimizeConstantPoolO);
                Predicate<String> pathFilter = createPathFilter(options.valuesOf(includeO), options.valuesOf(excludeO));

                Map<PatchBase, File> baseFiles = new EnumMap<>(PatchBase.class);
                Map<PatchBase, File> modifiedFiles = new EnumMap<>(PatchBase.class);
                File clientBase = options.valueOf(clientBaseO);
                if (clientBase != null) {
                    baseFiles.put(PatchBase.CLIENT, clientBase);
                    modifiedFiles.put(PatchBase.CLIENT, Objects.requireNonNull(options.valueOf(clientModifiedO)));
                }
                File serverBase = options.valueOf(serverBaseO);
                if (serverBase != null) {
                    baseFiles.put(PatchBase.SERVER, serverBase);
                    modifiedFiles.put(PatchBase.SERVER, Objects.requireNonNull(options.valueOf(serverModifiedO)));
                }
                File joinedBase = options.valueOf(joinedBaseO);
                if (joinedBase != null) {
                    baseFiles.put(PatchBase.JOINED, joinedBase);
                    modifiedFiles.put(PatchBase.JOINED, Objects.requireNonNull(options.valueOf(joinedModifiedO)));
                }
                if (baseFiles.isEmpty()) {
                    err("A base file must be given via any combination of --base-client, --base-server, --base-joined");
                }

                log("Generating: ");
                log("  Bases:  " + baseFiles);
                log("  Modified:  " + modifiedFiles);
                log("  Output:  " + output);
                log("Diff Options: ");
                log("  Optimize Constant Table: " + optimizeConstantPool);

                DiffOptions diffOptions = new DiffOptions();
                diffOptions.setOptimizeConstantPool(optimizeConstantPool);
                diffOptions.setPathFilter(pathFilter);
                Generator.createPatchBundle(
                        baseFiles,
                        modifiedFiles,
                        output,
                        diffOptions);

            } else if (options.has(patchO)) {
                File baseFile = options.valueOf(baseFileO);
                PatchBase baseType = options.valueOf(baseTypeO);
                List<File> patches = options.valuesOf(patchesO);

                long start = System.currentTimeMillis();

                log("Applying: ");
                log("  Base:      " + baseFile);
                log("  Base Type: " + baseType);
                log("  Output:    " + output);
                log("  Patches:   " + patches);

                long startLoadingPatches = System.currentTimeMillis();
                debug("Loaded patches in " + (System.currentTimeMillis() - startLoadingPatches) + "ms");

                Patcher.patch(baseFile, baseType, patches, output, ConsoleTool::debug);

                debug("Completed in " + (System.currentTimeMillis() - start) + "ms");
            } else {
                parser.printHelpOn(System.out);
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private static Predicate<String> createPathFilter(List<String> includes, List<String> excludes) {
        Predicate<String> includeFilter = includes.isEmpty() ? path -> true : Util.createPathFilter(includes);
        Predicate<String> excludeFilter = excludes.isEmpty() ? path -> false : Util.createPathFilter(excludes);

        return excludeFilter.negate().and(includeFilter);
    }

    private static void listPatchBundle(File patchBundleFile) throws IOException {
        List<String[]> rows = new ArrayList<>();

        try (PatchBundleReader reader = new PatchBundleReader(patchBundleFile)) {
            List<PatchBase> bases = new ArrayList<>(reader.getSupportedBaseTypes());
            int colCount = 4 + bases.size();

            // Create a header row (both to be used for determining column width and printing it)
            String[] headerRow = new String[colCount];
            headerRow[0] = "Path";
            headerRow[1] = "Operation";
            headerRow[2] = "Base Checksum";
            headerRow[3] = "Size";
            for (int i = 0; i < bases.size(); i++) {
                headerRow[4 + i] = bases.get(i).name();
            }
            rows.add(headerRow);

            List<Object[]> patchSizes = new ArrayList<>(reader.getEntryCount());

            for (Patch patch : reader) {
                String[] col = new String[colCount];
                col[0] = patch.getTargetPath();
                col[1] = patch.getOperation().name();
                col[2] = patch.getOperation() == PatchOperation.MODIFY ? Long.toHexString(patch.getBaseChecksumUnsigned()) : "";
                col[3] = patch.getOperation() != PatchOperation.REMOVE ? String.valueOf(patch.getData().length) : "";
                for (int i = 0; i < bases.size(); i++) {
                    col[4 + i] = patch.getBaseTypes().contains(bases.get(i)) ? "X" : "";
                }
                rows.add(col);

                // Record the patch size so we can print a report on the largest patches
                if (patch.getOperation() == PatchOperation.MODIFY) {
                    patchSizes.add(new Object[]{patch.getTargetPath(), patch.getData().length});
                }
            }

            printMarkdownTable(rows);
            System.out.println();

            // Sort by patch size in descending order. Skip CREATE since their size is obvious.
            patchSizes.sort(Comparator.comparingInt((Object[] row) -> (int) row[1]).reversed());
            System.out.println("Largest MODIFY patches:");
            System.out.println();
            List<String[]> maxSizeRows = new ArrayList<>(11);
            maxSizeRows.add(new String[]{"Target Path", "Size"});
            for (int i = 0; i < Math.min(10, patchSizes.size()); i++) {
                maxSizeRows.add(new String[]{patchSizes.get(i)[0].toString(), patchSizes.get(i)[1].toString()});
            }
            printMarkdownTable(maxSizeRows);

        }
    }

    private static void printMarkdownTable(List<String[]> rows) {
        // Determine col widths
        int[] colWidths = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                colWidths[i] = Math.max(colWidths[i], row[i].length());
            }
        }

        // Print a nice Markdown Table
        boolean printingHeaderRow = true;
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                String s = row[i];
                System.out.print("| ");
                System.out.print(s);
                System.out.print(repeat(' ', colWidths[i] - s.length()));
                System.out.print(" ");
            }
            System.out.print(" |");
            System.out.println();
            if (printingHeaderRow) {
                for (int colWidth : colWidths) {
                    System.out.print("| ");
                    System.out.print(repeat('-', colWidth));
                    System.out.print(" ");
                }
                System.out.println(" |");
                printingHeaderRow = false;
            }
        }
    }

    private static String repeat(char ch, int repeat) {
        char[] buffer = new char[repeat];
        Arrays.fill(buffer, ch);
        return String.valueOf(buffer);
    }

    public static void log(String message) {
        System.out.println(message);
    }

    public static void debug(String message) {
        if (DEBUG) {
            log(message);
        }
    }

    public static void err(String message) {
        System.out.println(message);
        throw new IllegalStateException(message);
    }
}
