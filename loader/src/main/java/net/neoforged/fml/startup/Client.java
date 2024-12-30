/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.ProgramArgs;
import net.neoforged.fml.loading.FMLLoader;

/**
 * The entrypoint for starting a modded Minecraft client.
 */
public class Client extends Entrypoint {
    private Client() {}

    public static void main(String[] args) {
        try (var startup = startup(args, false, Dist.CLIENT)) {
            if (!FMLLoader.isProduction()) {
                preProcessDevArguments(startup.programArgs());
            }

            var main = createMainMethodCallable(startup.classLoader(), "net.minecraft.client.main.Main");
            main.invokeExact(startup.programArgs().getArguments());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }

    /**
     * In development, we support the following:
     * - Auto-add a missing access token
     * - Replace "#" in usernames with random numbers
     * - Default the username to "Dev" if none is given
     */
    private static void preProcessDevArguments(ProgramArgs args) {
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
    }

    private static String getRandomNumbers(int length) {
        // Generate a time-based random number, to mimic how n.m.client.Main works
        return Long.toString(System.nanoTime() % (int) Math.pow(10, length));
    }
}
