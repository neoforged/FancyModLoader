/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.language;


import java.lang.annotation.ElementType;
import java.util.*;
import java.util.function.Predicate;

import org.objectweb.asm.Type;

public class ModFileScanData
{
    private final Set<AnnotationData> annotations = new LinkedHashSet<>();
    private final Set<ClassData> classes = new LinkedHashSet<>();
    private final Map<String,IModLanguageProvider.IModLanguageLoader> modTargets = new HashMap<>();
    private final List<IModFileInfo> modFiles = new ArrayList<>();

    public static Predicate<Type> interestingAnnotations() {
        return t->true;
    }

    public Set<ClassData> getClasses() {
        return classes;
    }

    public Set<AnnotationData> getAnnotations() {
        return annotations;
    }

    public void addLanguageLoader(final Map<String,? extends IModLanguageProvider.IModLanguageLoader> modTargetMap)
    {
        modTargets.putAll(modTargetMap);
    }

    public void addModFileInfo(IModFileInfo info) {
        this.modFiles.add(info);
    }

    public Map<String, ? extends IModLanguageProvider.IModLanguageLoader> getTargets()
    {
        return modTargets;
    }

    public List<IModFileInfo> getIModInfoData() {
        return this.modFiles;
    }

    public record ClassData(Type clazz, Type parent, Set<Type> interfaces) {}

    public record AnnotationData(Type annotationType, ElementType targetType, Type clazz, String memberName, Map<String, Object> annotationData) {}
}
