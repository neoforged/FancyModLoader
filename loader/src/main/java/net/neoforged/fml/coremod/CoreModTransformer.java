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

import java.util.Set;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

/**
 * A targeted bytecode transformer that can be provided by a {@link net.neoforged.neoforgespi.transformation.ClassProcessorProvider}.
 */
public sealed interface CoreModTransformer permits CoreModClassTransformer, CoreModMethodTransformer, CoreModFieldTransformer {
    /**
     * {@return a unique name for this transformer}
     */
    ProcessorName name();

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
}
