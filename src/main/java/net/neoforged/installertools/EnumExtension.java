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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

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
 * This class applies <a href="https://docs.neoforged.net/docs/advanced/extensibleenums/">enum extension</a> data files,
 * and marks enums extended in such a way with an annotation.
 * <p>
 * <strong>Note that only a subset of FML's <code>RuntimeEnumExtender</code>'s functionality is re-implemented here.
 * Thus, the resulting class files will not work at runtime, unless FML is present to replace the injected enum entries
 * in a way that gives the proper ordering guarantees.</strong>
 */
public class EnumExtension {
    private final String annotationMarker;

    // Enum class -> entries to add
    private final Map<String, Set<String>> extensions;

    public EnumExtension(List<File> extensionDataFiles, String annotationMarker) {
        this.annotationMarker = annotationMarker;
        
        extensions = new HashMap<>();
        Gson gson = new Gson();
        for (File file : extensionDataFiles) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                JsonObject json = gson.fromJson(new BufferedReader(reader), JsonObject.class);
                if (json.has("entries")) {
                    JsonArray entries = json.getAsJsonArray("entries");
                    for (JsonElement entry : entries) {
                        JsonObject entryObj = entry.getAsJsonObject();
                        String enumName = entryObj.getAsJsonPrimitive("enum").getAsString();
                        String entryName = entryObj.getAsJsonPrimitive("name").getAsString();
                        extensions.computeIfAbsent(enumName, unused -> new LinkedHashSet<>()).add(entryName);
                    }
                }
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to read interface injection data file " + file, exception);
            }
        }
    }

    public boolean containsClassTarget(Type classType) {
        return extensions.containsKey(classType.getInternalName());
    }

    public void transform(ClassNode cn, Type type) {
        Set<String> entries = extensions.get(type.getInternalName());
        List<String> sortedEntries = entries.stream().sorted().collect(Collectors.toList());
        for (String entry : sortedEntries) {
            FieldNode field = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, entry, type.getDescriptor(), null, null);
            List<AnnotationNode> invisibleAnnotations = new ArrayList<>();
            invisibleAnnotations.add(new AnnotationNode(
                    Type.getObjectType(annotationMarker).getDescriptor()
            ));
            field.invisibleAnnotations = invisibleAnnotations;
            cn.fields.add(field);
        }
    }
}
