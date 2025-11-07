/*
 * InstallerTools
 * Copyright (c) 2019-2025.
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
package net.neoforged.installertools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessMinecraftJarTest {
    @TempDir
    Path tempDir;

    @Test
    void testMergeJars() throws IOException {
        Path clientJar = tempDir.resolve("client.jar");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(clientJar)))) {
            writeClass(out, "net/minecraft/client/main/Main.class");
            writeClass(out, "net/minecraft/server/Main.class");

            out.putNextEntry(new ZipEntry("assets/lang/en_us.json"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("assets/client_only.json"));
            out.closeEntry();
        }

        Path serverJar = tempDir.resolve("server_inner.jar");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(serverJar)))) {
            writeClass(out, "net/minecraft/server/Main.class");
            // this does not exist in reality, but we'll keep it to test server-only sided annotations
            writeClass(out, "net/minecraft/server/ServerOnly.class");

            out.putNextEntry(new ZipEntry("assets/lang/en_us.json"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("data/server_only.json"));
            out.closeEntry();
        }

        Path joinedJar = tempDir.resolve("joined.jar");

        new ProcessMinecraftJar().process(new String[]{
                "--input", clientJar.toString(),
                "--input", serverJar.toString(),
                "--output", joinedJar.toString()
        });

        try (JarFile jar = new JarFile(joinedJar.toFile())) {
            assertThat(jar.stream()).extracting(ZipEntry::getName).containsOnly(
                    "net/minecraft/client/main/",
                    "net/minecraft/client/main/Main.class",
                    "net/minecraft/server/",
                    "net/minecraft/server/Main.class",
                    "assets/lang/",
                    "assets/lang/en_us.json",
                    "assets/",
                    "assets/client_only.json",
                    "net/minecraft/server/ServerOnly.class",
                    "data/",
                    "data/server_only.json",
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "META-INF/neoforge.mods.toml"
            );

            assertSideOnlyAnnotation(jar, "net/minecraft/client/main/Main.class", "CLIENT");
            assertSideOnlyAnnotation(jar, "net/minecraft/server/ServerOnly.class", "DEDICATED_SERVER");
            assertNoSideOnlyAnnotation(jar, "net/minecraft/server/Main.class");

            Manifest manifest = jar.getManifest();
            assertEquals("client server", manifest.getMainAttributes().getValue("Minecraft-Dists"));
            assertThat(getDistTable(manifest)).containsOnly(
                    "net/minecraft/server/ServerOnly.class=server",
                    "assets/client_only.json=client",
                    "net/minecraft/client/main/Main.class=client",
                    "data/server_only.json=server"
            );
        }
    }

    private static List<String> getDistTable(Manifest manifest) {
        List<String> result = new ArrayList<>();

        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            String dist = entry.getValue().getValue("Minecraft-Dist");
            result.add(entry.getKey() + "=" + dist);
        }

        return result;
    }

    private void assertSideOnlyAnnotation(ZipFile zf, String relativePath, String expectedSide) throws IOException {
        try (InputStream in = zf.getInputStream(zf.getEntry(relativePath))) {
            ClassReader reader = new ClassReader(in);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            assertThat(classNode.visibleAnnotations)
                    .isNotNull()
                    .extracting(ProcessMinecraftJarTest::annotationToString)
                    .containsOnly(
                            "Lnet/neoforged/api/distmarker/OnlyIn;(value,[Lnet/neoforged/api/distmarker/Dist;, " + expectedSide + "],)"
                    );
        }
    }

    private void assertNoSideOnlyAnnotation(ZipFile zf, String relativePath) throws IOException {
        try (InputStream in = zf.getInputStream(zf.getEntry(relativePath))) {
            ClassReader reader = new ClassReader(in);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            assertThat(classNode.visibleAnnotations).isNullOrEmpty();
        }
    }

    private static String annotationToString(AnnotationNode ann) {
        StringBuilder result = new StringBuilder(ann.desc);
        result.append("(");
        for (Object value : ann.values) {
            if (value instanceof String[]) {
                result.append(Arrays.toString((String[]) value));
            } else {
                result.append(value);
            }
            result.append(",");
        }
        result.append(")");
        return result.toString();
    }

    /**
     * Dynamically generates a class. This is not 100% correct, but should be sufficient for the
     * background scanner to read it.
     */
    private static void writeClass(ZipOutputStream output, String relativePath) throws IOException {
        String className = relativePath.replace(".class", "");
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);
        MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);  // Load 'this'
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);  // Call super()
        constructor.visitInsn(Opcodes.RETURN);  // Return
        constructor.visitMaxs(0, 0); // Let COMPUTE_MAXS handle this
        constructor.visitEnd();
        classWriter.visitEnd();

        ZipEntry ze = new ZipEntry(relativePath);
        output.putNextEntry(ze);
        output.write(classWriter.toByteArray());
        output.closeEntry();
    }
}