package net.neoforged.neoforgespi.transformation;

import net.neoforged.neoforgespi.ILaunchContext;

import java.util.List;

public interface IClassProcessorProvider {
    List<IClassProcessor> makeTransformers(ILaunchContext launchContext);
}
