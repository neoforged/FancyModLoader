package fmlbuild;

import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.JavaExec;

public abstract class JmhTask extends JavaExec {
    @ServiceReference("jmhMutex")
    abstract Property<JmhMutexService> getServer();
}
