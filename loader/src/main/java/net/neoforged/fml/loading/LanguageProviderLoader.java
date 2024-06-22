/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.util.ServiceLoaderUtil;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;

public class LanguageProviderLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final List<IModLanguageLoader> languageProviders;
    private final Map<String, ModLanguageWrapper> languageProviderMap = new HashMap<>();

    public void forEach(final Consumer<IModLanguageLoader> consumer) {
        languageProviders.forEach(consumer);
    }

    public <T> Stream<T> applyForEach(final Function<IModLanguageLoader, T> function) {
        return languageProviders.stream().map(function);
    }

    private record ModLanguageWrapper(IModLanguageLoader modLanguageProvider, ArtifactVersion version) {}

    LanguageProviderLoader(ILaunchContext launchContext) {
        languageProviders = ServiceLoaderUtil.loadServices(launchContext, IModLanguageLoader.class);
        ImmediateWindowHandler.updateProgress("Loading language providers");
        languageProviders.forEach(lp -> {
            String version = lp.version();
            if (version == null || version.isBlank()) {
                LOGGER.error(LogMarkers.CORE, "Found unversioned language provider {}", lp.name());
                throw new RuntimeException("Failed to find implementation version for language provider " + lp.name());
            }
            LOGGER.debug(LogMarkers.CORE, "Found language provider {}, version {}", lp.name(), version);
            ImmediateWindowHandler.updateProgress("Loaded language provider " + lp.name() + " " + version);
            languageProviderMap.put(lp.name(), new ModLanguageWrapper(lp, new DefaultArtifactVersion(version)));
        });
    }

    public IModLanguageLoader findLanguage(ModFile mf, String modLoader, VersionRange modLoaderVersion) {
        final String languageFileName = mf.getFileName();
        final ModLanguageWrapper mlw = languageProviderMap.get(modLoader);
        if (mlw == null) {
            LOGGER.error(LogMarkers.LOADING, "Missing language {} version {} wanted by {}", modLoader, modLoaderVersion, languageFileName);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.language.missingversion", modLoader, modLoaderVersion, "-").withAffectedModFile(mf));
        }
        if (!VersionSupportMatrix.testVersionSupportMatrix(modLoaderVersion, modLoader, "languageloader", (llid, range) -> range.containsVersion(mlw.version()))) {
            LOGGER.error(LogMarkers.LOADING, "Missing language {} version {} wanted by {}, found {}", modLoader, modLoaderVersion, languageFileName, mlw.version());
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.language.missingversion", modLoader, modLoaderVersion, mlw.version()).withAffectedModFile(mf));
        }

        return mlw.modLanguageProvider();
    }
}
