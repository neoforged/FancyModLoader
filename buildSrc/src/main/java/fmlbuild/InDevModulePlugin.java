package fmlbuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

public class InDevModulePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        var mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        var generatedVersionDir = project.getLayout().getBuildDirectory().dir("generated/version");
        var createVersionProperties = project.getTasks().register("createVersionProperties", WriteVersionPropertiesTask.class, task -> {
            task.setDescription("Generates a Module version properties file for use during development and containing more information for production as well.");
            task.getOutputDirectory().set(generatedVersionDir);
            task.getProjectVersion().set(project.provider(() -> project.getVersion().toString()));
        });
        mainSourceSet.getResources().srcDir(createVersionProperties.flatMap(WriteVersionPropertiesTask::getOutputDirectory));
    }
}
