package cpw.mods.modlauncher;

import org.jetbrains.annotations.Nullable;

public interface ClassHierarchyRecomputationContext {
    @Nullable
    Class<?> findLoadedClass(String name);

    byte[] upToFrames(String className) throws ClassNotFoundException;

    Class<?> locateParentClass(String className) throws ClassNotFoundException;
}
