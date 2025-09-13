/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.params.provider.ArgumentsSource;

@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(InstallationTypeArgumentProvider.class)
public @interface InstallationTypeSource {
    SimulatedInstallation.Type[] value();
}
