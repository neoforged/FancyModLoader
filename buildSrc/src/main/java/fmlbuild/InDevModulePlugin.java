package fmlbuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public class InDevModulePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);

        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        var mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        var outputDir = project.getLayout().getBuildDirectory().dir("generated/inDevModule");

        var tasks = project.getTasks();

        var generateTask = tasks.register("generateInDevModuleManifest", GenerateInDevModuleManifestTask.class, task -> {
            var moduleName = project.getName();
            task.getOutputDirectory().set(outputDir);
            task.getClassesFolders().from(mainSourceSet.getOutput().getClassesDirs());
            task.getResourcesFolder().set(mainSourceSet.getOutput().getResourcesDir());
            task.getModuleName().set(moduleName);
        });

        var runtimeClasspath = project.getConfigurations().getByName(mainSourceSet.getRuntimeClasspathConfigurationName());
        runtimeClasspath.withDependencies(dependencies -> {
            dependencies.addLater(generateTask.map(task -> project.files(task.getOutputDirectory()))
                    .map(project.getDependencyFactory()::create));
        });

    }
}
