package net.neoforged.cliutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

public class JarUtils {
    public static int getFileCountInZip(File path) throws IOException {
        long c = 0;
        try (FileSystem fs = FileSystems.newFileSystem(path.toPath(), null)) {
            final Iterator<Path> roots = fs.getRootDirectories().iterator();
            while (roots.hasNext()) {
                try (final Stream<Path> count = Files.find(roots.next(), Integer.MAX_VALUE, (p, basicFileAttributes) -> basicFileAttributes.isRegularFile())) {
                    c += count.count();
                }
            }
        }
        return c > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) c;
    }
}
