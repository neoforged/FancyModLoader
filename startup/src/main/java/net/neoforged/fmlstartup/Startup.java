/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import net.neoforged.fmlstartup.api.StartupArgs;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;

public class Startup {
    public static void main(String[] args) throws IOException {
        StartupLog.info("JVM Uptime: {}ms", ManagementFactory.getRuntimeMXBean().getUptime());

        try {
            args = ArgFileExpander.expandArgFiles(args);

            run(args, Startup::createFmlLoaderEntrypoint);
        } catch (FatalStartupException e) {
            FatalErrorReporting.reportFatalError(e.getMessage());
            System.exit(1);
        }
    }

    private static StartupEntrypoint createFmlLoaderEntrypoint() {
        try {
            var fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            var lookup = MethodHandles.lookup();
            var methodType = MethodType.methodType(void.class, Instrumentation.class, StartupArgs.class);
            var handle = lookup.findStatic(fmlLoader, "startup", methodType);

            var site = LambdaMetafactory.metafactory(
                    lookup,
                    "start",
                    MethodType.methodType(StartupEntrypoint.class),
                    methodType,
                    handle,
                    methodType);

            return (StartupEntrypoint) site.getTarget().invokeExact();
        } catch (ClassNotFoundException e) {
            throw new FatalStartupException("Missing net.neoforged.fml.loading.FMLLoader class on the classpath.");
        } catch (NoSuchMethodException e) {
            throw new FatalStartupException("net.neoforged.fml.loading.FMLLoader is missing method 'startup'.");
        } catch (IllegalAccessException e) {
            throw new FatalStartupException("net.neoforged.fml.loading.FMLLoader or its method startup have wrong access level.");
        } catch (Throwable e) {
            throw new FatalStartupException("Failed to create FML entrypoint object: " + e);
        }
    }

    private static void run(String[] args, Supplier<StartupEntrypoint> entrypoint) throws IOException {
        var gameDir = getGameDir(args);
        StartupLog.info("Game Directory: {}", gameDir);
        var launchTarget = getLaunchTarget(args);
        StartupLog.info("Launch Target: {}", launchTarget);

        var cacheDir = new File(gameDir, ".neoforgecache");
        if (!cacheDir.exists() && !cacheDir.mkdir()) {
            StartupLog.error("Failed to create cache directory: {}", cacheDir);
        }

        var instrumentation = obtainInstrumentation();

        // Disabling JMX for JUnit improves startup time
        if (System.getProperty("log4j2.disable.jmx") == null) {
            System.setProperty("log4j2.disable.jmx", "true");
        }

        // Launch FML
        var previousClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            var startupArgs = new StartupArgs(
                    gameDir,
                    launchTarget,
                    args,
                    new HashSet<>(),
                    listClasspathEntries(),
                    false,
                    Startup.class.getClassLoader()
            );

            entrypoint.get().start(instrumentation, startupArgs);
        } catch (Exception e) {
            var sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            StartupLog.error("Failed to load FML: {}", sw);
            throw new FatalStartupException("Failed to load FML: " + e);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }

        StartupLog.info("After FMLLoader.startup");
    }

    private static List<File> listClasspathEntries() {
        // Find entries on the classpath
        var classPathEntries = System.getProperty("java.class.path").split(File.pathSeparator);
        var result = new ArrayList<File>(classPathEntries.length);
        for (var classPathEntry : classPathEntries) {
            var file = new File(classPathEntry);
            result.add(file);
        }

        return result;
    }

    private static File getGameDir(String[] args) {
        var gameDir = new File(getArg(args, "gameDir", "")).getAbsoluteFile();
        if (!gameDir.isDirectory()) {
            throw new RuntimeException("The game directory passed on the command-line is not a directory: " + gameDir);
        }
        return gameDir;
    }

    private static String getLaunchTarget(String[] args) {
        var target = getArg(args, "launchTarget", "neoforgeclient");
        return switch (target) {
            // TODO: Remove translation in 1.21.4
            case "forgeclient" -> "neoforgeclient";
            default -> target;
        };
    }

    private static String getArg(String[] args, String name, String defaultValue) {
        var argName = "--" + name;
        for (int i = 0; i + 1 < args.length; i++) {
            if (argName.equals(args[i])) {
                return args[i + 1];
            }
        }

        return defaultValue;
    }

    private static Instrumentation obtainInstrumentation() {
        var storedExceptions = new ArrayList<Exception>();

        // Obtain instrumentation as early as possible. We use reflection here since we want to make sure that even if
        // we are loaded through other means, we get the agent class from the system CL.
        try {
            return getFromOurOwnAgent();
        } catch (Exception e) {
            storedExceptions.add(e);
        }

        // Still don't have it? Try self-attach!
        // This most likely will go away in the next Java LTS, but until then, it's convenient for unit tests.
        try {
            var classpathItem = SelfAttach.getClassPathItem();
            var command = ProcessHandle.current().info().command().orElseThrow(() -> new RuntimeException("Could not self-attach: failed to determine our own commandline"));
            var process = new ProcessBuilder(command, "-cp", classpathItem, SelfAttach.class.getName(), DevAgent.class.getName())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .inheritIO()
                    .start();
            process.getOutputStream().close();
            var result = process.waitFor();
            if (result != 0) {
                throw new RuntimeException("Could not self-attach agent: " + result);
            }
            return getFromOurOwnAgent();
        } catch (Exception e) {
            storedExceptions.add(e);
        }

        // If our own self-attach fails due to a user using an unexpected JVM, we support that they add ByteBuddy
        // which has a plethora of self-attach options.
        try {
            var byteBuddyAgent = Class.forName("net.bytebuddy.agent.ByteBuddyAgent", true, ClassLoader.getSystemClassLoader());
            var instrumentation = (Instrumentation) byteBuddyAgent.getMethod("install").invoke(null);
            StartupLog.info("Using byte-buddy fallback");
            return instrumentation;
        } catch (Exception e) {
            storedExceptions.add(e);
        }

        var e = new IllegalStateException("Failed to obtain instrumentation.");
        storedExceptions.forEach(e::addSuppressed);
        throw e;
    }

    private static Instrumentation getFromOurOwnAgent() throws Exception {
        // This code may be surprising, but the DevAgent is *always* loaded on the system classloader.
        // If we have been loaded somewhere beneath, our copy of DevAgent may not be the same. To ensure we actually
        // get the "real" agent, we specifically grab the class from the system CL.
        var devAgent = Class.forName("net.neoforged.fmlstartup.DevAgent", true, ClassLoader.getSystemClassLoader());
        var instrumentation = (Instrumentation) devAgent.getMethod("getInstrumentation").invoke(null);
        StartupLog.info("Using our own agent");
        if (instrumentation == null) {
            throw new IllegalStateException("Our DevAgent was not attached. Pass an appropriate -javaagent parameter.");
        }
        return instrumentation;
    }
}
