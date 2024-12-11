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
package net.neoforged.installertools;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.neoforged.srgutils.IMappingFile;

import java.io.File;
import java.io.IOException;

/**
 * Takes a file containing the official Mojang mappings and adds mergers a TSRG mappings file into it with the purpose
 * of adding parameter names.
 * The result is written out as a TSRG file mapping obfuscated to named.
 */
public class AddParamsToOfficialMappings extends Task {
    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> official = parser.accepts("official").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> paramsTsrg = parser.accepts("params").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputTsrg = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            mergeParams(
                    options.valueOf(official),
                    options.valueOf(paramsTsrg),
                    options.valueOf(outputTsrg)
            );
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            error("Please provide correct parameters");
        }
    }

    private void mergeParams(File officialFile, File paramsTsrgFile, File outputTsrgFile) throws IOException {
        log("Official Mappings: " + officialFile);
        log("Parameter Mappings: " + paramsTsrgFile);
        log("Output: " + outputTsrgFile);

        IMappingFile officialMappings = IMappingFile.load(officialFile).reverse();
        IMappingFile paramMappings = IMappingFile.load(paramsTsrgFile);
        IMappingFile merged = officialMappings.merge(paramMappings);
        merged.write(outputTsrgFile.toPath(), IMappingFile.Format.TSRG2, false);
    }
}
