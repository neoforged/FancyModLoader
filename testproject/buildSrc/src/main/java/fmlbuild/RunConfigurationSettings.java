package fmlbuild;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class RunConfigurationSettings implements Named {
    private final String name;

    /**
     * The Gradle tasks that should be run before running this run.
     */
    private List<TaskProvider<?>> tasksBefore = new ArrayList<>();

    @Inject
    public RunConfigurationSettings(Project project, String name) {
        this.name = name;
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

    /**
     * Gets the Gradle tasks that should be run before running this run.
     */
    public List<TaskProvider<?>> getTasksBefore() {
        return tasksBefore;
    }

    /**
     * Sets the Gradle tasks that should be run before running this run.
     * This also slows down running through your IDE since it will first execute Gradle to run the requested
     * tasks, and then run the actual game.
     */
    public void setTasksBefore(List<TaskProvider<?>> taskNames) {
        this.tasksBefore = new ArrayList<>(Objects.requireNonNull(taskNames, "taskNames"));
    }

    /**
     * Configures the given Task to be run before launching the game.
     * This also slows down running through your IDE since it will first execute Gradle to run the requested
     * tasks, and then run the actual game.
     */
    public void taskBefore(TaskProvider<?> task) {
        this.tasksBefore.add(task);
    }

}
