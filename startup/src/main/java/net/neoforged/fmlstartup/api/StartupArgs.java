/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup.api;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is dual-loaded both in the boot classpath and in the module-layer we use to start the game.
 * To allow the data to be comfortably constructed in Startup before it is passed over to FML,
 * we serialize and deserialize it in-memory to get it across the class-loader boundary.
 */
public record StartupArgs(
        File gameDirectory,
        String launchTarget,
        String[] programArgs,
        List<DiscoveredFile> files,
        List<File> directories
) {

    public Object[] parcel() {
        Object[][] parceledFiles = new Object[files.size()][];
        for (int i = 0; i < files.size(); i++) {
            parceledFiles[i] = files.get(i).parcel();
        }

        return new Object[]{
                gameDirectory,
                launchTarget,
                programArgs,
                parceledFiles,
                directories
        };
    }

    @SuppressWarnings("unchecked")
    public static StartupArgs unparcel(Object[] parcel) {
        var parcelledFiles = (Object[][]) parcel[3];
        List<DiscoveredFile> files = new ArrayList<>(parcelledFiles.length);
        for (Object[] parcelledFile : parcelledFiles) {
            files.add(DiscoveredFile.unparcel(parcelledFile));
        }

        return new StartupArgs(
                (File) parcel[0],
                (String) parcel[1],
                (String[]) parcel[2],
                files,
                (List<File>) parcel[4]
        );
    }

}
