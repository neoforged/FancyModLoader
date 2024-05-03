/*
 * Minecraft Forge
 * Copyright (c) 2016-2018.
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.neoforged.neoforgespi.language;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * Loaded as a ServiceLoader, from the plugin layer.
 * Jars in the mods directory must have an {@code FMLModType} of {@link IModFile.Type#LIBRARY}
 * to be loaded on the plugin layer.
 *
 * <p>Version data is read from the manifest's implementation version.
 */
public interface IModLanguageLoader {
    String name();

    ModContainer loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer);
}
