/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib.args;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import net.neoforged.fml.testlib.SimulatedInstallation;
import org.junit.jupiter.params.provider.ArgumentsSource;

@Retention(RetentionPolicy.RUNTIME)
@ArgumentsSource(InstallationTypeArgumentProvider.class)
@Repeatable(InstallationTypeSources.class)
public @interface InstallationTypeSource {
    SimulatedInstallation.Type[] value();
}
