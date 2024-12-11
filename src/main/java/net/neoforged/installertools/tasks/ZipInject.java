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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipInject {
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        OptionParser parser = new OptionParser();
        OptionSpec<File> baseO = parser.accepts("base", "The base zip to inject into").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputO = parser.accepts("output", "The location of the output zip").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> injectO = parser.accepts("inject", "A zip or directory to inject").withRequiredArg().ofType(File.class);
        OptionSpec<String> sourcePrefixO = parser.accepts("path-prefix", "A prefix to strip from source file paths").withRequiredArg();
        OptionSpec<String> packageInfoPackagesO = parser.accepts("inject-package-info", "A prefix that packages that should contain a package-info have").withRequiredArg().ofType(String.class);

        try {
            OptionSet options = parser.parse(args);
            List<File> injects = options.valuesOf(injectO);
            List<Path> injectRoots = new ArrayList<>();
            for (File inject : injects) {
                if (!inject.isDirectory()) {
                    if (!inject.exists()) {
                        throw new IllegalArgumentException("Injection path " + inject + " doesn't exist");
                    }
                    try {
                        FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + inject.toURI()), Collections.emptyMap());
                        injectRoots.add(fs.getRootDirectories().iterator().next());
                    } catch (Exception exception) {
                        throw new IllegalArgumentException("Injection path " + inject + " is not a zip", exception);
                    }
                } else {
                    injectRoots.add(inject.toPath().toAbsolutePath());
                }
            }

            String packageInfoTemplate = null;
            if (options.has(packageInfoPackagesO)) {
                packageInfoTemplate = findPackageInfoTemplate(injectRoots);
            }

            String sourcePrefix = options.has(sourcePrefixO) ? options.valueOf(sourcePrefixO) : null;

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(options.valueOf(outputO).toPath()))) {
                copyInputZipContent(options.valueOf(baseO).toPath(), zos, packageInfoTemplate, options.valuesOf(packageInfoPackagesO));

                for (Path folder : injectRoots) {
                    try (Stream<Path> stream = Files.walk(folder).sorted()) {
                        stream.filter(Files::isRegularFile).forEach(path -> {
                            String outputPath = folder.relativize(path).toString().replace('\\', '/');
                            try {
                                if (sourcePrefix != null && outputPath.startsWith(sourcePrefix)) {
                                    outputPath = outputPath.substring(sourcePrefix.length());
                                    if (outputPath.isEmpty()) return;
                                } else if (outputPath.equals("package-info-template.java")) {
                                    // Don't include the template file in the output
                                    return;
                                }
                                zos.putNextEntry(new ZipEntry(outputPath));
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    }
                }
            }

            // Now close the opened zip file systems
            for (Path injectRoot : injectRoots) {
                if (injectRoot.getFileSystem().provider().getScheme().equals("jar")) {
                    injectRoot.getFileSystem().close();
                }
            }
        } catch (OptionException e) {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }

    private static String findPackageInfoTemplate(List<Path> roots) throws IOException {
        // Try to find a package-info-template.java
        for (Path injectedSource : roots) {
            Path subPath = injectedSource.resolve("package-info-template.java");
            if (Files.isRegularFile(subPath)) {
                return new String(Files.readAllBytes(subPath), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void copyInputZipContent(Path inputZipFile, ZipOutputStream zos, String packageInfoTemplateContent, List<String> packagePrefixes) throws IOException {
        Set<String> visited = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(inputZipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(entry);
                copy(zis, zos);
                zos.closeEntry();

                if (packageInfoTemplateContent != null) {
                    String pkg = entry.isDirectory() && !entry.getName().endsWith("/") ? entry.getName() : (entry.getName().indexOf('/') == -1 ? "" : entry.getName().substring(0, entry.getName().lastIndexOf('/')));
                    if (visited.add(pkg)) {
                        for (String prefix : packagePrefixes) {
                            if (pkg.startsWith(prefix)) {
                                zos.putNextEntry(new ZipEntry(pkg + "/package-info.java"));
                                zos.write(packageInfoTemplateContent.replace("{PACKAGE}", pkg.replaceAll("/", ".")).getBytes(StandardCharsets.UTF_8));
                                zos.closeEntry();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1)
            output.write(buffer, 0, read);
    }
}
