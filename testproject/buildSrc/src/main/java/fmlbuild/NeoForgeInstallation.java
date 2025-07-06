package fmlbuild;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

public abstract class NeoForgeInstallation implements Named {
    private final String name;

    public NeoForgeInstallation(Project project, String name) {
        this.name = name;
        getDirectory().convention(project.getLayout().getBuildDirectory().dir("installs/" + name));
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * The NeoForge version to install.
     */
    public abstract Property<String> getVersion();

    /**
     * The Minecraft version matching the NeoForge version.
     */
    public abstract Property<String> getMinecraftVersion();

    /**
     * Where the installation should be made.
     */
    public abstract DirectoryProperty getDirectory();
}
