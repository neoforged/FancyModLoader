package fmlbuild;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * Generates the FMLVersionProperties class.
 */
public abstract class GenerateVersionClass extends DefaultTask {
    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<String> getClassName();

    @OutputDirectory
    public abstract RegularFileProperty getOutput();

    @Inject
    public GenerateVersionClass(Project project) {
        getVersion().convention(project.provider(() -> project.getVersion().toString()));
    }

    @TaskAction
    public void writeVersion() throws IOException {
        var className = getClassName().get();
        var packageName = className.substring(0, className.lastIndexOf('.'));
        var shortClassName = className.substring(className.lastIndexOf('.') + 1);
        var destinationPath = new File(getOutput().getAsFile().get(), className.replace('.', '/') + ".java");
        destinationPath.getParentFile().mkdirs();

        var classContent = new ArrayList<String>();
        classContent.add("package " + packageName + ";");
        classContent.add("final class " + shortClassName + " {");
        classContent.add(" private " + shortClassName + "() {}");
        classContent.add(" static final String VERSION = \"" + getVersion().get() + "\";");
        classContent.add("}");

        Files.write(destinationPath.toPath(), classContent, StandardCharsets.UTF_8);
    }

}
