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
package net.neoforged.installertools.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class JarUtils {
    public static int getFileCountInZip(File path) throws IOException {
        long c = 0;
        try (FileSystem fs = FileSystems.newFileSystem(path.toPath(), null)) {
            for (Path root : fs.getRootDirectories()) {
                try (final Stream<Path> count = Files.find(root, Integer.MAX_VALUE, (p, basicFileAttributes) -> basicFileAttributes.isRegularFile())) {
                    c += count.count();
                }
            }
        }
        return c > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) c;
    }
}
