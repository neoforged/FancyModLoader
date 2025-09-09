/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.Nullable;

public abstract class Entrypoint {
    protected Entrypoint() {}

    protected static FMLLoader startup(String[] args, boolean headless, Dist dist, boolean cleanDist) {
        StartupLog.debug("JVM Uptime: {}ms", ManagementFactory.getRuntimeMXBean().getUptime());

        args = ArgFileExpander.expandArgFiles(args);

        // In dev, do not overwrite the logging configuration if the user explicitly set another one.
        // In production, always overwrite the vanilla configuration.
        // TODO: Update this comment and coordinate with launchers to determine how to use THEIR logging config
        if (System.getProperty("log4j2.configurationFile") == null) {
            overwriteLoggingConfiguration();
        }

        var gameDir = getGameDir(args);
        StartupLog.info("Game Directory: {}", gameDir);

        // Disabling JMX for JUnit improves startup time
        if (System.getProperty("log4j2.disable.jmx") == null) {
            System.setProperty("log4j2.disable.jmx", "true");
        }

        var startupArgs = new StartupArgs(
                gameDir,
                headless,
                dist,
                cleanDist,
                args,
                new HashSet<>(),
                listClasspathEntries(),
                Thread.currentThread().getContextClassLoader());

        try {
            return FMLLoader.create(DevAgent.getInstrumentation(), startupArgs);
        } catch (Exception e) {
            var sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            StartupLog.error("Failed to start FML: {}", sw);
            throw new FatalStartupException("Failed to start FML: " + e);
        }
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

    private static Path getGameDir(String[] args) {
        var gameDir = new File(getArg(args, "gameDir", "")).getAbsoluteFile();
        if (!gameDir.isDirectory()) {
            throw new RuntimeException("The game directory passed on the command-line is not a directory: " + gameDir);
        }
        return gameDir.toPath();
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

    /**
     * Forces the log4j2 logging context to use the configuration shipped with fml_loader.
     */
    static void overwriteLoggingConfiguration() {
        var loggingConfigUrl = Entrypoint.class.getResource("log4j2.xml");
        if (loggingConfigUrl != null) {
            URI loggingConfigUri;
            try {
                loggingConfigUri = loggingConfigUrl.toURI();
            } catch (URISyntaxException e) {
                StartupLog.error("Failed to read FML logging configuration: {}", loggingConfigUrl, e);
                return;
            }
            StartupLog.debug("Reconfiguring logging with configuration from {}", loggingConfigUri);
            var configSource = ConfigurationSource.fromUri(loggingConfigUri);
            Configurator.reconfigure(ConfigurationFactory.getInstance().getConfiguration(LoggerContext.getContext(), configSource));
        }
    }

    /**
     * The only point of this is to get a neater stacktrace in all crash reports, since this
     * will replace three levels of Java reflection with one generated lambda method.
     */
    protected static MethodHandle createMainMethodCallable(FMLLoader loader, String mainClassName) {
        try {
            var mainClass = Class.forName(mainClassName, true, loader.currentClassLoader());
            var lookup = MethodHandles.publicLookup();
            var methodType = MethodType.methodType(void.class, String[].class);
            return lookup.findStatic(mainClass, "main", methodType);
        } catch (ClassNotFoundException e) {
            throw new FatalStartupException("Missing main class " + mainClassName + " on the classpath.");
        } catch (NoSuchMethodException e) {
            throw new FatalStartupException(mainClassName + " is missing a static 'main' method.");
        } catch (Throwable e) {
            throw new FatalStartupException("Failed to create entrypoint object.", e);
        }
    }

    protected static @Nullable Thread findThread(String threadName) {
        Thread serverThread = null;
        for (var thread : Thread.getAllStackTraces().keySet()) {
            if (threadName.equals(thread.getName())) {
                // While there's no guarantee for thread ids to be monotonically increasing
                // if there's ever a conflict between threads named "Server thread" because a mod spawned one
                // with that name, we'll pick the lower.
                if (serverThread == null || thread.threadId() <= serverThread.threadId()) {
                    serverThread = thread;
                }
            }
        }
        return serverThread;
    }
}
