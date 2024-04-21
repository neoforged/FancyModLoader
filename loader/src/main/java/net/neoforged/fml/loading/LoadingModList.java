/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import cpw.mods.modlauncher.api.LambdaExceptionUtils;
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
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.mixin.DeferredMixinConfigRegistration;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;

/**
 * Master list of all mods <em>in the loading context. This class cannot refer outside the
 * loading package</em>
 */
public class LoadingModList {
    private static LoadingModList INSTANCE;
    private final List<ModFileInfo> modFiles;
    private final List<ModInfo> sortedList;
    private final Map<String, ModFileInfo> fileById;
    private final List<ModLoadingIssue> modLoadingIssues;

    private LoadingModList(final List<ModFile> modFiles, final List<ModInfo> sortedList) {
        this.modFiles = modFiles.stream()
                .map(ModFile::getModFileInfo)
                .map(ModFileInfo.class::cast)
                .collect(Collectors.toList());
        this.sortedList = sortedList.stream()
                .map(ModInfo.class::cast)
                .collect(Collectors.toList());
        this.fileById = this.modFiles.stream()
                .map(ModFileInfo::getMods)
                .flatMap(Collection::stream)
                .map(ModInfo.class::cast)
                .collect(Collectors.toMap(ModInfo::getModId, ModInfo::getOwningFile));
        this.modLoadingIssues = new ArrayList<>();
    }

    public static LoadingModList of(List<ModFile> modFiles, List<ModInfo> sortedList, List<ModLoadingIssue> issues) {
        INSTANCE = new LoadingModList(modFiles, sortedList);
        INSTANCE.modLoadingIssues.addAll(issues);
        return INSTANCE;
    }

    public static LoadingModList get() {
        return INSTANCE;
    }

    public void addCoreMods() {
        modFiles.stream()
                .map(ModFileInfo::getFile)
                .map(ModFile::getCoreMods)
                .flatMap(List::stream)
                .forEach(FMLLoader.getCoreModEngine()::loadCoreMod);
    }

    public void addMixinConfigs() {
        modFiles.stream()
                .map(ModFileInfo::getFile)
                .forEach(file -> {
                    final String modId = file.getModInfos().get(0).getModId();
                    file.getMixinConfigs().forEach(cfg -> DeferredMixinConfigRegistration.addMixinConfig(cfg, modId));
                });
    }

    public void addAccessTransformers() {
        modFiles.stream()
                .map(ModFileInfo::getFile)
                .forEach(mod -> mod.getAccessTransformers().forEach(path -> FMLLoader.addAccessTransformer(path, mod)));
    }

    public void addForScanning(BackgroundScanHandler backgroundScanHandler) {
        backgroundScanHandler.setLoadingModList(this);
        modFiles.stream()
                .map(ModFileInfo::getFile)
                .forEach(backgroundScanHandler::submitForScanning);
    }

    public List<ModFileInfo> getModFiles() {
        return modFiles;
    }

    public Path findResource(final String className) {
        for (ModFileInfo mf : modFiles) {
            final Path resource = mf.getFile().findResource(className);
            if (Files.exists(resource)) return resource;
        }
        return null;
    }

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

    public boolean hasErrors() {
        return modLoadingIssues.stream().noneMatch(issue -> issue.severity() == ModLoadingIssue.Severity.ERROR);
    }

    public List<ModLoadingIssue> getModLoadingIssues() {
        return modLoadingIssues;
    }
}
