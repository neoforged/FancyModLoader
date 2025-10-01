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

package net.neoforged.fml.coremod;

import java.util.Locale;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * A bytecode transformer that is provided by a {@linkplain CoreMod core mod}.
 * <p>
 * {@link CoreModTransformer}s must be named. FML will attempt to generate a name given the owner of the transformer
 * and its class name, but if more than one is provided that uses the same implementing class, it must override
 * {@link #name()}.
 */
public sealed interface CoreModTransformer permits CoreModClassTransformer, CoreModMethodTransformer, CoreModFieldTransformer {
    /**
     * {@return a unique name for this transformer. Defaults to a name derived from the source class and mod file names}
     */
    default ProcessorName name() {
        return new ProcessorName(
                getOwnerName(getClass()),
                getClass().getName().replace('$', '.').toLowerCase(Locale.ROOT));
    }

    /**
     * {@return processors or transformers that this transformer must run before}
     */
    default Set<ProcessorName> runsBefore() {
        return Set.of();
    }

    /**
     * {@return processors or transformers that this transformer must run after} Defaults to running after {@link ClassProcessorIds#COREMODS_GROUP}, which runs after mixins.
     */
    default Set<ProcessorName> runsAfter() {
        return Set.of(ClassProcessorIds.COREMODS_GROUP);
    }

    ClassProcessor toProcessor();

    private static String getOwnerName(Class<? extends CoreModTransformer> clazz) {
        var module = clazz.getModule();
        if (module.isNamed()) {
            return module.getName();
        }
        var modFile = FMLLoader.getCurrent().getModFileByClass(clazz);
        if (modFile != null) {
            return modFile.getId();
        }
        throw new IllegalStateException("Cannot determine owner name for " + clazz + ", it is not in a named module and not loaded from a mod file");
    }
}
