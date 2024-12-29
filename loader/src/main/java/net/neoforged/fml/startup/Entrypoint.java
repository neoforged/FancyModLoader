/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;

public abstract class Entrypoint {
    Entrypoint() {}

    protected static FMLStartupContext startup(String[] args, boolean headless, Dist forcedDist) {
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

        var cacheDir = new File(gameDir, ".neoforgecache");
        if (!cacheDir.exists() && !cacheDir.mkdir()) {
            StartupLog.error("Failed to create cache directory: {}", cacheDir);
        }

        var instrumentation = obtainInstrumentation();

        // Disabling JMX for JUnit improves startup time
        if (System.getProperty("log4j2.disable.jmx") == null) {
            System.setProperty("log4j2.disable.jmx", "true");
        }

        var startupArgs = new StartupArgs(
                gameDir,
                headless,
                forcedDist,
                args,
                new HashSet<>(),
                listClasspathEntries(),
                Thread.currentThread().getContextClassLoader());

        // Launch FML
        try {
            return FMLLoader.startup(instrumentation, startupArgs);
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

    private static File getGameDir(String[] args) {
        var gameDir = new File(getArg(args, "gameDir", "")).getAbsoluteFile();
        if (!gameDir.isDirectory()) {
            throw new RuntimeException("The game directory passed on the command-line is not a directory: " + gameDir);
        }
        return gameDir;
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
        var devAgent = Class.forName("net.neoforged.fml.startup.DevAgent", true, ClassLoader.getSystemClassLoader());
        var instrumentation = (Instrumentation) devAgent.getMethod("getInstrumentation").invoke(null);
        StartupLog.info("Using our own agent");
        if (instrumentation == null) {
            throw new IllegalStateException("Our DevAgent was not attached. Pass an appropriate -javaagent parameter.");
        }
        return instrumentation;
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
    protected static MethodHandle createMainMethodCallable(ClassLoader loader, String mainClassName) {
        try {
            var mainClass = Class.forName(mainClassName, true, loader);
            var lookup = MethodHandles.publicLookup();
            var methodType = MethodType.methodType(void.class, String[].class);
            return lookup.findStatic(mainClass, "main", methodType);
        } catch (ClassNotFoundException e) {
            throw new FatalStartupException("Missing main class " + mainClassName + " on the classpath.");
        } catch (NoSuchMethodException e) {
            throw new FatalStartupException(mainClassName + " is missing a static 'main' method.");
        } catch (Throwable e) {
            throw new FatalStartupException("Failed to create entrypoint object: " + e);
        }
    }
}
