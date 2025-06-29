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
import com.google.gson.GsonBuilder;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.neoforged.installertools.util.ManifestJson;
import net.neoforged.installertools.util.VersionJson;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;

public class DownloadMojmaps extends Task {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<String> versionO = parser.accepts("version").withRequiredArg().ofType(String.class).required();
        OptionSpec<String> sideO = parser.accepts("side").withRequiredArg().ofType(String.class).required();
        OptionSpec<File> outputO = parser.accepts("output").withRequiredArg().ofType(File.class).required();

        try {
            OptionSet options = parser.parse(args);

            String mcversion = options.valueOf(versionO);
            String side = options.valueOf(sideO);
            File output = options.valueOf(outputO);

            log("MC Version: " + mcversion);
            log("Side:       " + side);
            log("Output:     " + output);

            if (output.exists() && !output.delete())
                error("Could not delete output file: " + output);

            if (!output.getParentFile().exists() && !output.getParentFile().mkdirs())
                error("Could not make output folders: " + output.getParentFile());

            try (InputStream manIn = new URL(MANIFEST_URL).openStream()) {
                URL url = GSON.fromJson(new InputStreamReader(manIn), ManifestJson.class).getUrl(mcversion);
                if (url == null)
                    error("Missing version from manifest: " + mcversion);

                try (InputStream verIn = url.openStream()) {
                    VersionJson json = VersionJson.load(verIn);
                    if (json == null)
                        error("Missing Minecraft version JSON from URL " + url);

                    VersionJson.Download download = json.downloads.get(side + "_mappings");
                    if (download == null || download.url == null)
                        error("Missing download info for " + side + " mappings");

                    final URLConnection connection = download.url.openConnection();
                    Files.copy(PROGRESS.wrapDownload(connection), output.toPath());
                    log("Downloaded Mojang mappings for " + mcversion);
                }
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
