/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.VirtualJar;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.net.URISyntaxException;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.invoke.arg.ArgsClassGenerator;
import org.spongepowered.asm.service.MixinService;

/**
 * Encapsulates the code required to interact with Mixin.
 */
public final class MixinFacade {
    private static final Logger LOG = LoggerFactory.getLogger(MixinFacade.class);

    private final FMLMixinLaunchPlugin launchPlugin;
    private final FMLMixinService service;

    public MixinFacade(FMLMixinLaunchPlugin launchPlugin) {
        if (FMLLoader.getDist() == null) {
            throw new IllegalStateException("The dist must be set before initializing Mixin");
        }

        service = (FMLMixinService) MixinService.getService();
        this.launchPlugin = launchPlugin;
    }

    public void finishInitialization(ILaunchPluginService.ITransformerLoader transformerLoader) {
        MixinBootstrap.init();

        // We must transition to DEFAULT phase for normal Mixins to be applied at all
        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);

        service.setBytecodeProvider(new FMLClassBytecodeProvider(transformerLoader, this.launchPlugin));
        MixinBootstrap.init();
        MixinBootstrap.getPlatform().inject();
    }

    private void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            var m = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            m.setAccessible(true);
            m.invoke(null, phase);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SecureJar createGeneratedCodeContainer() {
        try {
            Path codeSource = Path.of(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            return new VirtualJar("mixin_synthetic", codeSource, ArgsClassGenerator.SYNTHETIC_PACKAGE);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
