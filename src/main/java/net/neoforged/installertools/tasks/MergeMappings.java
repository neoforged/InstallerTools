/*
 * InstallerTools
 * Copyright (c) 2019-2021.
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
package net.neoforged.installertools.tasks;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.neoforged.srgutils.IMappingFile;

import java.io.File;
import java.io.IOException;

public class MergeMappings extends Task {
    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> base = parser.accepts("base").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> reverseBase = parser.accepts("reverse-base");
        OptionSpec<File> merge = parser.accepts("merge").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> reverseMerge = parser.accepts("reverse-merge");
        OptionSpec<File> output = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        OptionSpec<Void> reverseOutput = parser.accepts("reverse-output");

        try {
            OptionSet options = parser.parse(args);

            merge(
                    options.valueOf(base),
                    options.has(reverseBase),
                    options.valueOf(merge),
                    options.has(reverseMerge),
                    options.valueOf(output),
                    options.has(reverseOutput)
            );
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            error("Please provide correct parameters");
        }
    }

    private void merge(File baseFile, boolean reverseBase,
                       File mergeFile, boolean reverseMerge,
                       File outputFile, boolean reverseOutput) throws IOException {
        log("Base Mappings: " + baseFile);
        log("Reverse Base Mappings: " + reverseBase);
        log("Merge Mappings: " + mergeFile);
        log("Reverse Merge Mappings: " + reverseMerge);
        log("Output: " + outputFile);
        log("Reverse Output: " + reverseOutput);

        IMappingFile baseMappings = IMappingFile.load(baseFile);
        if (reverseBase) {
            baseMappings = baseMappings.reverse();
        }
        IMappingFile mergeMappings = IMappingFile.load(mergeFile);
        if (reverseMerge) {
            mergeMappings = mergeMappings.reverse();
        }
        IMappingFile outputMappings = baseMappings.merge(mergeMappings);
        if (reverseOutput) {
            outputMappings = outputMappings.reverse();
        }
        outputMappings.write(outputFile.toPath(), IMappingFile.Format.TSRG2, false);
    }
}
