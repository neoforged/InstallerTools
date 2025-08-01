package net.neoforged.installertools;

import net.neoforged.installertools.util.HashFunction;
import net.neoforged.installertools.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The Minecraft Server distribution uses a custom bundling mechanism by Mojang, which embeds original third
 * party libraries as jar files and uses a custom extractor. This class tries to read the metadata for
 * that bundler from a ZipFile and returns the information if found.
 */
final class BundleInfo {
    private final List<BundledFile> libraries;
    private final List<BundledFile> versions;

    private BundleInfo(List<BundledFile> libraries, List<BundledFile> versions) {
        this.libraries = libraries;
        this.versions = versions;
    }

    public List<BundledFile> getLibraries() {
        return libraries;
    }

    public List<BundledFile> getVersions() {
        return versions;
    }

    @Nullable
    public static BundleInfo of(ZipFile zf) throws IOException {
        Manifest manifest = readManifest(zf);
        if (manifest == null) {
            return null;
        }

        if (!"1.0".equals(manifest.getMainAttributes().getValue("Bundler-Format"))) {
            return null;
        }

        List<BundledFile> libraries = readFileList(zf, BundledFileType.LIBRARY, "META-INF/libraries.list");
        List<BundledFile> versions = readFileList(zf, BundledFileType.VERSION, "META-INF/versions.list");
        return new BundleInfo(libraries, versions);
    }

    public File extractAndReturnPrimaryJar(ZipFile zf, @Nullable File librariesFolder) throws IOException {
        // Extract libraries if requested
        if (librariesFolder != null) {
            for (BundledFile library : libraries) {
                extractBundledFile(zf, library, new File(librariesFolder, library.path));
            }
        }

        // Extract the primary jar last, since that means there's less chance of something going wrong
        BundledFile primaryJarEntry = findPrimaryJarEntry();

        File tempFile = File.createTempFile("minecraft", ".jar", librariesFolder);
        try {
            extractBundledFile(zf, primaryJarEntry, tempFile);
        } catch (Exception e) {
            // Clean up potentially created temp files
            tempFile.delete();
            throw e;
        }

        return tempFile;
    }

    private void extractBundledFile(ZipFile zf, BundledFile bundledFile, File destination) throws IOException {
        // Check if the existing file has the right hash
        if (destination.isFile()) {
            String existing = HashFunction.SHA256.hash(destination);
            if (bundledFile.hash.equals(existing)) {
                return;
            }
        }

        ZipEntry entry = zf.getEntry(bundledFile.getPathInBundle());
        if (entry == null) {
            throw new IOException("Bundled library " + bundledFile.path + " does not exist in the bundle.");
        }

        if (!destination.getParentFile().mkdirs() && !destination.getParentFile().isDirectory()) {
            throw new IOException("Failed to create parent directories for " + destination);
        }

        MessageDigest digest = HashFunction.SHA256.get();
        try (InputStream input = zf.getInputStream(entry);
             OutputStream output = new BufferedOutputStream(new DigestOutputStream(new FileOutputStream(destination), digest))) {
            Utils.copy(input, output);
        } catch (Exception e) {
            // Try deleting the destination if copying fails mid-way
            destination.delete();
            throw e;
        }

        String hash = HashFunction.SHA256.formatHash(digest);
        if (!bundledFile.hash.equals(hash)) {
            destination.delete();
            throw new IOException("Hash mismatch after extraction. " + bundledFile + " extracted with " + hash + " but bundle expects " + bundledFile.hash);
        }
    }

    private BundledFile findPrimaryJarEntry() throws IOException {
        // Try to find a jar in the versions list. If more than one exists, fail.
        BundledFile primaryJarEntry = null;
        for (BundledFile versionEntry : versions) {
            if (versionEntry.path.endsWith(".jar")) {
                if (primaryJarEntry != null) {
                    throw new IOException("Multiple candidates for the primary jar are bundled: " + primaryJarEntry + " and " + versionEntry);
                }
                primaryJarEntry = versionEntry;
            }
        }
        if (primaryJarEntry == null) {
            throw new IOException("No primary jar entry found in the bundled versions: " + versions);
        }
        return primaryJarEntry;
    }

    private static Manifest readManifest(ZipFile zf) throws IOException {
        ZipEntry entry = zf.getEntry(JarFile.MANIFEST_NAME);
        if (entry == null) {
            return null;
        }

        try (InputStream input = zf.getInputStream(entry)) {
            return new Manifest(new BufferedInputStream(input));
        }
    }

    private static List<BundledFile> readFileList(ZipFile zf, BundledFileType fileTypes, String path) throws IOException {
        ZipEntry entry = zf.getEntry(path);
        if (entry == null) {
            throw new IOException("Bundle file does not contain expected entry " + path);
        }

        List<BundledFile> ret = new ArrayList<>();
        try (InputStream in = zf.getInputStream(entry)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                String[] pts = line.split("\t");
                if (pts.length != 3)
                    throw new IllegalStateException("Invalid file list line: " + line);
                ret.add(new BundledFile(pts[0], pts[1], pts[2], fileTypes));
            }
        }
        return ret;
    }

    public enum BundledFileType {
        VERSION,
        LIBRARY
    }

    public static class BundledFile {
        private final String hash;
        @SuppressWarnings("unused")
        private final String id;
        private final String path;
        private final BundledFileType type;

        private BundledFile(String hash, String id, String path, BundledFileType type) {
            this.hash = hash;
            this.id = id;
            this.path = path;
            this.type = type;
        }

        public String getHash() {
            return hash;
        }

        public String getId() {
            return id;
        }

        public String getPath() {
            return path;
        }

        public BundledFileType getType() {
            return type;
        }

        @Override
        public String toString() {
            return path;
        }

        public String getPathInBundle() {
            if (type == BundledFileType.LIBRARY) {
                return "META-INF/libraries/" + path;
            } else {
                return "META-INF/versions/" + path;
            }
        }
    }
}
