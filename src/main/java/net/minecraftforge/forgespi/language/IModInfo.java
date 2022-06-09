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

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IModInfo
{
    VersionRange UNBOUNDED = MavenVersionAdapter.createFromVersionSpec("");

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

    interface ModVersion {
        String getModId();

        VersionRange getVersionRange();

        boolean isMandatory();

        Ordering getOrdering();

        DependencySide getSide();

        void setOwner(IModInfo owner);

        IModInfo getOwner();

        Optional<URL> getReferralURL();
    }
}
