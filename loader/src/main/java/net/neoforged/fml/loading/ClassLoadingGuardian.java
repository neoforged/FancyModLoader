/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import org.jetbrains.annotations.Nullable;

public final class ClassLoadingGuardian {
    private final Instrumentation instrumentation;
    private final Set<String> protectedPackages;
    private final ClassFileTransformer guardianTransformer;
    private volatile boolean uninstalled;

    public ClassLoadingGuardian(Instrumentation instrumentation, List<ModFile> gameContent) {
        this.instrumentation = instrumentation;
        this.protectedPackages = getPackages(gameContent);
        this.guardianTransformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                throw new UnsupportedOperationException();
            }

            @Override
            public byte[] transform(Module module,
                    ClassLoader loader,
                    String className,
                    Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain,
                    byte[] classfileBuffer) {
                if (uninstalled) {
                    return null; // This can happen due to multi-threaded class-loading
                }

                var packageName = getPackageName(className);
                if (packageName != null && protectedPackages.contains(packageName)) {
                    throw new IllegalArgumentException();
                }

                return null;
            }
        };
        this.instrumentation.addTransformer(guardianTransformer);
    }

    private static Set<String> getPackages(List<ModFile> gameContent) {
        var protectedPackages = new HashSet<String>(1000);
        for (var modFile : gameContent) {
            for (var pkgName : modFile.getSecureJar().moduleDataProvider().descriptor().packages()) {
                protectedPackages.add(pkgName.replace('.', '/'));
            }
        }
        return protectedPackages;
    }

    public void end() {
        if (!this.uninstalled) {
            this.uninstalled = true;
            this.instrumentation.removeTransformer(guardianTransformer);

            // Final check for class-loading bugs
            for (var loadedClass : instrumentation.getAllLoadedClasses()) {
                if (loadedClass.getClassLoader() == null) {
                    continue; // JDK built-in
                }
                var physicalPackage = loadedClass.getPackageName().replace('.', '/');
                if (protectedPackages.contains(physicalPackage)) {
                    throw new IllegalArgumentException("Class " + loadedClass + " is incorrectly class-loaded!");
                }
            }
        }
    }

    @Nullable
    private static String getPackageName(String className) {
        var lastPkgSepIdx = className.lastIndexOf('/');
        if (lastPkgSepIdx != -1) {
            return className.substring(0, lastPkgSepIdx);
        }
        return null;
    }
}