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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ClassProcessorMetadata;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Type;

@ApiStatus.Internal
public class TransformStore {
    private final List<ClassProcessor> sortedProcessors;
    private final Set<ProcessorName> markerProcessors;

    TransformStore(List<ClassProcessor> sortedProcessors, Set<ProcessorName> markers) {
        CrashReportCallables.registerCrashCallable("Class Processors", () -> ClassTransformStatistics.computeCrashReportEntry(this));
        this.sortedProcessors = List.copyOf(sortedProcessors);
        this.markerProcessors = Set.copyOf(markers);
    }

    @VisibleForTesting
    public TransformStore(List<ClassProcessor> sortedProcessors) {
        this(sortedProcessors, Set.of());
    }

    public boolean isMarker(ClassProcessorMetadata processor) {
        return markerProcessors.contains(processor.name());
    }

    List<ClassProcessor> getSortedProcessors() {
        return sortedProcessors;
    }

    public List<ClassProcessor> transformersFor(Type classDesc, boolean isEmpty, ProcessorName upToTransformer) {
        var out = new ArrayList<ClassProcessor>();
        boolean includesComputingFrames = false;
        for (var transformer : sortedProcessors) {
            if (upToTransformer != null && upToTransformer.equals(transformer.metadata().name())) {
                break;
            } else if (ClassProcessorIds.COMPUTING_FRAMES.equals(transformer.metadata().name())) {
                includesComputingFrames = true;
                out.add(transformer);
            } else {
                ClassTransformStatistics.incrementAskedForTransform(transformer.metadata());

                var context = new ClassProcessor.SelectionContext(classDesc, isEmpty);
                if (transformer.handlesClass(context)) {
                    ClassTransformStatistics.incrementTransforms(transformer.metadata());
                    out.add(transformer);
                }
            }
        }
        if ((out.size() == 1 && includesComputingFrames)) {
            // The class does not actually require any transformation, as the only transformer present is the special
            // no-op marker for where class hierarchy computation in frame computation goes up to, and potentially the
            // marker for where results are fixed and may be responded to.
            return List.of();
        }
        return out;
    }
}
