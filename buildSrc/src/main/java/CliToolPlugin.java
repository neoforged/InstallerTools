import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;

import java.util.Map;

public class CliToolPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply("application");
        project.getPlugins().apply("com.gradleup.shadow");

        project.getTasks().named("shadowJar", AbstractArchiveTask.class, task -> {
            task.getArchiveClassifier().set("fatjar");
        });

        var applicationExtension = project.getExtensions().getByType(JavaApplication.class);
        project.getTasks().named("jar", Jar.class, task -> {
            var mainClass = applicationExtension.getMainClass().getOrNull();
            if (mainClass == null) {
                throw new InvalidUserCodeException("No application main class was set when the Jar task was configured.");
            }
            task.getManifest().attributes(Map.of("Main-Class", mainClass));
        });
    }
}
