package net.neoforged.fml.testlib;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.params.provider.ArgumentsSource;

@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(InstallationTypeArgumentProvider.class)
public @interface InstallationTypeSource {
    SimulatedInstallation.Type[] value();
}
