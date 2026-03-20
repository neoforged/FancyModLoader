/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.junit;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.startup.JUnitGameBootstrapper;
import net.neoforged.fml.startup.StartupArgs;
import org.junit.platform.engine.support.store.Namespace;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A session listener for JUnit environments that will bootstrap a Minecraft (FML) environment.
 */
public class JUnitService implements LauncherSessionListener {
    private static final Logger LOG = LoggerFactory.getLogger(JUnitService.class);

    private static final Namespace NAMESPACE = Namespace.create("fml", "junit");
    static final String KEY_ACTIVE_LOADER = "activeLoader";

    public JUnitService() {}

    record ActivatedLoader(
            ClassLoader previousLoader,
            FMLLoader loader) implements AutoCloseable {
        @Override
        public void close() {
            loader.close();
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        var store = session.getStore();

        // When the tests are started we want to make sure that they run on the transforming class loader which is set up by
        // creating a FML loader.
        var loader = store.getOrComputeIfAbsent(NAMESPACE, KEY_ACTIVE_LOADER, ignored -> createLoader(), ActivatedLoader.class);
        LOG.info("Active FML loader {}", Integer.toHexString(System.identityHashCode(loader.loader)));
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        var loader = session.getStore().remove(NAMESPACE, KEY_ACTIVE_LOADER, ActivatedLoader.class);
        if (loader != null) {
            loader.close();
        }
    }

    private ActivatedLoader createLoader() {
        var previousLoader = Thread.currentThread().getContextClassLoader();

        long fmlStart = System.nanoTime();

        // Start up FML
        var startupArgs = new StartupArgs(
                Path.of(""),
                true,
                Dist.DEDICATED_SERVER,
                false,
                new String[] {},
                Set.of(),
                List.of(),
                previousLoader);
        var loader = FMLLoader.create(startupArgs);

        LOG.info("Starting FML took {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - fmlStart));

        if (loader.getLoadingModList().hasErrors()) {
            throw new ModLoadingException(loader.getLoadingModList().getModLoadingIssues());
        }

        for (var bootstrapper : ServiceLoader.load(JUnitGameBootstrapper.class, loader.getCurrentClassLoader())) {
            long bootstrapStart = System.nanoTime();
            bootstrapper.bootstrap(loader);
            LOG.info("Running game bootstrapper {} took {}ms", bootstrapper.getClass().getName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - bootstrapStart));
        }

        return new ActivatedLoader(previousLoader, loader);
    }
}
