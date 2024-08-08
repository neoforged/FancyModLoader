package fmlbuild;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;

import javax.inject.Inject;

public abstract class NeoForgeClientInstallation extends NeoForgeInstallation {
    @Inject
    public NeoForgeClientInstallation(Project project, String name) {
        super(project, name);
        getVanillaJvmArgFile().convention(getDirectory().file("vanilla_jvm_args.txt"));
        getVanillaMainClassArgFile().convention(getDirectory().file("vanilla_main_class.txt"));
        getVanillaProgramArgFile().convention(getDirectory().file("vanilla_args.txt"));
        getNeoForgeJvmArgFile().convention(getDirectory().file("neoforge_jvm_args.txt"));
        getNeoForgeMainClassArgFile().convention(getDirectory().file("neoforge_main_class.txt"));
        getNeoForgeProgramArgFile().convention(getDirectory().file("neoforge_args.txt"));
    }

    /**
     * An argfile with the program arguments defined by the Vanilla launcher profile will be written here.
     */
    public abstract RegularFileProperty getVanillaJvmArgFile();

    /**
     * An argfile with the main class defined in the Vanilla launcher profile will be written here.
     */
    public abstract RegularFileProperty getVanillaMainClassArgFile();

    /**
     * An argfile with the program arguments defined by the Vanilla launcher profile will be written here.
     */
    public abstract RegularFileProperty getVanillaProgramArgFile();

    /**
     * An argfile with the JVM args defined in the NeoForge launcher profile will be written here.
     */
    public abstract RegularFileProperty getNeoForgeJvmArgFile();

    /**
     * An argfile with the main class defined in the NeoForge launcher profile will be written here.
     */
    public abstract RegularFileProperty getNeoForgeMainClassArgFile();

    /**
     * An argfile with the program args defined in the NeoForge launcher profile will be written here.
     */
    public abstract RegularFileProperty getNeoForgeProgramArgFile();
}
