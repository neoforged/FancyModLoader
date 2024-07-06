/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.LambdaExceptionUtils;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Master list of all mods <em>in the loading context. This class cannot refer outside the
 * loading package</em>
 */
public class LoadingModList {
    private final List<IModFileInfo> plugins;
    private final List<ModFileInfo> modFiles;
    private final List<ModInfo> sortedList;
    private final Map<ModInfo, List<ModInfo>> modDependencies;
    private final Map<String, ModFileInfo> fileById;
    private final List<ModLoadingIssue> modLoadingIssues;

    public LoadingModList(List<ModFile> plugins, List<ModFile> modFiles, List<ModInfo> sortedList, Map<ModInfo, List<ModInfo>> modDependencies, List<ModLoadingIssue> issues) {
        this.plugins = plugins.stream()
                .map(ModFile::getModFileInfo)
                .collect(Collectors.toList());
        this.modFiles = modFiles.stream()
                .map(ModFile::getModFileInfo)
                .map(ModFileInfo.class::cast)
                .collect(Collectors.toList());
        this.sortedList = new ArrayList<>(sortedList);
        this.modDependencies = modDependencies;
        this.fileById = this.modFiles.stream()
                .map(ModFileInfo::getMods)
                .flatMap(Collection::stream)
                .map(ModInfo.class::cast)
                .collect(Collectors.toMap(ModInfo::getModId, ModInfo::getOwningFile));
        this.modLoadingIssues = new ArrayList<>(issues);
    }

    public List<IModFileInfo> getPlugins() {
        return plugins;
    }

    public List<ModFileInfo> getModFiles() {
        return modFiles;
    }

    @Deprecated(forRemoval = true)
    public static LoadingModList get() {
        return FMLLoader.getLoadingModList();
    }

    @Deprecated(forRemoval = true)
    public Path findResource(final String className) {
        for (ModFileInfo mf : modFiles) {
            final Path resource = mf.getFile().findResource(className);
            if (Files.exists(resource)) return resource;
        }
        return null;
    }

    @Deprecated(forRemoval = true)
    public Enumeration<URL> findAllURLsForResource(final String resName) {
        final String resourceName;
        // strip a leading slash
        if (resName.startsWith("/")) {
            resourceName = resName.substring(1);
        } else {
            resourceName = resName;
        }
        return new Enumeration<URL>() {
            private final Iterator<ModFileInfo> modFileIterator = modFiles.iterator();
            private URL next;

            @Override
            public boolean hasMoreElements() {
                if (next != null) return true;
                next = findNextURL();
                return next != null;
            }

            @Override
            public URL nextElement() {
                if (next == null) {
                    next = findNextURL();
                    if (next == null) throw new NoSuchElementException();
                }
                URL result = next;
                next = null;
                return result;
            }

            private URL findNextURL() {
                while (modFileIterator.hasNext()) {
                    final ModFileInfo next = modFileIterator.next();
                    final Path resource = next.getFile().findResource(resourceName);
                    if (Files.exists(resource)) {
                        return LambdaExceptionUtils.uncheck(() -> new URL("modjar://" + next.getMods().get(0).getModId() + "/" + resourceName));
                    }
                }
                return null;
            }
        };
    }

    public ModFileInfo getModFileById(String modid) {
        return this.fileById.get(modid);
    }

    public List<ModInfo> getMods() {
        return this.sortedList;
    }

    /**
     * Returns all direct loading dependencies of the given mod.
     *
     * <p>This means: all the mods that are directly specified to be loaded before the given mod,
     * either because the given mod has an {@link IModInfo.Ordering#AFTER} constraint on the dependency,
     * or because the dependency has a {@link IModInfo.Ordering#BEFORE} constraint on the given mod.
     */
    public List<ModInfo> getDependencies(IModInfo mod) {
        var dependencies = this.modDependencies.get(mod);
        if (dependencies == null) {
            throw new IllegalArgumentException("The given mod info is not part of the loading mod list: " + mod);
        }
        return dependencies;
    }

    public boolean hasErrors() {
        return !modLoadingIssues.isEmpty() && modLoadingIssues.stream().anyMatch(issue -> issue.severity() == ModLoadingIssue.Severity.ERROR);
    }

    public List<ModLoadingIssue> getModLoadingIssues() {
        return modLoadingIssues;
    }
}
