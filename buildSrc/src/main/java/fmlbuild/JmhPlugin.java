package fmlbuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Registers a shared build service that is solely used to limit the concurrency of JMH to 1,
 * since JMH refuses to run in parallel, and since it's a benchmark, that would also not be advised.
 */
public class JmhPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getGradle().getSharedServices().registerIfAbsent("jmhMutex", JmhMutexService.class, spec -> {
            spec.getMaxParallelUsages().set(1);
        });
    }
}
