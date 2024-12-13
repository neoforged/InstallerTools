/*
 * InstallerTools
 * Copyright (c) 2019-2024.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class MergeMappingsTest {
    @TempDir
    Path tempDir;

    @Test
    void testMergeMappings() throws Exception {
        Path neoFormMappings = Paths.get(getClass().getResource("/neoform_mappings.tsrg").toURI());
        Path officialMappings = Paths.get(getClass().getResource("/official.txt").toURI());
        Path expectedMappings = Paths.get(getClass().getResource("/expected_merged_mappings.tsrg").toURI());
        Path outputPath = tempDir.resolve("output.txt");

        // This is what NeoForm calls
        new MergeMappings().process(new String[]{
                "--base",
                officialMappings.toAbsolutePath().toString(),
                "--reverse-base",
                "--merge",
                neoFormMappings.toAbsolutePath().toString(),
                "--output",
                outputPath.toString()
        });

        assertThat(outputPath).hasSameTextualContentAs(expectedMappings);
    }
}
