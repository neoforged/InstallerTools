package net.neoforged.cliutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class JarUtils {
    public static int getFileCountInZip(File path) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(path.toPath(), null); final Stream<Path> count = Files.find(fs.getPath("/"), Integer.MAX_VALUE, (p, basicFileAttributes) -> basicFileAttributes.isRegularFile())) {
            final long c = count.count();
            return c > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) c;
        }
    }
}
