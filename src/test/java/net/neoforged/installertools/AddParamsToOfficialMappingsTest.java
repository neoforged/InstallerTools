package net.neoforged.installertools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class AddParamsToOfficialMappingsTest {
    @TempDir
    Path tempDir;

    @Test
    void testMergeMappings() throws Exception {
        Path neoFormMappings = Paths.get(getClass().getResource("/neoform_mappings.tsrg").toURI());
        Path officialMappings = Paths.get(getClass().getResource("/official.txt").toURI());
        Path expectedMappings = Paths.get(getClass().getResource("/expected_merged_mappings.tsrg").toURI());
        Path outputPath = tempDir.resolve("output.txt");

        // This is what NeoForm calls
        new AddParamsToOfficialMappings().process(new String[]{
                "--official",
                officialMappings.toAbsolutePath().toString(),
                "--params",
                neoFormMappings.toAbsolutePath().toString(),
                "--output",
                outputPath.toString()
        });

        assertThat(outputPath).hasSameTextualContentAs(expectedMappings);
    }
}
