package fmlbuild;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class RunConfigurationSettings implements Named {
    private final String name;

    @Inject
    public RunConfigurationSettings(Project project, String name) {
        this.name = name;
        getIdeName().convention(name);
        getWorkingDirectory().convention(project.getLayout().getProjectDirectory());
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * The Gradle group to put the task into.
     */
    public abstract Property<String> getTaskGroup();

    /**
     * Name for the run configuration in the IDE.
     */
    public abstract Property<String> getIdeName();

    /**
     * The main class to launch.
     */
    public abstract Property<String> getMainClass();

    /**
     * The working directory to launch in.
     */
    public abstract DirectoryProperty getWorkingDirectory();

    /**
     * The program arguments to launch with.
     */
    public abstract ListProperty<String> getProgramArguments();

    /**
     * The JVM arguments to launch with.
     */
    public abstract ListProperty<String> getJvmArguments();

    /**
     * Additional system properties to add to the JVM arguments.
     */
    public abstract MapProperty<String, String> getSystemProperties();

    /**
     * Additional dependencies to add to the class- and module-path.
     */
    @Nested
    public abstract RunConfigurationDependencies getDependencies();

    public void dependencies(Action<? super RunConfigurationDependencies> action) {
        action.execute(getDependencies());
    }
}
