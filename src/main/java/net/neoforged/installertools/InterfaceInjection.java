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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class applies <a href="https://docs.neoforged.net/toolchain/docs/plugins/mdg/#interface-injection">interface injection</a>
 * data files, and optionally marks interfaces added in such a way with an annotation.
 */
public class InterfaceInjection {

    @Nullable
    private final String annotationMarker;

    private final Map<String, Set<String>> interfaces;

    public InterfaceInjection(List<File> injectionDataFiles, @Nullable String annotationMarker) {
        this.annotationMarker = annotationMarker;

        interfaces = new HashMap<>();

        Gson gson = new Gson();
        for (File file : injectionDataFiles) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(new BufferedReader(reader), JsonObject.class);
                for (String clazz : json.keySet()) {
                    JsonElement entry = json.get(clazz);

                    Set<String> injectedInterfaces = interfaces.computeIfAbsent(clazz, unused -> new LinkedHashSet<>());
                    if (entry.isJsonArray()) {
                        entry.getAsJsonArray().forEach(el -> injectedInterfaces.add(el.getAsString()));
                    } else {
                        injectedInterfaces.add(entry.getAsString());
                    }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to read interface injection data file " + file, exception);
            }
        }
    }

    public boolean containsClassTarget(Type classType) {
        return interfaces.containsKey(classType.getInternalName());
    }

    public void transform(ClassNode cn, Type type) {
        Set<String> injected = interfaces.get(type.getInternalName());
        if (injected == null) {
            return;
        }

        List<String> addedInterfaces = new ArrayList<>(injected);
        addedInterfaces.removeAll(cn.interfaces);

        if (addedInterfaces.isEmpty()) {
            return;
        }

        int startingInterfaceIndex = cn.interfaces.size();
        cn.interfaces.addAll(addedInterfaces);

        String signature = cn.signature;

        // Lazily create the signature if the class didn't have one yet, but we will be adding generics.
        // See https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-ClassSignature
        if (containsGenerics(addedInterfaces) && signature == null) {
            signature = "L" + cn.superName + ";" + cn.interfaces.stream().collect(Collectors.joining(
                    ";L", "L", ";"
            ));
        }

        if (signature != null) {
            cn.signature = SuperinterfaceSignatureConverter.convert(cn.signature, addedInterfaces);
        }

        if (annotationMarker != null) {
            if (cn.visibleTypeAnnotations == null) {
                cn.visibleTypeAnnotations = new ArrayList<>();
            }

            for (int i = 0; i < addedInterfaces.size(); i++) {
                int interfaceIndex = startingInterfaceIndex + i;
                int typeRef = TypeReference.newSuperTypeReference(interfaceIndex).getValue();
                cn.visitTypeAnnotation(typeRef, null, annotationMarker, true);
            }
        }
    }

    private boolean containsGenerics(List<String> addedInterfaces) {
        return addedInterfaces.stream().anyMatch(i -> i.contains("<"));
    }
}
