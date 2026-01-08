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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceInjectionTest implements @InjectedInterface Serializable {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testInjection(boolean withAnnotationMarker, @TempDir Path tempDir) throws Exception {
        // Create interface injection data file
        File injectionDataFile = tempDir.resolve("interface_injection.json").toFile();
        Map<String, String> injectionData = new HashMap<>();
        injectionData.put("net/neoforged/installertools/TestClass", "java/io/Serializable");

        try (FileWriter writer = new FileWriter(injectionDataFile)) {
            new Gson().toJson(injectionData, writer);
        }

        // Read TestClass bytecode using ASM
        String testClassName = TestClass.class.getName().replace('.', '/');
        ClassNode classNode = new ClassNode();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(testClassName + ".class")) {
            assertNotNull(is, "Could not find TestClass bytecode");
            ClassReader reader = new ClassReader(is);
            reader.accept(classNode, 0);
        }

        // Apply interface injection with annotation marker
        String annotationDescriptor = withAnnotationMarker ? Type.getDescriptor(InjectedInterface.class) : null;
        InterfaceInjection interfaceInjection = new InterfaceInjection(
            Collections.singletonList(injectionDataFile),
            annotationDescriptor
        );

        Type classType = Type.getObjectType(testClassName);
        interfaceInjection.transform(classNode, classType);

        // Write transformed TestClass to temp directory
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        byte[] testClassBytecode = writer.toByteArray();

        Path testClassFile = tempDir.resolve("net/neoforged/installertools/TestClass.class");
        Files.createDirectories(testClassFile.getParent());
        Files.write(testClassFile, testClassBytecode);

        // Copy InjectedInterface annotation to temp directory
        String annotationClassName = InjectedInterface.class.getName().replace('.', '/');
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(annotationClassName + ".class")) {
            assertNotNull(is, "Could not find InjectedInterface bytecode");
            Path annotationFile = tempDir.resolve(annotationClassName + ".class");
            Files.createDirectories(annotationFile.getParent());
            Files.copy(is, annotationFile);
        }

        // Load the class and verify via reflection
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null)) {
            Class<?> loadedClass = classLoader.loadClass("net.neoforged.installertools.TestClass");

            // Verify the interface was actually added
            Class<?>[] interfaces = loadedClass.getInterfaces();
            assertEquals(1, interfaces.length, "Should implement one interface");
            assertEquals(Serializable.class, interfaces[0], "Should implement Serializable");

            // Verify the type annotation on the interface using reflection
            AnnotatedType[] annotatedInterfaces = loadedClass.getAnnotatedInterfaces();
            assertEquals(1, annotatedInterfaces.length, "Should have one annotated interface");

            AnnotatedType annotatedInterface = annotatedInterfaces[0];
            assertEquals(Serializable.class, annotatedInterface.getType(), "Annotated type should be Serializable");

            // Verify the annotationMarker is present
            Annotation[] annotations = annotatedInterface.getAnnotations();
            if (withAnnotationMarker) {
                assertEquals(1, annotations.length, "Should have one annotation on the interface");
                assertEquals("net.neoforged.installertools.InjectedInterface", annotations[0].annotationType().getName());
            } else {
                assertEquals(0, annotations.length);
            }
        }
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface InjectedInterface {
}

class TestClass {}
