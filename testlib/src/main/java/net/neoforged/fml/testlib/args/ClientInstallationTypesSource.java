/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib.args;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import net.neoforged.fml.testlib.SimulatedInstallation;

/**
 * Supplies all installation types from development.
 */
@Retention(RetentionPolicy.RUNTIME)
@InstallationTypeSource({ SimulatedInstallation.Type.USERDEV, SimulatedInstallation.Type.USERDEV_LEGACY, SimulatedInstallation.Type.PRODUCTION_CLIENT })
public @interface ClientInstallationTypesSource {}
