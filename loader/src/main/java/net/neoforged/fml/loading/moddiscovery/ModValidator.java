/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import net.neoforged.fml.loading.*;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModValidator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<IModFile.Type, List<IModFile>> modFiles;
    private final List<IModFile> candidatePlugins;
    private final List<IModFile> candidateMods;
    private LoadingModList loadingModList;
    private List<IModFile> brokenFiles;
    private final List<EarlyLoadingException.ExceptionData> discoveryErrorData;

    public ModValidator(final Map<IModFile.Type, List<IModFile>> modFiles, final List<IModFileInfo> brokenFiles, final List<EarlyLoadingException.ExceptionData> discoveryErrorData) {
        this.modFiles = modFiles;
        this.candidateMods = lst(modFiles.get(IModFile.Type.MOD));
        this.candidateMods.addAll(lst(modFiles.get(IModFile.Type.GAMELIBRARY)));
        this.candidatePlugins = lst(modFiles.get(IModFile.Type.LANGPROVIDER));
        this.candidatePlugins.addAll(lst(modFiles.get(IModFile.Type.LIBRARY)));
        this.discoveryErrorData = discoveryErrorData;
        this.brokenFiles = brokenFiles.stream().map(IModFileInfo::getFile).collect(Collectors.toList()); // mutable list
    }

    private static List<IModFile> lst(List<IModFile> files) {
        return files == null ? new ArrayList<>() : new ArrayList<>(files);
    }

    public void stage1Validation() {
        brokenFiles.addAll(validateAndIdentify(candidateMods));
        if (LOGGER.isDebugEnabled(LogMarkers.SCAN)) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} mod files with {} mods", candidateMods.size(), candidateMods.stream().mapToInt(mf -> mf.getModInfos().size()).sum());
        }
        ImmediateWindowHandler.updateProgress("Found "+candidateMods.size()+" mod candidates");
    }

    @NotNull
    private List<IModFile> validateAndIdentify(final List<IModFile> mods) {
        final List<IModFile> brokenFiles = new ArrayList<>();
        for (Iterator<IModFile> iterator = mods.iterator(); iterator.hasNext(); )
        {
            IModFile modFile = iterator.next();
            if (modFile.getType() != IModFile.Type.MOD) continue;

            if (!modFile.getProvider().isValid(modFile)) {
                LOGGER.warn(LogMarkers.SCAN, "File {} has been ignored - it is invalid", modFile.getFilePath());
                iterator.remove();
                brokenFiles.add(modFile);
            } else {
                try {
                    modFile.getController().identify();
                } catch (Exception exception) {
                    LOGGER.error(LogMarkers.SCAN, "File {} was not identified:", modFile.getFilePath(), exception);
                    iterator.remove();
                    brokenFiles.add(modFile);
                }
            }
        }
        return brokenFiles;
    }

    public ITransformationService.Resource getPluginResources() {
        return new ITransformationService.Resource(IModuleLayerManager.Layer.PLUGIN, this.candidatePlugins.stream().map(IModFile::getSecureJar).toList());
    }

    public ITransformationService.Resource getModResources() {
        var modFilesToLoad = Stream.concat(
                // mods
                this.loadingModList.getModFiles().stream().map(IModFileInfo::getFile),
                // game libraries
                this.modFiles.get(IModFile.Type.GAMELIBRARY).stream());
        return new ITransformationService.Resource(IModuleLayerManager.Layer.GAME, modFilesToLoad.map(IModFile::getSecureJar).toList());
    }

    private List<EarlyLoadingException.ExceptionData> validateLanguages() {
        List<EarlyLoadingException.ExceptionData> errorData = new ArrayList<>();
        for (Iterator<IModFile> iterator = this.candidateMods.iterator(); iterator.hasNext(); ) {
            final IModFile modFile = iterator.next();
            if (modFile.getType() == IModFile.Type.MOD) {
                try {
                    modFile.getController().setLoaders(modFile.getModFileInfo().requiredLanguageLoaders().stream()
                            .map(spec-> FMLLoader.getLanguageLoadingProvider().findLanguage(modFile, spec.languageName(), spec.acceptedVersions()))
                            .toList());
                } catch (EarlyLoadingException e) {
                    errorData.addAll(e.getAllData());
                    iterator.remove();
                }
            }
        }
        return errorData;
    }

    public BackgroundScanHandler stage2Validation() {
        var errors = validateLanguages();

        var allErrors = new ArrayList<>(errors);
        allErrors.addAll(this.discoveryErrorData);

        loadingModList = ModSorter.sort(candidateMods, allErrors);
        loadingModList.addCoreMods();
        loadingModList.addAccessTransformers();
        loadingModList.addMixinConfigs();
        loadingModList.setBrokenFiles(brokenFiles);
        BackgroundScanHandler backgroundScanHandler = new BackgroundScanHandler();
        loadingModList.addForScanning(backgroundScanHandler);
        return backgroundScanHandler;
    }
}
