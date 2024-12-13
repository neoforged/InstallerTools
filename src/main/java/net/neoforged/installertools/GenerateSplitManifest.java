package net.neoforged.installertools;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.neoforged.srgutils.IMappingFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Given the client and server jar, this task generates a Jar Manifest detailing for every file that was only
 * present in one of the two jars, which jar it came from (using the "distribution" terminology).
 * It also adds a top-level attribute ("NeoForm-Minecraft-Dists: server client")
 */
public class GenerateSplitManifest extends Task {
    @Override
    public void process(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        OptionSpec<File> clientSpec = parser.accepts("client").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> serverSpec = parser.accepts("server").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> outputSpec = parser.accepts("output").withRequiredArg().ofType(File.class).required();
        OptionSpec<File> mappingsSpec = parser.accepts("mappings").withRequiredArg().ofType(File.class);

        OptionSet parsedArgs = parser.parse(args);

        File clientFile = parsedArgs.valueOf(clientSpec);
        File serverFile = parsedArgs.valueOf(serverSpec);
        File mappingsFile = parsedArgs.valueOf(mappingsSpec);
        File outputFile = parsedArgs.valueOf(outputSpec);

        IMappingFile mappings = mappingsFile != null ? IMappingFile.load(mappingsFile) : null;
        try (JarFile clientZip = new JarFile(clientFile);
             JarFile serverZip = new JarFile(serverFile)) {
            Set<String> clientFiles = getFileIndex(clientZip);
            clientFiles.remove(JarFile.MANIFEST_NAME);
            Set<String> serverFiles = getFileIndex(serverZip);
            serverFiles.remove(JarFile.MANIFEST_NAME);

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("NeoForm-Minecraft-Dists", "server client");

            addSourceDistEntries(clientFiles, serverFiles, "client", mappings, manifest);
            addSourceDistEntries(serverFiles, clientFiles, "server", mappings, manifest);

            try (OutputStream output = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                manifest.write(output);
            }
        }
    }

    private static void addSourceDistEntries(Set<String> distFiles,
                                             Set<String> otherDistFiles,
                                             String dist,
                                             IMappingFile mappings,
                                             Manifest manifest) {
        for (String file : distFiles) {
            if (!otherDistFiles.contains(file)) {
                Attributes fileAttr = new Attributes(1);
                fileAttr.putValue("NeoForm-Minecraft-Dist", dist);

                if (mappings != null && file.endsWith(".class")) {
                    file = mappings.remapClass(file.substring(0, file.length() - ".class".length())) + ".class";
                }
                manifest.getEntries().put(file, fileAttr);
            }
        }
    }

    private static Set<String> getFileIndex(ZipFile zipFile) {
        Set<String> result = new HashSet<>(zipFile.size());

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                result.add(entry.getName());
            }
        }

        return result;
    }
}
