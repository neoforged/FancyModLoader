/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2019 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher;

import cpw.mods.cl.ModuleClassLoader;
import java.lang.module.Configuration;
import java.util.List;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Module transforming class loader
 */
public class TransformingClassLoader extends ModuleClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }
    private final ClassTransformer classTransformer;

    @VisibleForTesting
    public TransformingClassLoader(ClassTransformer classTransformer, final Configuration configuration, List<ModuleLayer> parentLayers, ClassLoader parentClassLoader) {
        super("TRANSFORMER", configuration, parentLayers, parentClassLoader);
        this.classTransformer = classTransformer;
        classTransformer.linkBytecodeProviders(name -> className -> this.buildTransformedClassNodeFor(className, name));
    }

    @Override
    protected byte[] maybeTransformClassBytes(final byte[] bytes, final String name, final @Nullable String upToTransformer) {
        var upToTransformerName = upToTransformer == null ? null : ProcessorName.parse(upToTransformer);
        return classTransformer.transform(bytes, name, upToTransformerName, new ClassHierarchyRecomputationContext() {
            @Override
            public @Nullable Class<?> findLoadedClass(String name) {
                return TransformingClassLoader.this.getLoadedClass(name);
            }

            @Override
            public byte[] upToFrames(String className) throws ClassNotFoundException {
                return TransformingClassLoader.this.buildTransformedClassNodeFor(className, ClassProcessorIds.COMPUTING_FRAMES);
            }

            @Override
            public Class<?> locateParentClass(String className) throws ClassNotFoundException {
                return Class.forName(className, false, TransformingClassLoader.this.getParent());
            }
        });
    }

    private Class<?> getLoadedClass(String name) {
        return findLoadedClass(name);
    }

    byte[] buildTransformedClassNodeFor(final String className, final ProcessorName upToTransformer) throws ClassNotFoundException {
        return super.getMaybeTransformedClassBytes(className, upToTransformer.toString());
    }
}
