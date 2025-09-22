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

import static cpw.mods.modlauncher.LogMarkers.MODLAUNCHER;

import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Decorates {@link cpw.mods.modlauncher.api.ITransformationService} to track state and other runtime metadata.
 */
public class TransformationServiceDecorator {
    private static final Logger LOGGER = LogManager.getLogger();
    private final ITransformationService service;

    @VisibleForTesting
    public TransformationServiceDecorator(ITransformationService service) {
        this.service = service;
    }

    public void gatherTransformers(TransformStore transformStore) {
        LOGGER.debug(MODLAUNCHER, "Initializing transformers for transformation service {}", this.service::name);
        final List<? extends ITransformer<?>> transformers = this.service.transformers();
        Objects.requireNonNull(transformers, "The transformers list should not be null");
        transformers.forEach(xform -> {
            final TargetType<?> targetType = xform.getTargetType();
            Objects.requireNonNull(targetType, "Transformer type must not be null");
            final Set<? extends ITransformer.Target<?>> targets = xform.targets();
            if (!targets.isEmpty()) {
                final Map<TargetType<?>, List<TransformTargetLabel>> targetTypeListMap = targets.stream()
                        .map(TransformTargetLabel::new)
                        .collect(Collectors.groupingBy(TransformTargetLabel::getTargetType));
                if (targetTypeListMap.keySet().size() > 1 || !targetTypeListMap.containsKey(targetType)) {
                    LOGGER.error(MODLAUNCHER, "Invalid target {} for transformer {}", targetType, xform);
                    throw new IllegalArgumentException("The transformer contains invalid targets");
                }
                targetTypeListMap.values()
                        .stream()
                        .flatMap(Collection::stream)
                        .forEach(target -> transformStore.addTransformer(target, xform, service));
            }
        });
        LOGGER.debug(MODLAUNCHER, "Initialized transformers for transformation service {}", this.service::name);
    }

    ITransformationService getService() {
        return service;
    }
}
