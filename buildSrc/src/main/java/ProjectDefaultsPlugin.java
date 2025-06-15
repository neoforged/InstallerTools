import net.neoforged.gradleutils.GradleUtilsExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.util.Map;

public class ProjectDefaultsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var plugins = project.getPlugins();
        plugins.apply("maven-publish");
        plugins.apply("java-library");
        plugins.apply("net.neoforged.gradleutils");

        project.setGroup("net.neoforged.installertools");

        var java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.withJavadocJar();
        java.withSourcesJar();

        // We must support Java 8 due to it being the minimum supported version of the installer
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(8));

        var gradleUtilsExtension = project.getExtensions().getByType(GradleUtilsExtension.class);
        var projectVersion = gradleUtilsExtension.getVersion();
        project.setVersion(projectVersion);

        project.getLogger().lifecycle("{} version: {}", project.getName(), projectVersion);

        gradleUtilsExtension.setupSigning(project, true);

        var jarTask = project.getTasks().named("jar", Jar.class);
        jarTask.configure(task -> {
            var manifest = task.getManifest();
            manifest.attributes(Map.of("Implementation-Version", projectVersion.toString()));
        });
    }
}
