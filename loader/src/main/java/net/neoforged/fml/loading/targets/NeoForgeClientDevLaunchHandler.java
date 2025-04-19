/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.targets;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.api.distmarker.Dist;

public class NeoForgeClientDevLaunchHandler extends NeoForgeDevLaunchHandler {
    @Override
    public String name() {
        return "neoforgeclientdev";
    }

    @Override
    public Dist getDist() {
        return Dist.CLIENT;
    }

    @Override
    protected String[] preLaunch(String[] arguments, ModuleLayer layer) {
        var args = ArgumentList.from(arguments);

        String username = args.get("username");
        if (username != null) { // Replace '#' placeholders with random numbers
            Matcher m = Pattern.compile("#+").matcher(username);
            var replaced = new StringBuilder();
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

        return super.preLaunch(args.getArguments(), layer);
    }

    @Override
    public void runService(String[] arguments, ModuleLayer layer) throws Throwable {
        clientService(arguments, layer);
    }

    private static String getRandomNumbers(int length) {
        // Generate a time-based random number, to mimic how n.m.client.Main works
        return Long.toString(System.nanoTime() % (int) Math.pow(10, length));
    }
}
