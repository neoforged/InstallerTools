package net.neoforged.binarypatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static net.neoforged.binarypatcher.PatchBundleConstants.BUNDLE_SIGNATURE;
import static net.neoforged.binarypatcher.PatchBundleConstants.ENTRY_TYPE_CREATE;
import static net.neoforged.binarypatcher.PatchBundleConstants.ENTRY_TYPE_REMOVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchBundleReaderTest {

    @Test
    void shouldThrowExceptionForInvalidSignature() {
        byte[] invalidData = "INVALID_SIGNATURE".getBytes();

        assertThatThrownBy(() ->
                new PatchBundleReader(new ByteArrayInputStream(invalidData)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid bundle signature");
    }

    @Test
    void shouldReadEntryCount() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.CLIENT))) {
            writer.writeCreateEntry("file1.txt", "content1".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
            writer.writeCreateEntry("file2.txt", "content2".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
            writer.writeCreateEntry("file3.txt", "content3".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(reader.getEntryCount()).isEqualTo(3);
        }
    }

    @Test
    void shouldReadBundleDistributions() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        EnumSet<PatchBase> distributions = EnumSet.of(
                PatchBase.CLIENT, PatchBase.JOINED);

        new PatchBundleWriter(baos, distributions).close();

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(reader.getSupportedBaseTypes()).isEqualTo(distributions);
        }
    }

    @Test
    void shouldReadCreateEntry() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] content = "Test Content".getBytes(StandardCharsets.UTF_8);

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.CLIENT))) {
            writer.writeCreateEntry("test/file.txt", content,
                    EnumSet.of(PatchBase.CLIENT));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            Patch patch = reader.readEntry();

            assertThat(patch.getOperation()).isEqualTo(PatchOperation.CREATE);
            assertThat(patch.getTargetPath()).isEqualTo("test/file.txt");
            assertThat(patch.getBaseTypes()).containsExactly(PatchBase.CLIENT);
            assertThat(patch.getData()).isEqualTo(content);
            assertThat(patch.getBaseChecksum()).isNull();
        }
    }

    @Test
    void shouldReadModifyEntry() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] patchData = new byte[]{0x01, 0x02, 0x03, 0x04};
        long checksum = 0xABCD1234L;

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.SERVER))) {
            writer.writeModifyEntry("modified.bin", checksum, patchData,
                    EnumSet.of(PatchBase.SERVER));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            Patch patch = reader.readEntry();

            assertThat(patch.getOperation()).isEqualTo(PatchOperation.MODIFY);
            assertThat(patch.getTargetPath()).isEqualTo("modified.bin");
            assertThat(patch.getBaseTypes()).containsExactly(PatchBase.SERVER);
            assertThat(patch.getData()).isEqualTo(patchData);
            assertThat(patch.getBaseChecksum()).isNotNull();
            assertThat(patch.getBaseChecksumUnsigned()).isEqualTo(checksum);
        }
    }

    @Test
    void shouldReadRemoveEntry() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.JOINED))) {
            writer.writeRemoveEntry("deleted.txt",
                    EnumSet.of(PatchBase.JOINED));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            Patch patch = reader.readEntry();

            assertThat(patch.getOperation()).isEqualTo(PatchOperation.REMOVE);
            assertThat(patch.getTargetPath()).isEqualTo("deleted.txt");
            assertThat(patch.getBaseTypes()).containsExactly(PatchBase.JOINED);
            assertThat(patch.getData()).isEmpty();
            assertThat(patch.getBaseChecksum()).isNull();
        }
    }

    @Test
    void shouldReadMultipleEntriesSequentially() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.allOf(PatchBase.class))) {
            writer.writeCreateEntry("first.txt", "1".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
            writer.writeModifyEntry("second.txt", 100L, "2".getBytes(),
                    EnumSet.of(PatchBase.SERVER));
            writer.writeRemoveEntry("third.txt",
                    EnumSet.of(PatchBase.JOINED));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(reader.getEntryCount()).isEqualTo(3);
            assertThat(reader.getEntriesRead()).isEqualTo(0);

            Patch e1 = reader.readEntry();
            assertThat(e1.getOperation()).isEqualTo(PatchOperation.CREATE);
            assertThat(reader.getEntriesRead()).isEqualTo(1);

            Patch e2 = reader.readEntry();
            assertThat(e2.getOperation()).isEqualTo(PatchOperation.MODIFY);
            assertThat(reader.getEntriesRead()).isEqualTo(2);

            Patch e3 = reader.readEntry();
            assertThat(e3.getOperation()).isEqualTo(PatchOperation.REMOVE);
            assertThat(reader.getEntriesRead()).isEqualTo(3);

            assertThat(reader.hasMoreEntries()).isFalse();
            assertThat(reader.readEntry()).isNull();
        }
    }

    @Test
    void shouldIterateOverEntries() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.CLIENT))) {
            writer.writeCreateEntry("file1.txt", "a".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
            writer.writeCreateEntry("file2.txt", "b".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
            writer.writeCreateEntry("file3.txt", "c".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            List<Patch> entries = new ArrayList<>();

            for (Patch patch : reader) {
                entries.add(patch);
            }

            assertThat(entries).hasSize(3);
            assertThat(entries.get(0).getTargetPath()).isEqualTo("file1.txt");
            assertThat(entries.get(1).getTargetPath()).isEqualTo("file2.txt");
            assertThat(entries.get(2).getTargetPath()).isEqualTo("file3.txt");
        }
    }

    @Test
    void shouldThrowExceptionWhenIteratingAfterManualRead() throws IOException {
        ByteArrayOutputStream baos = createSimpleBundle();

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            reader.readEntry(); // Manual read

            assertThatThrownBy(() -> reader.iterator())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot create multiple iterators");
        }
    }

    @Test
    void shouldThrowExceptionWhenCreatingMultipleIterators() throws IOException {
        ByteArrayOutputStream baos = createSimpleBundle();

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            Iterator<Patch> it1 = reader.iterator();
            it1.next(); // Consume one entry

            assertThatThrownBy(() -> reader.iterator())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot create multiple iterators");
        }
    }

    @Test
    void shouldThrowExceptionWhenReadingAfterClose() throws IOException {
        ByteArrayOutputStream baos = createSimpleBundle();
        PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()));
        reader.close();

        assertThatThrownBy(() -> reader.readEntry())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Reader is closed");
    }

    @Test
    void shouldReturnNullWhenAllEntriesRead() throws IOException {
        ByteArrayOutputStream baos = createSimpleBundle();

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            reader.readEntry(); // Read the one entry

            assertThat(reader.hasMoreEntries()).isFalse();
            assertThat(reader.readEntry()).isNull();
        }
    }

    @Test
    void shouldReadEmptyBundle() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        new PatchBundleWriter(baos, EnumSet.of(PatchBase.CLIENT)).close();

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(reader.getEntryCount()).isEqualTo(0);
            assertThat(reader.hasMoreEntries()).isFalse();
            assertThat(reader.readEntry()).isNull();
        }
    }

    @Test
    void shouldReadFromFile(@TempDir File tempDir) throws IOException {
        File bundleFile = new File(tempDir, "test.bundle");

        try (FileOutputStream fos = new FileOutputStream(bundleFile);
             PatchBundleWriter writer = new PatchBundleWriter(fos,
                     EnumSet.of(PatchBase.CLIENT))) {
            writer.writeCreateEntry("file.txt", "content".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                Files.newInputStream(bundleFile.toPath()))) {
            assertThat(reader.getEntryCount()).isEqualTo(1);

            Patch patch = reader.readEntry();
            assertThat(patch.getTargetPath()).isEqualTo("file.txt");
            assertThat(patch.getData()).isEqualTo("content".getBytes());
        }
    }

    @Test
    void shouldThrowExceptionForNegativeEntryCount() {
        byte[] data = new byte[BUNDLE_SIGNATURE.length + 5];
        System.arraycopy(BUNDLE_SIGNATURE, 0, data, 0, BUNDLE_SIGNATURE.length);
        // Write -1 as entry count (big endian)
        data[BUNDLE_SIGNATURE.length] = (byte) 0xFF;
        data[BUNDLE_SIGNATURE.length + 1] = (byte) 0xFF;
        data[BUNDLE_SIGNATURE.length + 2] = (byte) 0xFF;
        data[BUNDLE_SIGNATURE.length + 3] = (byte) 0xFF;

        assertThatThrownBy(() ->
                new PatchBundleReader(new ByteArrayInputStream(data)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Invalid entry count");
    }

    @Test
    void shouldThrowExceptionForEntryDistributionNotInBundle() throws IOException {
        // Manually construct invalid bundle where entry has distribution not in bundle
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

        dos.write(BUNDLE_SIGNATURE);
        dos.writeInt(1); // 1 entry
        dos.writeByte(0x01); // Bundle only has CLIENT

        // Entry with SERVER distribution
        dos.writeByte(ENTRY_TYPE_CREATE | 0x02); // SERVER flag
        dos.writeShort(8);
        dos.write("test.txt".getBytes(StandardCharsets.US_ASCII));
        dos.writeInt(0); // No data

        assertThatThrownBy(() -> {
            try (PatchBundleReader reader = new PatchBundleReader(
                    new ByteArrayInputStream(baos.toByteArray()))) {
                reader.readEntry();
            }
        }).isInstanceOf(IOException.class)
                .hasMessageContaining("not declared in bundle");
    }

    @Test
    void shouldThrowExceptionForInvalidCharacterInString() throws IOException {
        // Manually construct bundle with invalid character in path
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

        dos.write(BUNDLE_SIGNATURE);
        dos.writeInt(1);
        dos.writeByte(0x01);

        dos.writeByte(ENTRY_TYPE_CREATE | 0x01);
        dos.writeShort(5);
        dos.write("test\0".getBytes(StandardCharsets.ISO_8859_1)); // Null character
        dos.writeInt(0);

        assertThatThrownBy(() -> {
            try (PatchBundleReader reader = new PatchBundleReader(
                    new ByteArrayInputStream(baos.toByteArray()))) {
                reader.readEntry();
            }
        }).isInstanceOf(IOException.class)
                .hasMessageContaining("invalid character");
    }

    @Test
    void shouldThrowExceptionForRemoveEntryWithNonZeroData() throws IOException {
        // Manually construct bundle with REMOVE entry that has data
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

        dos.write(BUNDLE_SIGNATURE);
        dos.writeInt(1);
        dos.writeByte(0x01);

        dos.writeByte(ENTRY_TYPE_REMOVE | 0x01);
        dos.writeShort(8);
        dos.write("test.txt".getBytes(StandardCharsets.US_ASCII));
        dos.writeInt(5); // Non-zero data length for REMOVE
        dos.write(new byte[5]);

        assertThatThrownBy(() -> {
            try (PatchBundleReader reader = new PatchBundleReader(
                    new ByteArrayInputStream(baos.toByteArray()))) {
                reader.readEntry();
            }
        }).isInstanceOf(IOException.class)
                .hasMessageContaining("REMOVE entry must have data length of 0");
    }

    @Test
    void shouldThrowExceptionWhenGettingChecksumForNonModifyEntry() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.CLIENT))) {
            writer.writeCreateEntry("test.txt", "content".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            Patch patch = reader.readEntry();

            assertThatThrownBy(() -> patch.getBaseChecksumUnsigned())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not available for CREATE entries");
        }
    }

    @Test
    void shouldHandleAllDistributionCombinations() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.allOf(PatchBase.class))) {
            writer.writeCreateEntry("client-only.txt", "a".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
            writer.writeCreateEntry("server-only.txt", "b".getBytes(),
                    EnumSet.of(PatchBase.SERVER));
            writer.writeCreateEntry("joined-only.txt", "c".getBytes(),
                    EnumSet.of(PatchBase.JOINED));
            writer.writeCreateEntry("client-server.txt", "d".getBytes(),
                    EnumSet.of(PatchBase.CLIENT, PatchBase.SERVER));
            writer.writeCreateEntry("all.txt", "e".getBytes(),
                    EnumSet.allOf(PatchBase.class));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            Patch e1 = reader.readEntry();
            assertThat(e1.getBaseTypes()).containsExactly(PatchBase.CLIENT);

            Patch e2 = reader.readEntry();
            assertThat(e2.getBaseTypes()).containsExactly(PatchBase.SERVER);

            Patch e3 = reader.readEntry();
            assertThat(e3.getBaseTypes()).containsExactly(PatchBase.JOINED);

            Patch e4 = reader.readEntry();
            assertThat(e4.getBaseTypes()).containsExactlyInAnyOrder(
                    PatchBase.CLIENT, PatchBase.SERVER);

            Patch e5 = reader.readEntry();
            assertThat(e5.getBaseTypes()).containsExactlyInAnyOrder(
                    PatchBase.CLIENT, PatchBase.SERVER, PatchBase.JOINED);
        }
    }

    @ParameterizedTest
    @MethodSource("provideLargeData")
    void shouldHandleLargeEntryData(byte[] largeData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.CLIENT))) {
            writer.writeCreateEntry("large.bin", largeData,
                    EnumSet.of(PatchBase.CLIENT));
        }

        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            Patch patch = reader.readEntry();
            assertThat(patch.getData()).isEqualTo(largeData);
        }
    }

    static Stream<Arguments> provideLargeData() {
        byte[] oneKb = new byte[1024];
        byte[] oneMb = new byte[1024 * 1024];

        // Fill with patterns for verification
        for (int i = 0; i < oneKb.length; i++) {
            oneKb[i] = (byte) (i % 256);
        }
        for (int i = 0; i < oneMb.length; i++) {
            oneMb[i] = (byte) (i % 256);
        }

        return Stream.of(
                Arguments.of(oneKb),
                Arguments.of(oneMb)
        );
    }

    @Test
    void shouldRoundTripComplexBundle() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write complex bundle
        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.allOf(PatchBase.class))) {
            writer.writeCreateEntry("new/file.txt", "New content".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
            writer.writeModifyEntry("existing/file.bin", 0xDEADBEEFL,
                    new byte[]{1, 2, 3, 4, 5}, EnumSet.of(PatchBase.SERVER));
            writer.writeRemoveEntry("old/file.dat",
                    EnumSet.of(PatchBase.JOINED));
            writer.writeCreateEntry("shared.txt", "Shared".getBytes(),
                    EnumSet.allOf(PatchBase.class));
        }

        // Read and verify
        try (PatchBundleReader reader = new PatchBundleReader(
                new ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(reader.getEntryCount()).isEqualTo(4);
            assertThat(reader.getSupportedBaseTypes())
                    .containsExactlyInAnyOrder(PatchBase.values());

            // Verify all entries
            List<Patch> entries = new ArrayList<>();
            for (Patch patch : reader) {
                entries.add(patch);
            }

            assertThat(entries).hasSize(4);
            assertThat(entries.get(0).getOperation()).isEqualTo(PatchOperation.CREATE);
            assertThat(entries.get(1).getOperation()).isEqualTo(PatchOperation.MODIFY);
            assertThat(entries.get(2).getOperation()).isEqualTo(PatchOperation.REMOVE);
            assertThat(entries.get(3).getOperation()).isEqualTo(PatchOperation.CREATE);
        }
    }

    // Helper method to create a simple bundle for testing
    private ByteArrayOutputStream createSimpleBundle() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PatchBundleWriter writer = new PatchBundleWriter(baos,
                EnumSet.of(PatchBase.CLIENT))) {
            writer.writeCreateEntry("test.txt", "test".getBytes(),
                    EnumSet.of(PatchBase.CLIENT));
        }
        return baos;
    }
}