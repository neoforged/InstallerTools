package net.neoforged.installertools;

import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;

import java.util.List;

public class SuperinterfaceSignatureConverter {
    public static String convert(String existingSignature, List<String> addedInterfaces) {
        SignatureWriter writer = new SignatureWriter();
        new SignatureReader(existingSignature).accept(writer);

        for (String iface : addedInterfaces) {
            writeInterfaceSignature(removeWhitespace(iface), writer);
        }

        return writer.toString();
    }

    private static String removeWhitespace(String iface) {
        return iface.replaceAll("\\s", "");
    }

    private static void writeInterfaceSignature(String iface, SignatureWriter writer) {
        int genericStart = iface.indexOf('<');
        int genericEnd = iface.lastIndexOf('>');
        if (genericStart == -1) {
            writer.visitInterface();
            writer.visitClassType(iface);
            writer.visitEnd();
        } else {
            writer.visitInterface();
            writer.visitClassType(iface.substring(0, genericStart));
            String generics = iface.substring(genericStart + 1, genericEnd).replace('.', '/');
            for (String param : splitGenerics(generics)) {
                writeTypeArgument(param.trim(), writer);
            }
            writer.visitEnd();
        }
    }

    private static String[] splitGenerics(String generics) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : generics.toCharArray()) {
            if (c == '<') depth++;
            if (c == '>') depth--;
            if (c == ',' && depth == 0) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result.toArray(new String[0]);
    }

    private static void writeTypeArgument(String param, SignatureWriter sw) {
        // Wildcards are supported for nested generics, so let's always write them
        if (param.startsWith("?super")) {
            sw.visitTypeArgument('-');
            param = param.substring(6);
        } else if (param.startsWith("?extends")) {
            sw.visitTypeArgument('+');
            param = param.substring(8);
        } else {
            sw.visitTypeArgument('=');
        }
        writeType(param, sw);
    }

    private static void writeType(String type, SignatureWriter sw) {
        int genericStart = type.indexOf('<');
        int genericEnd = type.lastIndexOf('>');
        if (genericStart == -1) {
            if (type.contains("/")) {
                sw.visitClassType(type);
                sw.visitEnd();
            } else {
                sw.visitTypeVariable(type);
            }
        } else {
            String base = type.substring(0, genericStart);
            sw.visitClassType(base);
            String generics = type.substring(genericStart + 1, genericEnd);
            for (String param : splitGenerics(generics)) {
                writeTypeArgument(param.trim(), sw);
            }
            sw.visitEnd();
        }
    }
}
