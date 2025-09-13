package net.neoforged.neoforgespi.transformation;

import java.util.Collection;
import net.neoforged.neoforgespi.ILaunchContext;

public interface ClassProcessorProvider {
    Collection<ClassProcessor> makeTransformers(ILaunchContext launchContext);
}
