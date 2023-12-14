/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.MavenVersionAdapter;
import org.apache.maven.artifact.versioning.VersionRange;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ModVersionOverrides {
    public static Map<String, IConfigurable> getOverrides(IModInfo modInfo) {
        List<IConfigurable> overrides = FMLConfig.<String, List<IConfigurable>>
                getMapConfigValue(FMLConfig.ConfigValue.DEPENDENCY_VERSION_OVERRIDES).get(modInfo.getModId());

        if (overrides == null || overrides.isEmpty()) {
            return Collections.emptyMap();
        }

        return overrides.stream().collect(Collectors.toMap(config -> config.<String>getConfigElement("modId")
                .orElseThrow(() -> new InvalidModFileException("Missing required field modid in dependency override", modInfo.getOwningFile())), iConfigurable -> iConfigurable));
    }

    public static IModInfo.ModVersion applyOverrides(IModInfo.ModVersion version, Map<String, IConfigurable> overrides) {
        IConfigurable override = overrides.get(version.getModId());

        if (override == null) {
            return version;
        }

        return new OverridedModVersion(version, override);
    }

    public static class OverridedModVersion implements IModInfo.ModVersion {
        private final IModInfo.ModVersion modVersion;
        private final VersionRange versionRange;
        private final boolean mandatory;
        private final IModInfo.Ordering ordering;
        private final IModInfo.DependencySide side;

        public OverridedModVersion(IModInfo.ModVersion modVersion, IConfigurable config) {
            this.modVersion = modVersion;

            this.versionRange = config.<String>getConfigElement("versionRange")
                    .map(MavenVersionAdapter::createFromVersionSpec)
                    .orElse(modVersion.getVersionRange());

            this.mandatory = config.<Boolean>getConfigElement("mandatory")
                    .orElse(modVersion.isMandatory());

            this.ordering = config.<String>getConfigElement("ordering")
                    .map(IModInfo.Ordering::valueOf)
                    .orElse(modVersion.getOrdering());

            this.side = config.<String>getConfigElement("side")
                    .map(IModInfo.DependencySide::valueOf)
                    .orElse(modVersion.getSide());
        }

        @Override
        public String getModId() {
            return modVersion.getModId();
        }

        @Override
        public VersionRange getVersionRange() {
            return this.versionRange;
        }

        @Override
        public boolean isMandatory() {
            return this.mandatory;
        }

        @Override
        public IModInfo.Ordering getOrdering() {
            return this.ordering;
        }

        @Override
        public IModInfo.DependencySide getSide() {
            return this.side;
        }

        @Override
        public void setOwner(IModInfo owner) {
            modVersion.setOwner(owner);
        }

        @Override
        public IModInfo getOwner() {
            return modVersion.getOwner();
        }

        @Override
        public Optional<URL> getReferralURL() {
            return modVersion.getReferralURL();
        }
    }
}
