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

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumExtensionsTest {
    @Test
    void testInjection(@TempDir Path tempDir) throws Exception {
        // Create interface injection data file
        File extensionsDataFile = tempDir.resolve("enum_extensions.json").toFile();
        Map<String, Object> enumExtensionsData = new HashMap<>();
        List<Object> entries = new ArrayList<>();
        Map<String, Object> entryData = new HashMap<>();
        entryData.put("enum", "net/neoforged/installertools/TestEnum");
        entryData.put("name", "D");
        entries.add(entryData);
        enumExtensionsData.put("entries", entries);

        try (FileWriter writer = new FileWriter(extensionsDataFile)) {
            new Gson().toJson(enumExtensionsData, writer);
        }

        // Read TestClass bytecode using ASM
        String testEnum = TestEnum.class.getName().replace('.', '/');
        ClassNode classNode = new ClassNode();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(testEnum + ".class")) {
            assertNotNull(is, "Could not find TestClass bytecode");
            ClassReader reader = new ClassReader(is);
            reader.accept(classNode, 0);
        }

        // Apply interface injection with annotation marker
        EnumExtension interfaceInjection = new EnumExtension(
            Collections.singletonList(extensionsDataFile),
            Type.getInternalName(ExtensionEnumEntry.class)
        );

        Type classType = Type.getObjectType(testEnum);
        interfaceInjection.transform(classNode, classType);

        // Write transformed TestEnum to temp directory
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        byte[] testClassBytecode = writer.toByteArray();

        Path testClassFile = tempDir.resolve("net/neoforged/installertools/TestEnum.class");
        Files.createDirectories(testClassFile.getParent());
        Files.write(testClassFile, testClassBytecode);
        
        // Re-parse the bytecode and check for the runtime-invisible annotation
        ClassNode transformedClassNode = new ClassNode();
        try (InputStream is = Files.newInputStream(testClassFile)) {
            ClassReader reader = new ClassReader(is);
            reader.accept(transformedClassNode, 0);
        }
        assertEquals(5, transformedClassNode.fields.size(), "Should have 4 enum entries (values field + original 3 + 1 extension)");
        assertEquals("D", transformedClassNode.fields.get(4).name, "The new enum entry should be named D");
        assertNotNull(transformedClassNode.fields.get(4).invisibleAnnotations);
        assertEquals(1, transformedClassNode.fields.get(4).invisibleAnnotations.size(), "Should have one runtime-invisible annotation on the enum");
        assertEquals(Type.getDescriptor(ExtensionEnumEntry.class), transformedClassNode.fields.get(4).invisibleAnnotations.get(0).desc, "The annotation should be ExtensionEnumEntry");

        // Load the class and verify via reflection
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null)) {
            Class<?> loadedClass = classLoader.loadClass("net.neoforged.installertools.TestEnum");
            
            Field[] fields = loadedClass.getFields();
            assertEquals(4, fields.length, "Should have 4 enum entries (original 3 + 1 extension)");
            assertEquals("D", fields[3].getName(), "The new enum entry should be named D");
            assertEquals(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL | 0x4000 /* ACC_ENUM */, fields[3].getModifiers(), "The new enum entry should be public static final enum");
            assertTrue(fields[3].isEnumConstant(), "The new field should be an enum constant");
        }
    }
}

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
@interface ExtensionEnumEntry {
}

enum TestEnum {
    A, B, C;
}
