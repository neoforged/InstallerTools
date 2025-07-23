package net.neoforged.jartransform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelZipProcessorTest {
    @TempDir
    File tempDir;

    File inputFile;

    @Test
    void testContentConstructors() throws IOException {
        inputFile = new File(tempDir, "input.zip");
        new ZipOutput(inputFile).close();

        File outputFile = new File(tempDir, "output.zip");
        ParallelZipProcessor.process(
                inputFile,
                outputFile,
                new ZipProcessingStrategy() {
                    @Override
                    public CompletableFuture<TransformedContent> transformContent(ZipInput inputFile, ZipTransformEntry entry) {
                        return null;
                    }

                    @Override
                    public List<CompletableFuture<TransformedContent>> getLeadingContent(ZipInput inputFile) {
                        return Collections.singletonList(CompletableFuture.completedFuture(ZipProcessingStrategy.compressedContent("compressed", null, new byte[]{1, 2, 3})));
                    }

                    @Override
                    public List<CompletableFuture<TransformedContent>> getTrailingContent(ZipInput inputFile) {
                        return Collections.singletonList(CompletableFuture.completedFuture(ZipProcessingStrategy.uncompressedContent("uncompressed", null, new byte[]{1, 2, 3})));
                    }
                }
        );

        try (ZipFile zf = new ZipFile(outputFile)) {
            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> enumeration = zf.entries();
            while (enumeration.hasMoreElements()) {
                entries.add(enumeration.nextElement());
            }

            assertThat(entries).extracting(ZipEntry::getName).containsExactly("compressed", "uncompressed");

            for (ZipEntry entry : entries) {
                try (InputStream in = zf.getInputStream(entry)) {
                    assertThat(in).hasBinaryContent(new byte[]{1, 2, 3});
                }
            }
        }
    }

    @Test
    void testZipProcessing() throws IOException {
        Random random = new Random();
        inputFile = new File(tempDir, "input.zip");
        byte[] randomContent = new byte[4096];
        try (ZipOutput out = new ZipOutput(inputFile)) {
            for (int i = 0; i < 1000; i++) {
                random.nextBytes(randomContent);
                ZipTransformEntry entry = new ZipTransformEntry(
                        "entry_" + i,
                        true,
                        randomContent.length,
                        -1,
                        null
                );
                out.addArchiveEntry(entry, randomContent);
            }
        }

        ZipProcessingStrategy strategy = (inputFile, entry) -> CompletableFuture.completedFuture(ZipProcessingStrategy.copiedContent(inputFile, entry));

        File outputFile = new File(tempDir, "output.zip");
        ParallelZipProcessor.process(
                inputFile,
                outputFile,
                strategy
        );

        try (ZipFile input = new ZipFile(inputFile); ZipFile zf = new ZipFile(outputFile)) {
            List<ZipEntry> entries = new ArrayList<>();
            Enumeration<? extends ZipEntry> enumeration = zf.entries();
            while (enumeration.hasMoreElements()) {
                entries.add(enumeration.nextElement());
            }

            String[] expectedEntries = new String[1000];
            for (int i = 0; i < expectedEntries.length; i++) {
                expectedEntries[i] = "entry_" + i;
            }
            assertThat(entries).extracting(ZipEntry::getName).containsExactly(expectedEntries);

            for (ZipEntry entry : entries) {
                try (InputStream in = zf.getInputStream(entry);
                     InputStream expected = input.getInputStream(input.getEntry(entry.getName()))) {
                    assertThat(in).hasSameContentAs(expected);
                }
            }
        }
    }
}