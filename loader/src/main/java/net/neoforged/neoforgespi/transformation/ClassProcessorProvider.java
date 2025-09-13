/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.Collection;
import net.neoforged.neoforgespi.ILaunchContext;

public interface ClassProcessorProvider {
    Collection<ClassProcessor> makeTransformers(ILaunchContext launchContext);
}
