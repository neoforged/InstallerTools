/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.neoforged.binarypatcher;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class ConsoleTool {
    public static final boolean DEBUG = Boolean.getBoolean("net.neoforged.binarypatcher.debug");
    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        // Diff arguments
        OptionSpec<Void> diffO = parser.accepts("diff").availableUnless("patch");
        OptionSpec<File> clientBaseO = parser.accepts("base-client").availableIf(diffO).withRequiredArg().ofType(File.class);
        OptionSpec<File> serverBaseO = parser.accepts("base-server").availableIf(diffO).withRequiredArg().ofType(File.class);
        OptionSpec<File> joinedBaseO = parser.accepts("base-joined").availableIf(diffO).withRequiredArg().ofType(File.class);
        OptionSpec<File> modifiedO = parser.accepts("modified").availableIf(diffO).withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> optimizeConstantPoolO = parser.accepts("optimize-constantpool").availableIf(diffO);

        // Apply arguments
        OptionSpec<File> applyO = parser.accepts("apply").availableUnless(diffO).withRequiredArg().ofType(File.class);
        OptionSpec<File> cleanO = parser.accepts("base").availableIf(applyO).withRequiredArg().ofType(File.class).required();
        OptionSpec<PatchBase> baseTypeO = parser.accepts("base-type").availableIf(applyO).withRequiredArg().ofType(PatchBase.class).required();

        // Shared arguments
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            File output = options.valueOf(outputO).getAbsoluteFile();
            boolean optimizeConstantPool = options.has(optimizeConstantPoolO);

            if (output.exists() && !output.delete())
                err("Could not delete output file: " + output);

            if (!output.getParentFile().exists() && !output.getParentFile().mkdirs())
                err("Could not make output folders: " + output.getParentFile());

            if (options.has(diffO)) {
                File modifiedFile = options.valueOf(modifiedO);

                Map<PatchBase, File> baseFiles = new EnumMap<>(PatchBase.class);
                File clientBase = options.valueOf(clientBaseO);
                if (clientBase != null) {
                    baseFiles.put(PatchBase.CLIENT, clientBase);
                }
                File serverBase = options.valueOf(serverBaseO);
                if (serverBase != null) {
                    baseFiles.put(PatchBase.SERVER, serverBase);
                }
                File joinedBase = options.valueOf(joinedBaseO);
                if (joinedBase != null) {
                    baseFiles.put(PatchBase.JOINED, joinedBase);
                }
                if (baseFiles.isEmpty()) {
                    err("A base file must be given via any combination of --base-client, --base-server, --base-joined");
                }

                log("Generating: ");
                log("  Bases:  " + baseFiles);
                log("  Modified:  " + modifiedFile);
                log("  Output:  " + output);
                log("Diff Options: ");
                log("  Optimize Constant Table: " + optimizeConstantPool);

                DiffOptions diffOptions = new DiffOptions();
                diffOptions.setOptimizeConstantPool(optimizeConstantPool);
                Generator.createPatchBundle(
                        baseFiles,
                        modifiedFile,
                        output,
                        diffOptions);

            } else if (options.has(applyO)) {
                File baseFile = options.valueOf(cleanO);
                PatchBase baseType = options.valueOf(baseTypeO);
                List<File> patches = options.valuesOf(applyO);

                long start = System.currentTimeMillis();

                log("Applying: ");
                log("  Base:      " + baseFile);
                log("  Base Type: " + baseType);
                log("  Output:    " + output);
                log("  Patches:   " + patches);

                long startLoadingPatches = System.currentTimeMillis();
                debug("Loaded patches in " + (System.currentTimeMillis() - startLoadingPatches) + "ms");

                Patcher.patch(baseFile, baseType, patches, output);

                debug("Completed in " + (System.currentTimeMillis() - start) + "ms");

            } else {
                parser.printHelpOn(System.out);
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
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
