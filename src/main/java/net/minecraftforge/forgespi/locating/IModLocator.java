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

import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.jar.Manifest;

/**
 * Loaded as a ServiceLoader. Takes mechanisms for locating candidate "mods"
 * and transforms them into {@link ModFile} objects.
 */
public interface IModLocator {
    List<IModFile> scanMods();

    String name();

    Path findPath(IModFile modFile, String... path);

    void scanFile(final IModFile modFile, Consumer<Path> pathConsumer);

    Optional<Manifest> findManifest(Path file);

    default Pair<Optional<Manifest>, Optional<CodeSigner[]>> findManifestAndSigners(Path file) {
        return Pair.of(findManifest(file), Optional.empty());
    }

    void initArguments(Map<String, ?> arguments);

    boolean isValid(IModFile modFile);
}
