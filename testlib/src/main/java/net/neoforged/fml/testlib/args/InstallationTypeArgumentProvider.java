/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.testlib.args;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.AnnotationBasedArgumentsProvider;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.support.ParameterDeclarations;

public class InstallationTypeArgumentProvider extends AnnotationBasedArgumentsProvider<InstallationTypeSource> {
    @Override
    protected Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context,
            InstallationTypeSource annotation) {
        return Arrays.stream(annotation.value()).map(Arguments::of);
    }
}
