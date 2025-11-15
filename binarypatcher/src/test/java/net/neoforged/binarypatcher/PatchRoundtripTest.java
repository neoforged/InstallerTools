package net.neoforged.binarypatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive test suite for binary patching system roundtrip functionality.
 */
public class PatchRoundtripTest {
    private static final DiffOptions DIFF_OPTIONS = new DiffOptions();

    @TempDir
    Path tempDir;

    private File baseFile;
    private File modifiedFile;
    private File patchBundleFile;
    private File outputFile;

    @BeforeEach
    void setUp() {
        baseFile = tempDir.resolve("base.zip").toFile();
        modifiedFile = tempDir.resolve("modified.zip").toFile();
        patchBundleFile = tempDir.resolve("patch.bundle").toFile();
        outputFile = tempDir.resolve("output.zip").toFile();
    }

    @Test
    void testBasicRoundtrip_CreateOperation() throws IOException {
        // Create base ZIP with some files
        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put("file1.txt", "Hello World".getBytes());
        baseEntries.put("file2.txt", "Original Content".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with an additional file (CREATE operation)
        Map<String, byte[]> modifiedEntries = new HashMap<>(baseEntries);
        modifiedEntries.put("file3.txt", "New File Content".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate patch
        generateAndApplyPatch();

        // Verify output matches modified file
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        Map<String, byte[]> expectedEntries = readZipEntries(modifiedFile);

        assertThat(outputEntries).containsExactlyInAnyOrderEntriesOf(expectedEntries);
        assertThat(outputEntries.get("file3.txt")).isEqualTo("New File Content".getBytes());
    }

    @Test
    void testBasicRoundtrip_ModifyOperation() throws IOException {
        // Create base ZIP
        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put("file1.txt", "Original Content".getBytes());
        baseEntries.put("file2.txt", "Another File".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with changed content (MODIFY operation)
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("file1.txt", "Modified Content".getBytes());
        modifiedEntries.put("file2.txt", "Another File".getBytes()); // unchanged
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        generateAndApplyPatch();

        // Verify
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries.get("file1.txt")).isEqualTo("Modified Content".getBytes());
        assertThat(outputEntries.get("file2.txt")).isEqualTo("Another File".getBytes());
    }

    @Test
    void testBasicRoundtrip_DeleteOperation() throws IOException {
        // Create base ZIP with three files
        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put("file1.txt", "Keep This".getBytes());
        baseEntries.put("file2.txt", "Delete This".getBytes());
        baseEntries.put("file3.txt", "Keep This Too".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with one file removed (DELETE operation)
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("file1.txt", "Keep This".getBytes());
        modifiedEntries.put("file3.txt", "Keep This Too".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        generateAndApplyPatch();

        // Verify file2.txt is deleted
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries).hasSize(2);
        assertThat(outputEntries).containsKeys("file1.txt", "file3.txt");
        assertThat(outputEntries).doesNotContainKey("file2.txt");
    }

    @Test
    void testCombinedOperations() throws IOException {
        // Base: file1, file2, file3
        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put("file1.txt", "Keep Original".getBytes());
        baseEntries.put("file2.txt", "Modify This".getBytes());
        baseEntries.put("file3.txt", "Delete This".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Modified: file1 (unchanged), file2 (modified), file3 (deleted), file4 (created)
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("file1.txt", "Keep Original".getBytes());
        modifiedEntries.put("file2.txt", "Modified Content".getBytes());
        modifiedEntries.put("file4.txt", "New File".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        generateAndApplyPatch();

        // Verify all operations applied correctly
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries).hasSize(3);
        assertThat(outputEntries.get("file1.txt")).isEqualTo("Keep Original".getBytes());
        assertThat(outputEntries.get("file2.txt")).isEqualTo("Modified Content".getBytes());
        assertThat(outputEntries.get("file4.txt")).isEqualTo("New File".getBytes());
        assertThat(outputEntries).doesNotContainKey("file3.txt");
    }

    @Test
    void testTimestampPreservation() throws IOException {
        // Create base ZIP with specific timestamps
        long timestamp1 = 1000000000000L; // Some past time
        long timestamp2 = 1100000000000L;
        long timestamp3 = 1200000000000L;

        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put("unchanged.txt", "No Change".getBytes());
        baseEntries.put("modified.txt", "Original".getBytes());
        baseEntries.put("deleted.txt", "Will Delete".getBytes());

        Map<String, Long> baseTimestamps = new HashMap<>();
        baseTimestamps.put("unchanged.txt", timestamp1);
        baseTimestamps.put("modified.txt", timestamp2);
        baseTimestamps.put("deleted.txt", timestamp3);

        createZipFile(baseFile, baseEntries, baseTimestamps);

        // Create modified ZIP
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("unchanged.txt", "No Change".getBytes());
        modifiedEntries.put("modified.txt", "Modified Content".getBytes());
        modifiedEntries.put("created.txt", "New File".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        generateAndApplyPatch();

        // Verify timestamps are preserved from base
        Map<String, Long> outputTimestamps = readZipTimestamps(outputFile);
        assertThat(outputTimestamps.get("unchanged.txt")).isEqualTo(timestamp1);
        assertThat(outputTimestamps.get("modified.txt")).isEqualTo(timestamp2);
        assertThat(outputTimestamps).doesNotContainKey("deleted.txt");
    }

    @Test
    void testJavaClassFilePatchingWithASM() throws IOException {
        // Create base ZIP with a class file
        byte[] baseClass = generateSimpleClassFile("com/example/TestClass", 42);
        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put("com/example/TestClass.class", baseClass);
        baseEntries.put("other.txt", "Some text".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with modified class file (different field value)
        byte[] modifiedClass = generateSimpleClassFile("com/example/TestClass", 99);
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("com/example/TestClass.class", modifiedClass);
        modifiedEntries.put("other.txt", "Some text".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        generateAndApplyPatch();

        // Verify class file was patched correctly
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries.get("com/example/TestClass.class")).isEqualTo(modifiedClass);
    }

    @Test
    void testMultiBasePatchBundle_SharedAndUniqueFiles() throws IOException {
        // CLIENT base: A, B, C
        Map<String, byte[]> clientBase = new HashMap<>();
        clientBase.put("fileA.txt", "Client A".getBytes());
        clientBase.put("fileB.txt", "Shared B Original".getBytes());
        clientBase.put("fileC.txt", "Shared C Original".getBytes());
        File clientBaseFile = tempDir.resolve("client_base.zip").toFile();
        createZipFile(clientBaseFile, clientBase, null);

        // SERVER base: B, C, D
        Map<String, byte[]> serverBase = new HashMap<>();
        serverBase.put("fileB.txt", "Shared B Original".getBytes());
        serverBase.put("fileC.txt", "Shared C Original".getBytes());
        serverBase.put("fileD.txt", "Server D".getBytes());
        File serverBaseFile = tempDir.resolve("server_base.zip").toFile();
        createZipFile(serverBaseFile, serverBase, null);

        // Modified client file has different changes for all files
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("fileA.txt", "Client A Modified".getBytes());
        modifiedEntries.put("fileB.txt", "Shared B Modified".getBytes());
        modifiedEntries.put("fileC.txt", "Shared C Modified".getBytes());
        modifiedEntries.put("fileD.txt", "Server D Modified".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate single patch bundle for both bases
        Map<PatchBase, File> bases = new HashMap<>();
        bases.put(PatchBase.CLIENT, clientBaseFile);
        bases.put(PatchBase.SERVER, serverBaseFile);
        Generator.createPatchBundle(bases, modifiedFile, patchBundleFile, DIFF_OPTIONS);

        // Apply patch with CLIENT base
        File clientOutput = tempDir.resolve("client_output.zip").toFile();
        Patcher.patch(clientBaseFile, PatchBase.CLIENT, Collections.singletonList(patchBundleFile), clientOutput);

        assertThat(readZipEntries(clientOutput)).containsExactlyEntriesOf(modifiedEntries);

        // Apply patch with SERVER base
        File serverOutput = tempDir.resolve("server_output.zip").toFile();
        Patcher.patch(serverBaseFile, PatchBase.SERVER, Collections.singletonList(patchBundleFile), serverOutput);

        assertThat(readZipEntries(serverOutput)).containsExactlyEntriesOf(modifiedEntries);
    }

    @Test
    void testWrongBaseTypeApplication() throws IOException {
        // Create CLIENT base
        Map<String, byte[]> clientBase = new HashMap<>();
        clientBase.put("client_file.txt", "Client Content".getBytes());
        File clientBaseFile = tempDir.resolve("client_base.zip").toFile();
        createZipFile(clientBaseFile, clientBase, null);

        // Create SERVER base (different content)
        Map<String, byte[]> serverBase = new HashMap<>();
        serverBase.put("server_file.txt", "Server Content".getBytes());
        File serverBaseFile = tempDir.resolve("server_base.zip").toFile();
        createZipFile(serverBaseFile, serverBase, null);

        // Create modified CLIENT file
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("client_file.txt", "Modified Client Content".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate patch for CLIENT only
        Map<PatchBase, File> bases = new HashMap<>();
        bases.put(PatchBase.CLIENT, clientBaseFile);
        Generator.createPatchBundle(bases, modifiedFile, patchBundleFile, DIFF_OPTIONS);

        // Try to apply CLIENT patch with SERVER base type - should fail
        assertThatThrownBy(() -> {
            Patcher.patch(serverBaseFile, PatchBase.SERVER, Collections.singletonList(patchBundleFile), outputFile);
        }).isInstanceOf(Exception.class); // Could be IOException, IllegalArgumentException, etc.
    }

    @Test
    void testEmptyFiles() throws IOException {
        // Create base with empty file
        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put("empty.txt", new byte[0]);
        baseEntries.put("nonempty.txt", "Content".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Modify to have different empty/non-empty state
        Map<String, byte[]> modifiedEntries = new HashMap<>();
        modifiedEntries.put("empty.txt", "Now has content".getBytes());
        modifiedEntries.put("nonempty.txt", new byte[0]);
        createZipFile(modifiedFile, modifiedEntries, null);

        generateAndApplyPatch();

        // Verify
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries.get("empty.txt")).isEqualTo("Now has content".getBytes());
        assertThat(outputEntries.get("nonempty.txt")).isEmpty();
    }

    /**
     * This test checks that the constraints we put on the paths for patches is enforced
     * based on the filenames in the base and modified ZIP.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "üüüüüüüüüüwüüüüüüüü",
            // both technically ASCII but special characters we exclude
            "\u0019restofpath",
            "\u007frestofpath"
    })
    void testPathConstraintsOnPatchFilesAreEnforced(String path) throws IOException {
        // Create base with files containing special characters
        Map<String, byte[]> baseEntries = new HashMap<>();
        baseEntries.put(path, "Content 1".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Modify one file
        Map<String, byte[]> modifiedEntries = new HashMap<>(baseEntries);
        modifiedEntries.put(path, "Modified Content 1".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        Map<PatchBase, File> bases = new HashMap<>();
        bases.put(PatchBase.CLIENT, baseFile);
        assertThatThrownBy(() -> Generator.createPatchBundle(bases, modifiedFile, patchBundleFile, DIFF_OPTIONS))
                .hasMessage("Path '" + path + "' contains invalid characters.");
    }

    private void generateAndApplyPatch() throws IOException {
        // Generate and apply patch
        Map<PatchBase, File> bases = new HashMap<>();
        bases.put(PatchBase.CLIENT, baseFile);
        Generator.createPatchBundle(bases, modifiedFile, patchBundleFile, DIFF_OPTIONS);
        Patcher.patch(baseFile, PatchBase.CLIENT, Collections.singletonList(patchBundleFile), outputFile);
    }

    /**
     * Creates a ZIP file with specified entries and timestamps.
     */
    private void createZipFile(File zipFile, Map<String, byte[]> entries, Map<String, Long> timestamps) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                if (timestamps != null && timestamps.containsKey(entry.getKey())) {
                    ze.setTime(timestamps.get(entry.getKey()));
                }
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
    }

    /**
     * Reads all entries from a ZIP file.
     */
    private Map<String, byte[]> readZipEntries(File zipFile) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                entries.put(entry.getName(), baos.toByteArray());
            }
        }
        return entries;
    }

    /**
     * Reads timestamps for all entries in a ZIP file.
     */
    private Map<String, Long> readZipTimestamps(File zipFile) throws IOException {
        Map<String, Long> timestamps = new HashMap<>();
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                timestamps.put(entry.getName(), entry.getTime());
            }
        }
        return timestamps;
    }

    /**
     * Generates a minimal Java class file using ASM.
     * Creates a simple class with a field and a method.
     */
    private byte[] generateSimpleClassFile(String className, int fieldValue) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        // Add a field: public int value
        cw.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, fieldValue).visitEnd();

        // Add constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
