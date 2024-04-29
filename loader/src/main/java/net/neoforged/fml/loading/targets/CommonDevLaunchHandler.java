/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.locators.NeoForgeDevProvider;
import net.neoforged.fml.loading.moddiscovery.locators.UserdevLocator;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;

/**
 * For the NeoForge development environment.
 */
public abstract class CommonDevLaunchHandler extends CommonLaunchHandler {
    @Override
    public boolean isProduction() {
        return false;
    }

    @Override
    public void collectAdditionalModFileLocators(VersionInfo versionInfo, Consumer<IModFileCandidateLocator> output) {
        super.collectAdditionalModFileLocators(versionInfo, output);

        var groupedModFolders = getGroupedModFolders();
        var minecraftFolders = groupedModFolders.get("minecraft");
        if (minecraftFolders == null) {
            throw new IllegalStateException("Expected paths to minecraft classes to be passed via environment");
        }

        output.accept(new NeoForgeDevProvider(minecraftFolders));
        output.accept(new UserdevLocator(groupedModFolders));
    }

    @Override
    protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
        super.preLaunch(arguments, layer);

        if (getDist().isDedicatedServer())
            return arguments;

        if (isData())
            return arguments;

        var args = ArgumentList.from(arguments);

        String username = args.get("username");
        if (username != null) { // Replace '#' placeholders with random numbers
            Matcher m = Pattern.compile("#+").matcher(username);
            StringBuffer replaced = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(replaced, getRandomNumbers(m.group().length()));
            }
            m.appendTail(replaced);
            args.put("username", replaced.toString());
        } else {
            args.putLazy("username", "Dev");
        }

        if (!args.hasValue("accessToken")) {
            args.put("accessToken", "0");
        }

        return args.getArguments();
    }

    private static String getRandomNumbers(int length) {
        // Generate a time-based random number, to mimic how n.m.client.Main works
        return Long.toString(System.nanoTime() % (int) Math.pow(10, length));
    }
}
