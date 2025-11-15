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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test suite for binary patching system roundtrip functionality.
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
        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put("file1.txt", "Hello World".getBytes());
        baseEntries.put("file2.txt", "Original Content".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with an additional file (CREATE operation)
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>(baseEntries);
        modifiedEntries.put("file3.txt", "New File Content".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate patch
        generateAndApplyPatch();

        // Verify output matches modified file
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        Map<String, byte[]> expectedEntries = readZipEntries(modifiedFile);

        assertThat(outputEntries).containsExactlyInAnyOrderEntriesOf(expectedEntries);
        assertThat(outputEntries.get("file3.txt")).isEqualTo("New File Content".getBytes());

        assertThat(getPatchBundleSummary()).containsExactly("file3.txt CREATE [CLIENT]");
    }

    @Test
    void testBasicRoundtrip_ModifyOperation() throws IOException {
        // Create base ZIP
        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put("file1.txt", "Original Content".getBytes());
        baseEntries.put("file2.txt", "Another File".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with changed content (MODIFY operation)
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>();
        modifiedEntries.put("file1.txt", "Modified Content".getBytes());
        modifiedEntries.put("file2.txt", "Another File".getBytes()); // unchanged
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        generateAndApplyPatch();

        // Verify
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries.get("file1.txt")).isEqualTo("Modified Content".getBytes());
        assertThat(outputEntries.get("file2.txt")).isEqualTo("Another File".getBytes());

        assertThat(getPatchBundleSummary()).containsExactly("file1.txt MODIFY [CLIENT]");
    }

    @Test
    void testBasicRoundtrip_DeleteOperation() throws IOException {
        // Create base ZIP with three files
        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put("file1.txt", "Keep This".getBytes());
        baseEntries.put("file2.txt", "Delete This".getBytes());
        baseEntries.put("file3.txt", "Keep This Too".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with one file removed (DELETE operation)
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>();
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

        assertThat(getPatchBundleSummary()).containsExactly("file2.txt REMOVE [CLIENT]");
    }

    @Test
    void testCombinedOperations() throws IOException {
        // Base: file1, file2, file3
        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put("file1.txt", "Keep Original".getBytes());
        baseEntries.put("file2.txt", "Modify This".getBytes());
        baseEntries.put("file3.txt", "Delete This".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Modified: file1 (unchanged), file2 (modified), file3 (deleted), file4 (created)
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>();
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

        assertThat(getPatchBundleSummary()).containsExactly(
                "file2.txt MODIFY [CLIENT]",
                "file3.txt REMOVE [CLIENT]",
                "file4.txt CREATE [CLIENT]"
        );
    }

    @Test
    void testTimestampPreservation() throws IOException {
        // Create base ZIP with specific timestamps
        long timestamp1 = 1000000000000L; // Some past time
        long timestamp2 = 1100000000000L;
        long timestamp3 = 1200000000000L;

        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put("unchanged.txt", "No Change".getBytes());
        baseEntries.put("modified.txt", "Original".getBytes());
        baseEntries.put("deleted.txt", "Will Delete".getBytes());

        Map<String, Long> baseTimestamps = new LinkedHashMap<>();
        baseTimestamps.put("unchanged.txt", timestamp1);
        baseTimestamps.put("modified.txt", timestamp2);
        baseTimestamps.put("deleted.txt", timestamp3);

        createZipFile(baseFile, baseEntries, baseTimestamps);

        // Create modified ZIP
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>();
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

        assertThat(getPatchBundleSummary()).containsExactly(
                "modified.txt MODIFY [CLIENT]",
                "deleted.txt REMOVE [CLIENT]",
                "created.txt CREATE [CLIENT]"
        );
    }

    @Test
    void testJavaClassFilePatchingWithASM() throws IOException {
        // Create base ZIP with a class file
        byte[] baseClass = generateSimpleClassFile("com/example/TestClass", 42);
        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put("com/example/TestClass.class", baseClass);
        baseEntries.put("other.txt", "Some text".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Create modified ZIP with modified class file (different field value)
        byte[] modifiedClass = generateSimpleClassFile("com/example/TestClass", 99);
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>();
        modifiedEntries.put("com/example/TestClass.class", modifiedClass);
        modifiedEntries.put("other.txt", "Some text".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        generateAndApplyPatch();

        // Verify class file was patched correctly
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries.get("com/example/TestClass.class")).isEqualTo(modifiedClass);

        assertThat(getPatchBundleSummary()).containsExactly("com/example/TestClass.class MODIFY [CLIENT]");
    }

    @Test
    void testMultiBasePatchBundle_SharedAndUniqueFiles() throws IOException {
        // CLIENT base
        Map<String, byte[]> clientBase = new LinkedHashMap<>();
        clientBase.put("fileA.txt", "Client A".getBytes());
        clientBase.put("fileB.txt", "Shared B Original".getBytes());
        clientBase.put("fileC.txt", "Shared C Original".getBytes());
        clientBase.put("shared_base_but_unique.txt", "Shared, Client Version".getBytes());
        File clientBaseFile = tempDir.resolve("client_base.zip").toFile();
        createZipFile(clientBaseFile, clientBase, null);

        // SERVER base
        Map<String, byte[]> serverBase = new LinkedHashMap<>();
        serverBase.put("fileB.txt", "Shared B Original".getBytes());
        serverBase.put("fileC.txt", "Shared C Original".getBytes());
        serverBase.put("fileD.txt", "Server D".getBytes());
        clientBase.put("shared_base_but_unique.txt", "Shared, Server Version".getBytes());
        File serverBaseFile = tempDir.resolve("server_base.zip").toFile();
        createZipFile(serverBaseFile, serverBase, null);

        // Modified client file
        Map<String, byte[]> modifiedClientEntries = new LinkedHashMap<>();
        modifiedClientEntries.put("fileA.txt", "Client A Modified".getBytes());
        modifiedClientEntries.put("fileB.txt", "Shared B Modified".getBytes());
        modifiedClientEntries.put("shared_base_but_unique.txt", "New Shared Version".getBytes());
        modifiedClientEntries.put("shared_created.txt", "Shared Created File".getBytes());
        modifiedClientEntries.put("unique_created.txt", "New Unique Client File".getBytes());
        File clientModifiedFile = tempDir.resolve("client_modified.zip").toFile();
        createZipFile(clientModifiedFile, modifiedClientEntries, null);

        // Modified server file
        Map<String, byte[]> modifiedServerEntries = new LinkedHashMap<>();
        modifiedServerEntries.put("fileB.txt", "Shared B Modified".getBytes());
        modifiedServerEntries.put("fileD.txt", "Server D Modified".getBytes());
        modifiedServerEntries.put("shared_base_but_unique.txt", "New Shared Version".getBytes());
        modifiedServerEntries.put("shared_created.txt", "Shared Created File".getBytes());
        modifiedServerEntries.put("unique_created.txt", "New Unique Server File".getBytes());
        File serverModifiedFile = tempDir.resolve("server_modified.zip").toFile();
        createZipFile(serverModifiedFile, modifiedServerEntries, null);

        // Generate single patch bundle for both bases
        Map<PatchBase, File> baseFiles = new LinkedHashMap<>();
        baseFiles.put(PatchBase.CLIENT, clientBaseFile);
        baseFiles.put(PatchBase.SERVER, serverBaseFile);
        Map<PatchBase, File> modifiedFiles = new LinkedHashMap<>();
        modifiedFiles.put(PatchBase.CLIENT, clientModifiedFile);
        modifiedFiles.put(PatchBase.SERVER, serverModifiedFile);
        Generator.createPatchBundle(baseFiles, modifiedFiles, patchBundleFile, DIFF_OPTIONS);

        assertThat(getPatchBundleSummary()).containsExactly(
                "fileA.txt MODIFY [CLIENT]",
                "fileB.txt MODIFY [CLIENT, SERVER]",
                "fileC.txt REMOVE [CLIENT, SERVER]",
                "shared_base_but_unique.txt MODIFY [CLIENT]",
                "shared_created.txt CREATE [CLIENT, SERVER]",
                "unique_created.txt CREATE [CLIENT]",
                "fileD.txt MODIFY [SERVER]",
                "shared_base_but_unique.txt CREATE [SERVER]",
                "unique_created.txt CREATE [SERVER]"
        );

        // Apply patch with CLIENT base
        File clientOutput = tempDir.resolve("client_output.zip").toFile();
        Patcher.patch(clientBaseFile, PatchBase.CLIENT, Collections.singletonList(patchBundleFile), clientOutput);
        assertThat(readZipEntries(clientOutput)).containsExactlyInAnyOrderEntriesOf(modifiedClientEntries);

        // Apply patch with SERVER base
        File serverOutput = tempDir.resolve("server_output.zip").toFile();
        Patcher.patch(serverBaseFile, PatchBase.SERVER, Collections.singletonList(patchBundleFile), serverOutput);
        assertThat(readZipEntries(serverOutput)).containsExactlyInAnyOrderEntriesOf(modifiedServerEntries);
    }

    @Test
    void testWrongBaseTypeApplication() throws IOException {
        // Create CLIENT base
        Map<String, byte[]> clientBase = new LinkedHashMap<>();
        clientBase.put("client_file.txt", "Client Content".getBytes());
        File clientBaseFile = tempDir.resolve("client_base.zip").toFile();
        createZipFile(clientBaseFile, clientBase, null);

        // Create SERVER base (different content)
        Map<String, byte[]> serverBase = new LinkedHashMap<>();
        serverBase.put("server_file.txt", "Server Content".getBytes());
        File serverBaseFile = tempDir.resolve("server_base.zip").toFile();
        createZipFile(serverBaseFile, serverBase, null);

        // Create modified CLIENT file
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>();
        modifiedEntries.put("client_file.txt", "Modified Client Content".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate patch for CLIENT only
        Map<PatchBase, File> baseFiles = new LinkedHashMap<>();
        baseFiles.put(PatchBase.CLIENT, clientBaseFile);
        Map<PatchBase, File> modifiedFiles = new LinkedHashMap<>();
        modifiedFiles.put(PatchBase.CLIENT, modifiedFile);
        Generator.createPatchBundle(baseFiles, modifiedFiles, patchBundleFile, DIFF_OPTIONS);

        // Try to apply CLIENT patch with SERVER base type - should fail
        assertThatThrownBy(() -> {
            Patcher.patch(serverBaseFile, PatchBase.SERVER, Collections.singletonList(patchBundleFile), outputFile);
        }).isInstanceOf(Exception.class); // Could be IOException, IllegalArgumentException, etc.
    }

    @Test
    void testEmptyFiles() throws IOException {
        // Create base with empty file
        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put("empty.txt", new byte[0]);
        baseEntries.put("nonempty.txt", "Content".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Modify to have different empty/non-empty state
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>();
        modifiedEntries.put("empty.txt", "Now has content".getBytes());
        modifiedEntries.put("nonempty.txt", new byte[0]);
        createZipFile(modifiedFile, modifiedEntries, null);

        generateAndApplyPatch();

        // Verify
        Map<String, byte[]> outputEntries = readZipEntries(outputFile);
        assertThat(outputEntries.get("empty.txt")).isEqualTo("Now has content".getBytes());
        assertThat(outputEntries.get("nonempty.txt")).isEmpty();

        assertThat(getPatchBundleSummary()).containsExactly(
                "empty.txt MODIFY [CLIENT]", "nonempty.txt MODIFY [CLIENT]"
        );
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
        Map<String, byte[]> baseEntries = new LinkedHashMap<>();
        baseEntries.put(path, "Content 1".getBytes());
        createZipFile(baseFile, baseEntries, null);

        // Modify one file
        Map<String, byte[]> modifiedEntries = new LinkedHashMap<>(baseEntries);
        modifiedEntries.put(path, "Modified Content 1".getBytes());
        createZipFile(modifiedFile, modifiedEntries, null);

        // Generate and apply patch
        Map<PatchBase, File> baseFiles = new LinkedHashMap<>();
        baseFiles.put(PatchBase.CLIENT, baseFile);
        Map<PatchBase, File> modifiedFiles = new LinkedHashMap<>();
        modifiedFiles.put(PatchBase.CLIENT, modifiedFile);
        assertThatThrownBy(() -> Generator.createPatchBundle(baseFiles, modifiedFiles, patchBundleFile, DIFF_OPTIONS))
                .hasMessage("Path '" + path + "' contains invalid characters.");
    }

    private void generateAndApplyPatch() throws IOException {
        Map<PatchBase, File> baseFiles = new LinkedHashMap<>();
        baseFiles.put(PatchBase.CLIENT, baseFile);
        Map<PatchBase, File> modifiedFiles = new LinkedHashMap<>();
        modifiedFiles.put(PatchBase.CLIENT, modifiedFile);
        Generator.createPatchBundle(baseFiles, modifiedFiles, patchBundleFile, DIFF_OPTIONS);
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
        Map<String, byte[]> entries = new LinkedHashMap<>();
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
        Map<String, Long> timestamps = new LinkedHashMap<>();
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

    private List<String> getPatchBundleSummary() throws IOException {
        List<String> result = new ArrayList<>();
        try (PatchBundleReader reader = new PatchBundleReader(patchBundleFile)) {
            for (Patch patch : reader) {
                result.add(patch.getTargetPath() + " " + patch.getOperation() + " " + patch.getBaseTypes());
            }
        }
        return result;
    }
}
