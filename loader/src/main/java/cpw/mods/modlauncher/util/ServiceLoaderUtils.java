/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2021 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher.util;

import cpw.mods.niofs.union.UnionFileSystem;
import java.nio.file.Path;

public final class ServiceLoaderUtils {
    public static String fileNameFor(Class<?> clazz) {
        // Used in test scenarios where services might come from normal CP
        if (clazz.getModule().getLayer() == null) {
            return clazz.getProtectionDomain().getCodeSource().getLocation().getFile();
        }

        return clazz.getModule().getLayer().configuration()
                .findModule(clazz.getModule().getName())
                .flatMap(rm -> rm.reference().location())
                .map(Path::of)
                .map(p -> p.getFileSystem() instanceof UnionFileSystem ufs ? ufs.getPrimaryPath() : p)
                .map(p -> p.getFileName().toString())
                .orElse("MISSING FILE");
    }
}
