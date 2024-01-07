package net.neoforged.cliutils.test;

import net.neoforged.cliutils.JarUtils;
import org.junit.jupiter.api.Test;

import java.io.File;

public class TestJarUtils {

    @Test
    void testCorrectCount() throws Exception {
        assert JarUtils.getFileCountInZip(new File(getClass().getResource("/jarutils.zip").toURI())) == 3;
    }
}
