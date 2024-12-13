package net.neoforged.installertools;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateSplitManifestTest {
    @TempDir
    File tempDir;
    Manifest obfManifest;
    Manifest namedManifest;

    @BeforeEach
    void setUp() throws Exception {
        // Generate two JarFiles
        File clientFile = new File(tempDir, "client.jar");
        File serverFile = new File(tempDir, "server.jar");
        File mappingsFile = new File(tempDir, "mappings.tsrg");
        File outputFile = new File(tempDir, "output.mf");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(clientFile))) {
            jos.putNextEntry(new ZipEntry("ignored_server_directory/"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("obf_client_only.class"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("obf_common_class.class"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("unobf/client_only.class"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("asset/some_asset.json"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("data/some_recipe.json"));
            jos.closeEntry();
        }

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(serverFile))) {
            jos.putNextEntry(new ZipEntry("ignored_server_directory/"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("obf_server_only.class"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("obf_common_class.class"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("unobf/server_only.class"));
            jos.closeEntry();
            jos.putNextEntry(new ZipEntry("data/some_recipe.json"));
            jos.closeEntry();
        }

        new GenerateSplitManifest().process(new String[]{
                "--client", clientFile.getAbsolutePath(),
                "--server", serverFile.getAbsolutePath(),
                "--output", outputFile.getAbsolutePath()
        });

        obfManifest = new Manifest();
        try (InputStream in = new FileInputStream(outputFile)) {
            obfManifest.read(in);
        }

        Files.write(mappingsFile.toPath(), Arrays.asList(
                "tsrg2 obf srg",
                "obf_client_only pkg/NamedClientOnlyClass",
                "obf_server_only pkg/NamedServerOnlyClass"
        ));

        new GenerateSplitManifest().process(new String[]{
                "--client", clientFile.getAbsolutePath(),
                "--server", serverFile.getAbsolutePath(),
                "--output", outputFile.getAbsolutePath(),
                "--mappings", mappingsFile.getAbsolutePath()
        });

        namedManifest = new Manifest();
        try (InputStream in = new FileInputStream(outputFile)) {
            namedManifest.read(in);
        }
    }

    @Test
    void testMainAttributes() {
        // Manifest-Version should be the only main attribute
        assertThat(obfManifest.getMainAttributes())
                .extracting(this::getEntries)
                .asInstanceOf(InstanceOfAssertFactories.LIST)
                .containsOnly(
                        "Manifest-Version: 1.0",
                        "NeoForm-Minecraft-Dists: server client"
                );
    }

    @Test
    void testObfContainsOnlyEntriesForDistExclusiveFiles() {
        // The common entries should not be in the manifest
        assertThat(obfManifest.getEntries().keySet())
                .containsOnly(
                        "unobf/server_only.class",
                        "unobf/client_only.class",
                        "obf_client_only.class",
                        "obf_server_only.class",
                        "asset/some_asset.json"
                );
    }

    @Test
    void testObfEntryAttributes() {
        assertThat(obfManifest.getEntries().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> getEntries(e.getValue())
        )))
                .containsOnly(
                        MapEntry.entry("unobf/server_only.class", Arrays.asList("NeoForm-Minecraft-Dist: server")),
                        MapEntry.entry("unobf/client_only.class", Arrays.asList("NeoForm-Minecraft-Dist: client")),
                        MapEntry.entry("obf_client_only.class", Arrays.asList("NeoForm-Minecraft-Dist: client")),
                        MapEntry.entry("obf_server_only.class", Arrays.asList("NeoForm-Minecraft-Dist: server")),
                        MapEntry.entry("asset/some_asset.json", Arrays.asList("NeoForm-Minecraft-Dist: client"))
                );
    }

    @Test
    void testNamedContainsOnlyEntriesForDistExclusiveFiles() {
        // The common entries should not be in the manifest
        assertThat(namedManifest.getEntries().keySet())
                .containsOnly(
                        "unobf/server_only.class",
                        "unobf/client_only.class",
                        "pkg/NamedClientOnlyClass.class",
                        "pkg/NamedServerOnlyClass.class",
                        "asset/some_asset.json"
                );
    }

    @Test
    void testNamedEntryAttributes() {
        assertThat(namedManifest.getEntries().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> getEntries(e.getValue())
        )))
                .containsOnly(
                        MapEntry.entry("unobf/server_only.class", Arrays.asList("NeoForm-Minecraft-Dist: server")),
                        MapEntry.entry("unobf/client_only.class", Arrays.asList("NeoForm-Minecraft-Dist: client")),
                        MapEntry.entry("pkg/NamedClientOnlyClass.class", Arrays.asList("NeoForm-Minecraft-Dist: client")),
                        MapEntry.entry("pkg/NamedServerOnlyClass.class", Arrays.asList("NeoForm-Minecraft-Dist: server")),
                        MapEntry.entry("asset/some_asset.json", Arrays.asList("NeoForm-Minecraft-Dist: client"))
                );
    }

    private List<String> getEntries(Map<Object, Object> attributes) {
        List<String> result = new ArrayList<>(attributes.size());
        for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
            result.add(entry.getKey() + ": " + entry.getValue());
        }
        return result;
    }
}
