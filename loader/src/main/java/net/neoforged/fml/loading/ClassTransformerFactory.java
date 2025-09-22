/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformerAuditTrail;
import java.util.List;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.transformation.ClassProcessor;

/**
 * Responsible for creating the {@link ClassTransformer} based on the available class processors.
 */
final class ClassTransformerFactory {
    private ClassTransformerFactory() {}

    public static ClassTransformer create(ILaunchContext launchContext,
            List<ClassProcessor> classProcessors) {
        var transformStore = new TransformStore(classProcessors);

        var auditTrail = new TransformerAuditTrail();
        // TODO: what?
        //environment.computePropertyIfAbsent(IEnvironment.Keys.AUDITTRAIL.get(), v -> auditTrail);
        return new ClassTransformer(transformStore, auditTrail);
    }
}
