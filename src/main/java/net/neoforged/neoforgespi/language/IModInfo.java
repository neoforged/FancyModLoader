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

package net.neoforged.neoforgespi.language;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforgespi.Environment;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IModInfo
{
    // " " will just be the preferred version for Maven, but it will accept anything.
    // The space is very important, else we get a range that doesn't accept anything.
    VersionRange UNBOUNDED = MavenVersionAdapter.createFromVersionSpec(" ");

    IModFileInfo getOwningFile();

    String getModId();

    String getDisplayName();

    String getDescription();

    ArtifactVersion getVersion();

    List<? extends ModVersion> getDependencies();

    List<? extends ForgeFeature.Bound> getForgeFeatures();

    String getNamespace();

    Map<String,Object> getModProperties();

    Optional<URL> getUpdateURL();

    Optional<URL> getModURL();

    Optional<String> getLogoFile();

    boolean getLogoBlur();

    IConfigurable getConfig();

    enum Ordering {
        BEFORE, AFTER, NONE
    }

    enum DependencySide {
        CLIENT(Dist.CLIENT), SERVER(Dist.DEDICATED_SERVER), BOTH(Dist.values());

        private final Dist[] dist;

        DependencySide(final Dist... dist) {
            this.dist = dist;
        }

        public boolean isContained(Dist side) {
            return this == BOTH || dist[0] == side;
        }
        public boolean isCorrectSide()
        {
            return this == BOTH || Environment.get().getDist().equals(this.dist[0]);
        }
    }

    enum DependencyType {
        REQUIRED, OPTIONAL,
        /**
         * Prevents the game from loading if the dependency is loaded.
         */
        INCOMPATIBLE,
        /**
         * Shows a warning if the dependency is loaded.
         */
        DISCOURAGED
    }

    interface ModVersion {
        String getModId();

        VersionRange getVersionRange();

        DependencyType getType();

        /**
         * {@return the reason of this dependency}
         * Only displayed if the type is either {@link DependencyType#DISCOURAGED} or {@link DependencyType#INCOMPATIBLE}
         */
        Optional<String> getReason();

        Ordering getOrdering();

        DependencySide getSide();

        void setOwner(IModInfo owner);

        IModInfo getOwner();

        Optional<URL> getReferralURL();
    }
}
