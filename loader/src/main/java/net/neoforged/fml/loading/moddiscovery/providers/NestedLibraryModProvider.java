/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.locators.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.LoadResult;
import net.neoforged.neoforgespi.locating.ModFileLoadingException;
import org.jetbrains.annotations.Nullable;

/**
 * Provides simple libraries that are embedded in other mods.
 */
public class NestedLibraryModProvider implements IModFileReader {
    @Override
    public String name() {
        return "library mod provider";
    }

    @Override
    public @Nullable LoadResult<IModFile> read(JarContents jar, @Nullable IModFile parent) {
        // We only consider jars that are contained in the context of another mod valid library targets,
        // since we assume those have been included deliberately. Loose jar files in the mods directory
        // are not considered, since those are likely to have been dropped in by accident.
        if (parent != null) {
            try {
                var modFile = IModFile.create(SecureJar.from(jar), this, JarModsDotTomlModFileReader::manifestParser, IModFile.Type.LIBRARY, parent);
                return new LoadResult.Success<>(modFile);
            } catch (ModFileLoadingException exception) {
                return new LoadResult.Error<>(ModLoadingIssue.error("TODO", exception.getMessage()).withCause(exception));
            } catch (Exception exception) {
                return new LoadResult.Error<>(ModLoadingIssue.error(
                        // TODO
                        "TECHNICAL ERROR WHILE READING ...", jar, parent).withCause(exception));
            }
        }

        return null;
    }
}
