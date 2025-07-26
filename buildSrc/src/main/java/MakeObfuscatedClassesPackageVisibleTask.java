import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * For the Zip Transform library, we let ProGuard obfuscate all third-party dependencies and move them
 * into the same package as the library implementation.
 * Since the library only has a single package, we can make all third party classes package-private,
 * but sadly ProGuard has no optimization for this.
 */
public abstract class MakeObfuscatedClassesPackageVisibleTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputFiles();

    @Input
    public abstract SetProperty<String> getClassWhitelist();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void run() throws IOException {

        Set<String> classWhitelist = getClassWhitelist().get();

        try (var zf = new ZipFile(getInputFiles().getSingleFile());
             var out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(getOutputFile().getAsFile().get())))) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();

                if (entry.isDirectory()) {
                    out.putNextEntry(entry);
                    out.closeEntry();
                    continue;
                }

                byte[] entryData;
                try (var entryIn = zf.getInputStream(entry)) {
                    entryData = entryIn.readAllBytes();
                }

                // Clear the access flags from classes that don't match our whitelist.
                if (entry.getName().endsWith(".class") && !classWhitelist.contains(entry.getName())) {
                    ClassReader classReader = new ClassReader(entryData);
                    ClassWriter classWriter = new ClassWriter(0);

                    ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
                        @Override
                        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                            access &= ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
                            super.visit(version, access, name, signature, superName, interfaces);
                        }
                    };

                    classReader.accept(classVisitor, 0);
                    entryData = classWriter.toByteArray();
                }

                out.putNextEntry(entry);
                out.write(entryData);
                out.closeEntry();
            }
        }

    }
}
