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

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TargetType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Transformer store - holds all the transformers
 */
public class TransformStore {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Map<String, ClassTransformations> transforms = new HashMap<>();
    private final IdentityHashMap<ITransformer<?>, String> ownerTracking = new IdentityHashMap<>();

    /**
     * @param className The classes binary name
     */
    ClassTransformations getClassTransforms(String className) {
        return transforms.get(normalizeClass(className));
    }

    private ClassTransformations getOrCreateClassTransforms(String className) {
        return transforms.computeIfAbsent(normalizeClass(className), ignored -> new ClassTransformations());
    }

    private static String normalizeClass(String className) {
        return className.replace('.', '/');
    }

    @SuppressWarnings("unchecked")
    public <T> void addTransformer(ITransformer<T> xform, @Nullable String owner) {
        var targetType = Objects.requireNonNull(xform.getTargetType(), "Transformer type must not be null");

        // Validate that the target type matches all targets
        for (var target : xform.targets()) {
            if (target.targetType() != targetType) {
                throw new IllegalArgumentException("Transformer " + xform + " has target " + target
                        + " that doesn't match its own target type " + targetType);
            }
        }

        if (targetType == TargetType.PRE_CLASS) {
            addPreClassTransformer((ITransformer<ClassNode>) xform);
        } else if (targetType == TargetType.CLASS) {
            addClassTransformer((ITransformer<ClassNode>) xform);
        } else if (targetType == TargetType.METHOD) {
            addMethodTransformer((ITransformer<MethodNode>) xform);
        } else if (targetType == TargetType.FIELD) {
            addFieldTransformer((ITransformer<FieldNode>) xform);
        } else {
            throw new IllegalArgumentException("Unrecognized target type: " + targetType);
        }

        if (owner != null) {
            ownerTracking.put(xform, owner);
        }
    }

    private void addPreClassTransformer(ITransformer<ClassNode> transformer) {
        for (var target : transformer.targets()) {
            LOGGER.debug(MODLAUNCHER, "Adding pre-class transformer {} to {}", transformer, target.className());
            getOrCreateClassTransforms(target.className()).preTransformers.add(transformer);
        }
    }

    private void addClassTransformer(ITransformer<ClassNode> transformer) {
        for (var target : transformer.targets()) {
            LOGGER.debug(MODLAUNCHER, "Adding class transformer {} to {}", transformer, target.className());
            getOrCreateClassTransforms(target.className()).postTransformers.add(transformer);
        }
    }

    private void addMethodTransformer(ITransformer<MethodNode> transformer) {
        for (var target : transformer.targets()) {
            LOGGER.debug(MODLAUNCHER, "Adding method transformer {} to {}#{}{}", transformer, target.className(), target.elementName(), target.elementDescriptor());
            var elementKey = new ClassElementKey(target.elementName(), target.elementDescriptor());
            getOrCreateClassTransforms(target.className()).methodTransformers.computeIfAbsent(elementKey, ignored -> new ArrayList<>()).add(transformer);
        }
    }

    private void addFieldTransformer(ITransformer<FieldNode> transformer) {
        for (var target : transformer.targets()) {
            LOGGER.debug(MODLAUNCHER, "Adding field transformer {} to {}#{}{}", transformer, target.className(), target.elementName(), target.elementDescriptor());
            if (target.elementDescriptor().isEmpty()) {
                getOrCreateClassTransforms(target.className()).legacyFieldTransformers.computeIfAbsent(target.elementName(), ignored -> new ArrayList<>()).add(transformer);
            } else {
                var elementKey = new ClassElementKey(target.elementName(), target.elementDescriptor());
                getOrCreateClassTransforms(target.className()).fieldTransformers.computeIfAbsent(elementKey, ignored -> new ArrayList<>()).add(transformer);
            }
        }
    }

    @Nullable
    public String getOwner(ITransformer<?> transformer) {
        return ownerTracking.get(transformer);
    }

    /**
     * Requires source-form class name (java.lang.String)
     */
    boolean needsTransforming(String className) {
        return transforms.containsKey(className);
    }

    Set<String> getTransformedClasses() {
        return transforms.keySet();
    }

    static final class ClassTransformations {
        final List<ITransformer<ClassNode>> preTransformers = new ArrayList<>();
        final Map<ClassElementKey, List<ITransformer<MethodNode>>> methodTransformers = new HashMap<>();
        final Map<ClassElementKey, List<ITransformer<FieldNode>>> fieldTransformers = new HashMap<>();
        // Field transformers without a descriptor apply to all fields of that name
        final Map<String, List<ITransformer<FieldNode>>> legacyFieldTransformers = new HashMap<>();
        final List<ITransformer<ClassNode>> postTransformers = new ArrayList<>();

        public List<ITransformer<FieldNode>> getForField(String name, String desc) {
            var legacy = legacyFieldTransformers.get(name);
            var transforms = fieldTransformers.get(new ClassElementKey(name, desc));
            if (legacy != null && transforms != null) {
                var combined = new ArrayList<ITransformer<FieldNode>>(legacy.size() + transforms.size());
                combined.addAll(legacy);
                combined.addAll(transforms);
                return combined;
            } else if (legacy != null) {
                return legacy;
            } else if (transforms != null) {
                return transforms;
            } else {
                return List.of();
            }
        }

        public List<ITransformer<MethodNode>> getForMethod(String name, String desc) {
            return methodTransformers.getOrDefault(new ClassElementKey(name, desc), List.of());
        }
    }

    record ClassElementKey(String name, String descriptor) {}

    public Collection<ITransformer<?>> getTransformers() {
        return ownerTracking.keySet();
    }
}
