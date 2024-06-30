package fmlbuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Writes a /version.properties for inclusion in the Jar.
 */
@DisableCachingByDefault(because = "not worth caching")
public abstract class WriteVersionPropertiesTask extends DefaultTask {
    @Input
    abstract Property<String> getProjectVersion();

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void write() throws IOException {
        var versionProperties = getOutputDirectory().file("version.properties").get().getAsFile();
        var properties = new Properties();
        properties.setProperty("projectVersion", getProjectVersion().get());
        try (var writer = new FileWriter(versionProperties, StandardCharsets.UTF_8)) {
            properties.store(writer, "");
        }
    }
}
