package fmlbuild;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.ModuleRef;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class RunConfigurationsPlugin implements Plugin<Project> {
    @Inject
    public abstract JavaToolchainService getJavaToolchainService();

    @Override
    public void apply(Project project) {
        var logger = project.getLogger();

        var java = project.getExtensions().getByType(JavaPluginExtension.class);

        var sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        var runConfigurations = project.getObjects().domainObjectContainer(RunConfigurationSettings.class);
        project.getExtensions().add("runConfigurations", runConfigurations);
        runConfigurations.all(runConfiguration -> {
            var name = runConfiguration.getName();
            logger.info("Adding run configuration {}", name);
            var capitalizedName = Character.toUpperCase(name.charAt(0)) + name.substring(1);

            // Create a distinct source set to set up a separate classpath for this
            var sourceSet = sourceSets.create(name);
            var runtimeConfig = project.getConfigurations().getByName(sourceSet.getRuntimeOnlyConfigurationName());
            runtimeConfig.fromDependencyCollector(runConfiguration.getDependencies().getClasspath());
            runtimeConfig.fromDependencyCollector(runConfiguration.getDependencies().getModulepath());

            var runtimeModulesConfig = project.getConfigurations().create(getRuntimeModuleConfigName(runConfiguration), spec -> {
                spec.setDescription("The set of dependencies that should be put on the runtime module path");
                spec.setCanBeConsumed(false);
                spec.setCanBeResolved(true);
                var runtimeClasspathConfig = project.getConfigurations().getByName(sourceSet.getRuntimeClasspathConfigurationName());
                spec.shouldResolveConsistentlyWith(runtimeClasspathConfig);
            });
            runtimeModulesConfig.fromDependencyCollector(runConfiguration.getDependencies().getModulepath());

            var runTask = project.getTasks().register("run" + capitalizedName, JavaExec.class, task -> {
                task.getOutputs().upToDateWhen(ignored -> false);
                task.classpath(sourceSet.getRuntimeClasspath());
                task.getMainClass().set(runConfiguration.getMainClass());
                var jvmArguments = task.getJvmArguments();
                jvmArguments.addAll(runConfiguration.getJvmArguments());
                jvmArguments.addAll(runConfiguration.getSystemProperties().map(properties -> {
                    return properties.entrySet().stream().map(entry -> "-D" + entry.getKey() + "=" + entry.getValue()).toList();
                }));
                jvmArguments.addAll(runtimeModulesConfig.getIncoming().getArtifacts().getResolvedArtifacts().map(elements -> {
                    if (elements.isEmpty()) {
                        return List.of();
                    }
                    return List.of("-p", elements.stream()
                            .map(ResolvedArtifactResult::getFile)
                            .map(File::getAbsolutePath)
                            .collect(Collectors.joining(File.pathSeparator))
                    );
                }));

                // Use the project java version to launch
                task.getJavaLauncher().set(getJavaToolchainService().launcherFor(javaSpec -> {
                    javaSpec.getLanguageVersion().set(java.getToolchain().getLanguageVersion());
                }));

                // We need to pass some inputs to the task that cannot be set via Properties yet
                var taskInputs = task.getInputs();
                // To avoid accidentally tripping Gradle by passing a "File", we convert it to String in the provider
                taskInputs.property("runWorkingDirectory", runConfiguration.getWorkingDirectory().map(Directory::getAsFile).map(File::getAbsolutePath));
                taskInputs.property("runProgramArgs", runConfiguration.getProgramArguments());
                task.doFirst(RunConfigurationsPlugin::configureJavaExec);

                task.dependsOn(runConfiguration.getTasksBefore());
            });
            // I don't see a way to avoid querying this provider eagerly...
            project.afterEvaluate(ignored -> {
                runTask.configure(t -> t.setGroup(runConfiguration.getTaskGroup().get()));
            });
        });
        runConfigurations.whenObjectRemoved(installation -> {
            throw new GradleException("Cannot remove installations once they have been registered");
        });

        project.afterEvaluate(ignored -> {
            var ijRunConfigs = getIntelliJRunConfigurations(project);
            if (ijRunConfigs == null) {
                return;
            }

            for (var settings : runConfigurations) {
                var sourceSet = sourceSets.getByName(settings.getName());

                var runtimeModulesConfig = project.getConfigurations().getByName(getRuntimeModuleConfigName(settings));

                var app = new Application(settings.getIdeName().get(), project);
                app.setModuleRef(new ModuleRef(project, sourceSet)); // TODO: Use MDG utility since idea-ext is just wrong
                app.setMainClass(settings.getMainClass().get());
                var effectiveJvmArgs = new ArrayList<>(settings.getJvmArguments().get());
                for (var entry : settings.getSystemProperties().get().entrySet()) {
                    effectiveJvmArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
                }

                if (!runtimeModulesConfig.isEmpty()) {
                    effectiveJvmArgs.add("-p");
                    var modulePath = runtimeModulesConfig.getFiles().stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
                    effectiveJvmArgs.add(modulePath);
                }

                app.setJvmArgs(
                        effectiveJvmArgs.stream().map(RunUtils::escapeJvmArg).collect(Collectors.joining(" "))
                );
                app.setProgramParameters(
                        settings.getProgramArguments().get().stream().map(RunUtils::escapeJvmArg).collect(Collectors.joining(" "))
                );
                app.setWorkingDirectory(settings.getWorkingDirectory().getAsFile().get().getAbsolutePath());
                ijRunConfigs.add(app);
            }
        });
    }

    private static String getRuntimeModuleConfigName(RunConfigurationSettings runConfig) {
        return runConfig.getName() + "RuntimeModules";
    }

    private static void configureJavaExec(Task task) {
        var javaExec = (JavaExec) task;
        var inputProps = task.getInputs().getProperties();
        javaExec.workingDir(inputProps.get("runWorkingDirectory"));
        javaExec.args((List<?>) inputProps.get("runProgramArgs"));
    }

    private static @Nullable RunConfigurationContainer getIntelliJRunConfigurations(Project project) {
        var rootProject = project.getRootProject();

        var ideaModel = (IdeaModel) rootProject.getExtensions().findByName("idea");
        if (ideaModel == null) {
            return null;
        }
        var ideaProject = ideaModel.getProject();
        if (ideaProject == null) {
            return null;
        }
        var projectSettings = ((ExtensionAware) ideaProject).getExtensions().findByType(ProjectSettings.class);
        if (projectSettings == null) {
            return null;
        }
        return (RunConfigurationContainer) ((ExtensionAware) projectSettings).getExtensions().findByName("runConfigurations");
    }
}
