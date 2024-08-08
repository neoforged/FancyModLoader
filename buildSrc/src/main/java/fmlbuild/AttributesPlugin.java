package fmlbuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;

/**
 * Adds the attributes used by NeoForge, NeoForm and Minecraft Gradle module metadata.
 */
public class AttributesPlugin implements Plugin<Project> {
    private static final Attribute<String> ATTRIBUTE_DISTRIBUTION = Attribute.of("net.neoforged.distribution", String.class);
    private static final Attribute<String> ATTRIBUTE_OPERATING_SYSTEM = Attribute.of("net.neoforged.operatingsystem", String.class);

    @Override
    public void apply(Project project) {
        project.getDependencies().attributesSchema(attributesSchema -> {
            attributesSchema.attribute(ATTRIBUTE_DISTRIBUTION).getDisambiguationRules().add(DistributionDisambiguation.class);
            attributesSchema.attribute(ATTRIBUTE_OPERATING_SYSTEM).getDisambiguationRules().add(OperatingSystemDisambiguation.class);
        });
    }
}

// The production server configuration has to be given the attribute to get server dependencies
abstract class DistributionDisambiguation implements AttributeDisambiguationRule<String> {
    @Override
    public void execute(MultipleCandidatesDetails<String> details) {
        details.closestMatch("client");
    }
}

/**
 * This disambiguation rule will select native dependencies based on the operating system Gradle is currently running on.
 */
abstract class OperatingSystemDisambiguation implements AttributeDisambiguationRule<String> {
    @Override
    public void execute(MultipleCandidatesDetails<String> details) {
        details.closestMatch(switch (OperatingSystem.current()) {
            case LINUX -> "linux";
            case MACOS -> "osx";
            case WINDOWS -> "windows";
        });
    }
}
