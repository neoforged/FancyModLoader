package net.neoforged.neoforgespi.transformation;

import net.neoforged.neoforgespi.ILaunchContext;

import java.util.List;

public interface ClassProcessorProvider {
    List<ClassProcessor> makeTransformers(ILaunchContext launchContext);
}
