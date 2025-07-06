package fmlbuild;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;

import javax.inject.Inject;

public abstract class NeoForgeServerInstallation extends NeoForgeInstallation {
    @Inject
    public NeoForgeServerInstallation(Project project, String name) {
        super(project, name);

        // Write the JVM args to files
        getNeoForgeJvmArgFile().set(getDirectory().file("neoforge_jvm_args.txt"));
        getNeoForgeMainClassArgFile().set(getDirectory().file("neoforge_mainclass.txt"));
        getNeoForgeProgramArgFile().set(getDirectory().file("neoforge_args.txt"));
    }

    /**
     * An JVM argfile with the necessary JVM args to launch will be written here.
     */
    public abstract RegularFileProperty getNeoForgeJvmArgFile();

    /**
     * An JVM argfile with the main class needed to launch will be written here.
     */
    public abstract RegularFileProperty getNeoForgeMainClassArgFile();

    /**
     * An argfile with the necessary program arguments will be written here.
     */
    public abstract RegularFileProperty getNeoForgeProgramArgFile();
}
