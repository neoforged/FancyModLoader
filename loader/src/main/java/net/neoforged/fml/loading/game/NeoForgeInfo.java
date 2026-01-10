/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.game;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.util.ClasspathResourceUtils;
import net.neoforged.fml.util.PathPrettyPrinting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Information about the NeoForge version we're loading.
 * <p>The content is sourced from a properties file in the NeoForge jar, which is generated during the NF build.
 */
record NeoForgeInfo(
        Path sourcePath,
        String neoForgeVersion,
        String neoFormVersion,
        String minecraftVersion,
        String buildType,
        String patchBundleLocation) {

    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeInfo.class);

    private static final String PROPERTIES_LOCATION = "net/neoforged/neoforge/common/version.properties";
    private static NeoForgeInfo read(Path sourcePath, InputStream input) throws IOException {
        var p = new Properties();
        p.load(new BufferedInputStream(input));

        return new NeoForgeInfo(
                sourcePath,
                getRequiredProperty(p, "neoforge_version"),
                getRequiredProperty(p, "neoform_version"),
                getRequiredProperty(p, "minecraft_version"),
                getRequiredProperty(p, "build_type"),
                getRequiredProperty(p, "autoinstall_patches"));
    }

    private static String getRequiredProperty(Properties p, String name) throws IOException {
        var value = p.getProperty(name);
        if (value == null) {
            throw new IOException("NeoForge version properties are missing required key '" + name + "'");
        }
        return value;
    }

    /**
     * @throws ModLoadingException if not found or corrupted
     */
    public static NeoForgeInfo fromClasspath(ClassLoader classLoader) {
        var versionInfoUrl = classLoader.getResource(PROPERTIES_LOCATION);
        if (versionInfoUrl == null) {
            LOG.error("Could not find {} on the classpath.", PROPERTIES_LOCATION);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.missing_neoforge_jar"));
        }

        var sourcePath = ClasspathResourceUtils.getRootFromResourceUrl(PROPERTIES_LOCATION, versionInfoUrl);
        try (var input = versionInfoUrl.openStream()) {
            return read(sourcePath, input);
        } catch (IOException e) {
            LOG.error("Could not load the NeoForge version from the version properties file in {}.", PathPrettyPrinting.prettyPrint(sourcePath), e);
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.corrupted_neoforge_jar").withAffectedPath(sourcePath).withCause(e));
        }
    }
}
