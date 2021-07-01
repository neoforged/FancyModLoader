/*
 * Minecraft Forge
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.forgespi.language;


import java.lang.annotation.ElementType;
import java.util.*;
import java.util.function.Predicate;

import org.objectweb.asm.Type;

public class ModFileScanData
{
    private final Set<AnnotationData> annotations = new LinkedHashSet<>();
    private final Set<ClassData> classes = new LinkedHashSet<>();
    private final Map<String,IModLanguageProvider.IModLanguageLoader> modTargets = new HashMap<>();
    private Map<String,?> functionalScanners;
    private List<IModFileInfo> modFiles = new ArrayList<>();

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
