import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public class ShadowDefaultsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply("com.gradleup.shadow");

        project.getTasks().named("shadowJar", AbstractArchiveTask.class, task -> {
            task.getArchiveClassifier().set("fatjar");
        });
    }
}
