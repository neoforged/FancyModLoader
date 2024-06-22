/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.ModSorter;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ModValidator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<IModFile.Type, List<ModFile>> modFiles;
    private final List<ModFile> candidatePlugins;
    private final List<ModFile> candidateMods;
    private final List<ModLoadingIssue> issues;
    private LoadingModList loadingModList;

    public ModValidator(Map<IModFile.Type, List<ModFile>> modFiles, List<ModLoadingIssue> issues) {
        this.modFiles = modFiles;
        this.candidateMods = lst(modFiles.get(IModFile.Type.MOD));
        this.candidateMods.addAll(lst(modFiles.get(IModFile.Type.GAMELIBRARY)));
        this.candidatePlugins = lst(modFiles.get(IModFile.Type.LIBRARY));
        this.issues = issues;
    }

    private static List<ModFile> lst(@Nullable List<ModFile> files) {
        return files == null ? new ArrayList<>() : new ArrayList<>(files);
    }

    public void stage1Validation() {
        validateFiles(candidateMods);
        if (LOGGER.isDebugEnabled(LogMarkers.SCAN)) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} mod files with {} mods", candidateMods.size(), candidateMods.stream().mapToInt(mf -> mf.getModInfos().size()).sum());
        }
        ImmediateWindowHandler.updateProgress("Found " + candidateMods.size() + " mod candidates");
    }

    private void validateFiles(final List<ModFile> mods) {
        for (Iterator<ModFile> iterator = mods.iterator(); iterator.hasNext();) {
            var modFile = iterator.next();
            if (!modFile.identifyMods()) {
                LOGGER.warn(LogMarkers.SCAN, "File {} has been ignored - it is invalid", modFile.getFilePath());
                iterator.remove();
            }
        }
    }

    public ITransformationService.Resource getPluginResources() {
        return new ITransformationService.Resource(IModuleLayerManager.Layer.PLUGIN, this.candidatePlugins.stream().map(IModFile::getSecureJar).toList());
    }

    public ITransformationService.Resource getModResources() {
        var modFilesToLoad = Stream.concat(
                // mods
                this.loadingModList.getModFiles().stream().map(ModFileInfo::getFile),
                // game libraries
                lst(this.modFiles.get(IModFile.Type.GAMELIBRARY)).stream());
        return new ITransformationService.Resource(IModuleLayerManager.Layer.GAME, modFilesToLoad.map(ModFile::getSecureJar).toList());
    }

    private void validateLanguages() {
        for (Iterator<ModFile> iterator = this.candidateMods.iterator(); iterator.hasNext();) {
            var modFile = iterator.next();
            try {
                modFile.identifyLanguage();
            } catch (ModLoadingException e) {
                issues.addAll(e.getIssues());
                iterator.remove();
            } catch (Exception e) {
                issues.add(ModLoadingIssue.error("fml.modloadingissue.technical_error").withAffectedModFile(modFile).withCause(e));
                iterator.remove();
            }
        }
    }

    public BackgroundScanHandler stage2Validation() {
        validateLanguages();

        loadingModList = ModSorter.sort(candidatePlugins, candidateMods, issues);
        loadingModList.addAccessTransformers();
        loadingModList.addMixinConfigs();
        loadingModList.addEnumExtenders();
        var backgroundScanHandler = new BackgroundScanHandler();
        loadingModList.addForScanning(backgroundScanHandler);
        return backgroundScanHandler;
    }
}
