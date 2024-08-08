package fmlbuild;

import org.gradle.api.artifacts.dsl.Dependencies;
import org.gradle.api.artifacts.dsl.DependencyCollector;

/**
 * Additional dependencies of the run configuration.
 */
public interface RunConfigurationDependencies extends Dependencies {
    /**
     * Additional dependencies to put on the classpath.
     */
    DependencyCollector getClasspath();

    /**
     * Additional dependencies to put on the module path.
     */
    DependencyCollector getModulepath();
}
