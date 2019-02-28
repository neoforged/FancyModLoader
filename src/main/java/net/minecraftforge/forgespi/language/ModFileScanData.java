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


import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.util.*;
import java.util.function.Predicate;

public class ModFileScanData
{
    private final Set<AnnotationData> annotations = new LinkedHashSet<>();
    private final Set<ClassData> classes = new LinkedHashSet<>();
    private Map<String,? extends IModLanguageProvider.IModLanguageLoader> modTargets;
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

    public void addLanguageLoader(Map<String,? extends IModLanguageProvider.IModLanguageLoader> modTargetMap)
    {
        modTargets = modTargetMap;
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

    public static class ClassData {
        private final Type clazz;
        private final Type parent;
        private final Set<Type> interfaces;

        public ClassData(final Type clazz, final Type parent, final Set<Type> interfaces) {
            this.clazz = clazz;
            this.parent = parent;
            this.interfaces = interfaces;
        }

        @Override
        public boolean equals(final Object obj) {
            try {
                return (!Objects.isNull(obj)) && Objects.equals(clazz, ((ClassData)obj).clazz);
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.clazz);
        }
    }
    public static class AnnotationData {
        private final Type annotationType;
        private final ElementType targetType;
        private final Type clazz;
        private final String memberName;
        private final Map<String,Object> annotationData;


        public AnnotationData(final Type annotationType, final ElementType targetType, final Type clazz, final String memberName, final Map<String, Object> annotationData) {
            this.annotationType = annotationType;
            this.targetType = targetType;
            this.clazz = clazz;
            this.memberName = memberName;
            this.annotationData = annotationData;
        }

        public Type getAnnotationType() {
            return annotationType;
        }

        public ElementType getTargetType() {
            return targetType;
        }

        public Type getClassType() {
            return clazz;
        }

        public String getMemberName() {
            return memberName;
        }

        public Map<String, Object> getAnnotationData() {
            return annotationData;
        }

        @Override
        public boolean equals(final Object obj) {
            try {
                AnnotationData dat = (AnnotationData) obj;
                return (!Objects.isNull(dat)) &&
                        Objects.equals(annotationType, dat.annotationType) &&
                        Objects.equals(targetType, dat.targetType) &&
                        Objects.equals(clazz, dat.clazz) &&
                        Objects.equals(memberName, dat.memberName);
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotationType, targetType, clazz, memberName);
        }
    }
}
