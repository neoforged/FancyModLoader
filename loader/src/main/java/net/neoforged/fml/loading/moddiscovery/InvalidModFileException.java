/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import java.util.Locale;
import java.util.Optional;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.ModFileLoadingException;

public class InvalidModFileException extends ModFileLoadingException {
    private final IModFileInfo modFileInfo;

    public InvalidModFileException(String message, IModFileInfo modFileInfo) {
        super(String.format(Locale.ROOT, "%s (%s)", message, Optional.ofNullable(modFileInfo).map(mf -> mf.getFile().getFileName()).orElse("MISSING FILE NAME")));
        this.modFileInfo = modFileInfo;
    }

    public IModFileInfo getBrokenFile() {
        return modFileInfo;
    }
}
