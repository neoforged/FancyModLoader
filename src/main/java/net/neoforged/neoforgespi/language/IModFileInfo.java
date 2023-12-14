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

import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.List;
import java.util.Map;

public interface IModFileInfo
{
    List<IModInfo> getMods();

    record LanguageSpec(String languageName, VersionRange acceptedVersions) {}

    List<LanguageSpec> requiredLanguageLoaders();

    boolean showAsResourcePack();

    boolean showAsDataPack();

    Map<String,Object> getFileProperties();

    String getLicense();

    String moduleName();

    String versionString();

    List<String> usesServices();

    IModFile getFile();

    IConfigurable getConfig();
}
