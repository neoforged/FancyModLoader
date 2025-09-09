package net.neoforged.neoforgespi.transformation;

import net.neoforged.neoforgespi.ILaunchContext;

import java.util.Collection;
import java.util.List;

public interface ClassProcessorProvider {
    Collection<ClassProcessor> makeTransformers(ILaunchContext launchContext);
}
