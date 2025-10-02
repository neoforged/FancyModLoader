/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.transformation;

import java.util.Set;

abstract sealed class BaseSimpleProcessor implements ClassProcessor permits SimpleClassProcessor, SimpleFieldProcessor, SimpleMethodProcessor {
    @Override
    public final void link(LinkContext context) {
        ClassProcessor.super.link(context);
    }

    @Override
    public final void afterProcessing(AfterProcessingContext context) {
        ClassProcessor.super.afterProcessing(context);
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        return Set.of(ClassProcessorIds.COREMODS_GROUP, ClassProcessorIds.COMPUTING_FRAMES);
    }
}
