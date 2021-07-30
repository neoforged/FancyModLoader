/*
 * Minecraft Forge
 * Copyright (c) 2016-2019.
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

package net.minecraftforge.forgespi.locating;

import java.util.List;

/**
 * Loaded as a ServiceLoader. Takes mechanisms for locating candidate "mods"
 * and transforms them into {@link IModFile} objects.
 */
public interface IModLocator extends IModProvider
{
    /**
     * Invoked to find all mods that this mod locator can find.
     * It is not guaranteed that all these are loaded into the runtime,
     * as such the result of this method should be seen as a list of candidates to load.
     *
     * @return All found, or discovered, mod files.
     */
    List<IModFile> scanMods();
}
