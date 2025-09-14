/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib.args;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import net.neoforged.fml.testlib.SimulatedInstallation;

/**
 * Supplies all installation types from production.
 */
@Retention(RetentionPolicy.RUNTIME)
@InstallationTypeSource({ SimulatedInstallation.Type.PRODUCTION_CLIENT, SimulatedInstallation.Type.PRODUCTION_SERVER })
public @interface ProductionInstallationTypesSource {}
