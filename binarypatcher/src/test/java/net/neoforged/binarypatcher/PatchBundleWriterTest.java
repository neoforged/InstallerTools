package net.neoforged.binarypatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static net.neoforged.binarypatcher.PatchBundleConstants.*;

class PatchBundleWriterTest {

    @Test
    void shouldWriteCorrectEntryCount() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT))) {
            writer.writeCreateEntry("file1.txt", "content1".getBytes(),
                    EnumSet.of(TargetDistribution.CLIENT));
            writer.writeCreateEntry("file2.txt", "content2".getBytes(),
                    EnumSet.of(TargetDistribution.CLIENT));
        }

        DataInputStream dis = new DataInputStream(
                new java.io.ByteArrayInputStream(baos.toByteArray()));
        dis.skip(BUNDLE_SIGNATURE.length);
        int entryCount = dis.readInt();

        assertThat(entryCount).isEqualTo(2);
    }

    @Test
    void shouldWriteCorrectBundleDistributions() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EnumSet<TargetDistribution> distributions = EnumSet.of(
                TargetDistribution.CLIENT, TargetDistribution.SERVER);

        new PatchBundleWriter(baos, distributions).close();

        DataInputStream dis = new DataInputStream(
                new java.io.ByteArrayInputStream(baos.toByteArray()));
        dis.skip(BUNDLE_SIGNATURE.length);
        dis.readInt(); // skip entry count
        int distBitfield = dis.readUnsignedByte();

        assertThat(distBitfield).isEqualTo(0x03); // CLIENT | SERVER
    }

    @Test
    void shouldWriteMultipleEntriesInOrder() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.allOf(TargetDistribution.class))) {
            writer.writeCreateEntry("first.txt", "1".getBytes(),
                    EnumSet.of(TargetDistribution.CLIENT));
            writer.writeModifyEntry("second.txt", 100L, "2".getBytes(),
                    EnumSet.of(TargetDistribution.SERVER));
            writer.writeRemoveEntry("third.txt",
                    EnumSet.of(TargetDistribution.JOINED));
        }

        // Verify we can read all entries back
        try (PatchBundleReader reader = new PatchBundleReader(
                new java.io.ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(reader.getEntryCount()).isEqualTo(3);

            PatchBundleReader.Entry e1 = reader.readEntry();
            assertThat(e1.getType()).isEqualTo(PatchOperation.CREATE);
            assertThat(e1.getTargetPath()).isEqualTo("first.txt");

            PatchBundleReader.Entry e2 = reader.readEntry();
            assertThat(e2.getType()).isEqualTo(PatchOperation.MODIFY);
            assertThat(e2.getTargetPath()).isEqualTo("second.txt");

            PatchBundleReader.Entry e3 = reader.readEntry();
            assertThat(e3.getType()).isEqualTo(PatchOperation.REMOVE);
            assertThat(e3.getTargetPath()).isEqualTo("third.txt");
        }
    }

    @Test
    void shouldThrowExceptionForEmptyBundleDistributions() {
        assertThatThrownBy(() ->
                new PatchBundleWriter(new ByteArrayOutputStream(), EnumSet.noneOf(TargetDistribution.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bundle must target at least one distribution");
    }

    @Test
    void shouldThrowExceptionForEmptyEntryDistributions() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT));

        assertThatThrownBy(() ->
                writer.writeCreateEntry("test.txt", new byte[0],
                        EnumSet.noneOf(TargetDistribution.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Entry must target at least one distribution");
    }

    @Test
    void shouldThrowExceptionWhenEntryDistributionNotInBundle() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT));

        assertThatThrownBy(() ->
                writer.writeCreateEntry("test.txt", new byte[0],
                        EnumSet.of(TargetDistribution.SERVER)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not declared in bundle");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "path//double", "path/./dot", "path/../dotdot", "/leading", "trailing/"})
    void shouldThrowExceptionForInvalidPaths(String invalidPath) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT));

        assertThatThrownBy(() ->
                writer.writeCreateEntry(invalidPath, new byte[0],
                        EnumSet.of(TargetDistribution.CLIENT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowExceptionForStringWithInvalidCharacters() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT));

        // Test with newline character
        assertThatThrownBy(() ->
                writer.writeCreateEntry("test\nfile.txt", new byte[0],
                        EnumSet.of(TargetDistribution.CLIENT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void shouldThrowExceptionForInvalidChecksum() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT));

        assertThatThrownBy(() ->
                writer.writeModifyEntry("test.txt", -1L, new byte[0],
                        EnumSet.of(TargetDistribution.CLIENT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid 32-bit unsigned value");

        assertThatThrownBy(() ->
                writer.writeModifyEntry("test.txt", 0x100000000L, new byte[0],
                        EnumSet.of(TargetDistribution.CLIENT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid 32-bit unsigned value");
    }

    @Test
    void shouldThrowExceptionWhenWritingAfterClose() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT));
        writer.close();

        assertThatThrownBy(() ->
                writer.writeCreateEntry("test.txt", new byte[0],
                        EnumSet.of(TargetDistribution.CLIENT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void shouldCalculateChecksumCorrectly() {
        byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);
        long checksum = PatchBundleWriter.calculateChecksum(data);

        assertThat(checksum).isNotZero();
        assertThat(checksum).isGreaterThanOrEqualTo(0);
        assertThat(checksum).isLessThanOrEqualTo(0xFFFFFFFFL);
    }

    @Test
    void shouldWriteEmptyBundle() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        new PatchBundleWriter(baos, EnumSet.of(TargetDistribution.CLIENT)).close();

        try (PatchBundleReader reader = new PatchBundleReader(
                new java.io.ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(reader.getEntryCount()).isEqualTo(0);
            assertThat(reader.hasMoreEntries()).isFalse();
        }
    }

    @Test
    void shouldWriteToFile(@TempDir File tempDir) throws IOException {
        File bundleFile = new File(tempDir, "test.bundle");

        try (FileOutputStream fos = new FileOutputStream(bundleFile);
             PatchBundleWriter writer = new PatchBundleWriter(fos,
                     EnumSet.of(TargetDistribution.CLIENT))) {
            writer.writeCreateEntry("file.txt", "content".getBytes(),
                    EnumSet.of(TargetDistribution.CLIENT));
        }

        assertThat(bundleFile).exists();
        assertThat(bundleFile.length()).isGreaterThan(0);

        // Verify it can be read back
        try (PatchBundleReader reader = new PatchBundleReader(
                Files.newInputStream(bundleFile.toPath()))) {
            assertThat(reader.getEntryCount()).isEqualTo(1);
        }
    }

    @ParameterizedTest
    @MethodSource("provideValidPaths")
    void shouldAcceptValidPaths(String validPath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(TargetDistribution.CLIENT))) {
            assertThatCode(() ->
                    writer.writeCreateEntry(validPath, new byte[0],
                            EnumSet.of(TargetDistribution.CLIENT)))
                    .doesNotThrowAnyException();
        }
    }

    static Stream<Arguments> provideValidPaths() {
        return Stream.of(
                Arguments.of("file.txt"),
                Arguments.of("path/to/file.txt"),
                Arguments.of("a/b/c/d/e/f.bin"),
                Arguments.of("file_with_underscores.txt"),
                Arguments.of("file-with-dashes.txt"),
                Arguments.of("CamelCase.txt"),
                Arguments.of("file.with.dots.txt")
        );
    }
}