package net.neoforged.fml.testlib;

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
