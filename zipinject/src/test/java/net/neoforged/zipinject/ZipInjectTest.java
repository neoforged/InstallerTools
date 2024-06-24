package net.neoforged.zipinject;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipInjectTest {
    @TempDir
    File tempDir;

    @Test
    void testInjectDir() throws Exception {
        final Path injectDir = tempDir.toPath().resolve("injection");
        Files.createDirectories(injectDir);

        Files.write(injectDir.resolve("a.txt"), "file a".getBytes(StandardCharsets.UTF_8));
        Files.write(injectDir.resolve("b.txt"), "file b".getBytes(StandardCharsets.UTF_8));

        final File base = new File(tempDir, "base.zip");
        writeJar(base, "c.txt", "file c", "d.txt", "file d");

        final File out = new File(tempDir, "out.zip");
        invoke("--base", base, "--output", out, "--inject", injectDir);

        assertContents(new ZipFile(out), "c.txt", "file c", "d.txt", "file d", "a.txt", "file a", "b.txt", "file b");
    }

    @Test
    void testInjectZip() throws Exception {
        final File base = new File(tempDir, "base.zip");
        writeJar(base, "c.txt", "file c", "d.txt", "file d");

        final File inject = new File(tempDir, "inject.zip");
        writeJar(inject, "e.txt", "yes it's e");

        final File out = new File(tempDir, "out.zip");
        invoke("--base", base, "--output", out, "--inject", inject);

        assertContents(new ZipFile(out), "c.txt", "file c", "d.txt", "file d", "e.txt", "yes it's e");
    }

    @Test
    void testInjectWithPrefix() throws Exception {
        final File base = new File(tempDir, "base.zip");
        writeJar(base, "c.txt", "file c", "d.txt", "file d");

        final File inject = new File(tempDir, "inject.zip");
        writeJar(inject, "correct/e.txt", "yes it's e", "correct/sub/f.txt", "sub file");

        final File out = new File(tempDir, "out.zip");
        invoke("--base", base, "--output", out, "--inject", inject, "--path-prefix", "correct/");

        assertContents(new ZipFile(out), "c.txt", "file c", "d.txt", "file d", "e.txt", "yes it's e", "sub/f.txt", "sub file");
    }

    @Test
    void testInjectPackageInfo() throws Exception {
        final File base = new File(tempDir, "base.zip");
        writeJar(base, "com/mojang/A.file", "a file", "com/notmojang/B.file", "b file");

        final File inject = new File(tempDir, "inject.zip");
        writeJar(inject, "package-info-template.java", "package {PACKAGE};");

        final File out = new File(tempDir, "out.zip");
        invoke("--base", base, "--output", out, "--inject", inject, "--inject-package-info", "com/mojang");

        assertContents(new ZipFile(out), "com/mojang/A.file", "a file", "com/mojang/package-info.java", "package com.mojang;", "com/notmojang/B.file", "b file");
    }

    private static void assertContents(ZipFile file, String... entries) throws IOException {
        Map<String, String> expectedFiles = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            expectedFiles.put(entries[i], entries[i + 1]);
        }

        Map<String, String> actualFiles = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> contained = file.entries();
        while (contained.hasMoreElements()) {
            ZipEntry entry = contained.nextElement();
            if (entry.isDirectory()) continue;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ConsoleTool.copy(file.getInputStream(entry), bos);
            actualFiles.put(entry.getName(), new String(bos.toByteArray(), StandardCharsets.UTF_8));
        }
        file.close();

        Assertions.assertThat(actualFiles)
                .containsExactlyEntriesOf(expectedFiles);
    }

    private static void writeJar(File location, String... entries) throws IOException {
        try (final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(location))) {
            for (int i = 0; i < entries.length; i += 2) {
                zos.putNextEntry(new ZipEntry(entries[i]));
                zos.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    private static void invoke(Object... args) throws Exception {
        final String[] finalArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof File) {
                finalArgs[i] = ((File) args[i]).getAbsolutePath();
            } else if (args[i] instanceof Path) {
                finalArgs[i] = ((Path) args[i]).toAbsolutePath().toString();
            } else {
                finalArgs[i] = args[i].toString();
            }
        }
        ConsoleTool.main(finalArgs);
    }
}
