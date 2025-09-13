/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.VirtualJar;
import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.injection.invoke.arg.ArgsClassGenerator;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FMLMixinClassProcessor implements ClassProcessor {
    public static final ProcessorName NAME = new ProcessorName("neoforge", "mixin");

    private static final Logger LOGGER = LogUtils.getLogger();

    FMLMixinService service;
    private List<String> extraMixinConfigs = List.of();

    public FMLMixinClassProcessor() {
        System.setProperty("mixin.service", FMLMixinService.class.getName());
        System.setProperty("mixin.bootstrapService", FMLMixinServiceBootstrap.class.getName());
    }

    public synchronized void extraMixinConfigs(List<String> extraMixinConfigs) {
        this.extraMixinConfigs = extraMixinConfigs;
    }

    @Override
    public void initializeBytecodeProvider(BytecodeProvider bytecodeProvider, IEnvironment environment) {
        if (FMLLoader.getDist() == null) {
            throw new IllegalStateException("The dist must be set before initializing Mixin");
        }

        this.service = (FMLMixinService) MixinService.getService();

        MixinBootstrap.init();

        registerMixinConfigs();

        // We must transition to DEFAULT phase for normal Mixins to be applied at all
        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);

        service.setBytecodeProvider(new FMLClassBytecodeProvider(bytecodeProvider, this));
        MixinBootstrap.init();
        MixinBootstrap.getPlatform().inject();
    }

    // Increment to break compatibility; during a BC window, this should be set to the latest version. This is _not_ set
    // to COMPATIBILITY_LATEST, so that if mixin is bumped past a BC it does not break mods.
    @VisibleForTesting
    public static final int DEFAULT_BEHAVIOUR_VERSION = FabricUtil.COMPATIBILITY_0_14_0;
    @VisibleForTesting
    public static final ArtifactVersion HIGHEST_MIXIN_VERSION;
    @VisibleForTesting
    public static final ArtifactVersion LOWEST_MIXIN_VERSION;

    static {
        HIGHEST_MIXIN_VERSION = new DefaultArtifactVersion(Optional.ofNullable(FabricUtil.class.getModule().getDescriptor())
                .flatMap(ModuleDescriptor::version).map(ModuleDescriptor.Version::toString)
                .or(() -> Optional.ofNullable(FabricUtil.class.getPackage().getImplementationVersion()))
                .orElseThrow(() -> new IllegalStateException("Cannot determine version of currently running mixin")));
        int defaultMixinVersion = FMLMixinLaunchPlugin.DEFAULT_BEHAVIOUR_VERSION;
        int patch = defaultMixinVersion % 1000;
        defaultMixinVersion /= 1000;
        int minor = defaultMixinVersion % 1000;
        defaultMixinVersion /= 1000;
        int major = defaultMixinVersion;
        LOWEST_MIXIN_VERSION = new DefaultArtifactVersion(major + "." + minor + "." + patch);
    }

    private void registerMixinConfigs() {
        record AnnotationInfo(IModFile modFile, int behaviorVersion) {}

        Map<String, AnnotationInfo> configAnnotationInfo = new HashMap<>();
        var modList = LoadingModList.get();

        extraMixinConfigs.forEach(Mixins::addConfiguration);

        for (var modFileInfo : modList.getModFiles()) {
            var modFile = modFileInfo.getFile();
            for (var mixinConfig : modFile.getMixinConfigs()) {
                if (!areRequiredModsPresent(modFile, mixinConfig, modList)) {
                    continue;
                }

                // Validate the mixin version is supported
                if (!validateMixinBehavior(modFile, mixinConfig)) {
                    continue;
                }

                var currentInfo = new AnnotationInfo(modFile, calculateBehaviorVersion(mixinConfig.behaviorVersion()));
                var existingInfo = configAnnotationInfo.putIfAbsent(mixinConfig.config(), currentInfo);
                if (existingInfo != null && existingInfo.modFile() != modFile) {
                    ModLoader.addLoadingIssue(ModLoadingIssue.error(
                            "fml.modloadingissue.mixin.duplicate_config",
                            mixinConfig.config(),
                            existingInfo.modFile)
                            .withAffectedModFile(modFile));
                    continue;
                }

                // Grab the actual mixin config content from the Jar, which also allows us to print a nicer error if it's missing
                byte[] configContent;
                try {
                    configContent = modFile.getContents().readFile(mixinConfig.config());
                } catch (IOException e) {
                    configContent = null;
                }
                if (configContent == null) {
                    ModLoader.addLoadingIssue(ModLoadingIssue.error(
                            "fml.modloadingissue.mixin.missing_config",
                            mixinConfig.config())
                            .withAffectedModFile(modFile));
                    continue;
                }

                service.addMixinConfigContent(mixinConfig.config(), configContent);
                Mixins.addConfiguration(mixinConfig.config());
            }
        }

        var configMap = Mixins.getConfigs().stream().collect(Collectors.toMap(Config::getName, Config::getConfig));
        for (var entry : configAnnotationInfo.entrySet()) {
            var mixinConfigName = entry.getKey();
            var annotationInfo = entry.getValue();

            var config = configMap.get(mixinConfigName);
            if (config == null) {
                LOGGER.error("Mixin config {} from {} was not registered!", mixinConfigName, annotationInfo.modFile());
            } else {
                config.decorate(FabricUtil.KEY_MOD_ID, annotationInfo.modFile().getId());
                config.decorate(FabricUtil.KEY_COMPATIBILITY, annotationInfo.behaviorVersion());
            }
        }
    }

    private static boolean areRequiredModsPresent(ModFile modFile, ModFileParser.MixinConfig mixinConfig, LoadingModList modList) {
        for (var requiredModId : mixinConfig.requiredMods()) {
            if (modList.getModFileById(requiredModId) == null) {
                LOGGER.info("Mixin config {} from {} not applied as required mod '{}' is missing", mixinConfig.config(), modFile, requiredModId);
                return false;
            }
        }
        return true;
    }

    private static boolean validateMixinBehavior(ModFile modFile, ModFileParser.MixinConfig mixinConfig) {
        var behaviorVersion = mixinConfig.behaviorVersion();
        if (behaviorVersion == null) {
            return true; // Just uses default
        }

        if (behaviorVersion.compareTo(HIGHEST_MIXIN_VERSION) > 0) {
            ModLoader.addLoadingIssue(ModLoadingIssue.error(
                    "fml.modloadingissue.mixin.requested_behavior_too_new",
                    mixinConfig.config(),
                    behaviorVersion,
                    HIGHEST_MIXIN_VERSION).withAffectedModFile(modFile));
            return false;
        } else if (behaviorVersion.compareTo(LOWEST_MIXIN_VERSION) < 0) {
            ModLoader.addLoadingIssue(ModLoadingIssue.error(
                    "fml.modloadingissue.mixin.requested_behavior_too_old",
                    mixinConfig.config(),
                    behaviorVersion,
                    LOWEST_MIXIN_VERSION).withAffectedModFile(modFile));
            return false;
        }
        return true;
    }

    private static int calculateBehaviorVersion(@Nullable ArtifactVersion behaviorVersion) {
        if (behaviorVersion == null) {
            return DEFAULT_BEHAVIOUR_VERSION;
        }
        return behaviorVersion.getMajorVersion() * (1000 * 1000) +
                behaviorVersion.getMinorVersion() * 1000 +
                behaviorVersion.getIncrementalVersion();
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

    @Override
    public boolean handlesClass(SelectionContext context) {
        // Throw if the class was previously determined to be invalid
        String name = context.type().getClassName();
        if (this.service.getInternalClassTracker().isInvalidClass(name)) {
            throw new NoClassDefFoundError(String.format("%s is invalid", name));
        }

        if (!context.empty()) {
            if (processesClass(context.type())) {
                return true;
            }
            // If there is no chance of the class being processed, we do not bother.
        }

        if (this.service.getMixinTransformer().getExtensions().getSyntheticClassRegistry() == null) {
            return false;
        }

        return this.generatesClass(context.type());
    }

    private boolean processesClass(Type classType) {
        MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
        return this.service.getMixinTransformer().couldTransformClass(environment, classType.getClassName());
    }

    boolean generatesClass(Type classType) {
        return this.service.getMixinTransformer().getExtensions().getSyntheticClassRegistry().findSyntheticClass(classType.getClassName()) != null;
    }

    boolean generateClass(Type classType, ClassNode classNode) {
        return this.service.getMixinTransformer().generateClass(MixinEnvironment.getCurrentEnvironment(), classType.getClassName(), classNode);
    }

    @Override
    public boolean processClass(TransformationContext context) {
        var classType = context.type();
        var classNode = context.node();
        
        this.service.getInternalAuditTrail().setConsumer(classType.getClassName(), context.auditTrail());
        
        if (this.generatesClass(classType)) {
            return this.generateClass(classType, classNode);
        }

        MixinEnvironment environment = MixinEnvironment.getCurrentEnvironment();
        return this.service.getMixinTransformer().transformClass(environment, classType.getClassName(), classNode);
    }

    @Override
    public void afterProcessing(AfterProcessingContext context) {
        // Mixin wants to know when a class is _loaded_ for its internal tracking (to avoid allowing newly-loaded mixins,
        // since mixins can be loaded at arbitrary times, to affect already-loaded classes), but processors can
        // technically run on classes more than once (if, say, another transform requests the state before that
        // transform would run). Hence, why we are running in a post-result callback, which is guaranteed to be called
        // once, right before class load.
        this.service.getInternalClassTracker().addLoadedClass(context.type().getClassName());
    }

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public Set<String> generatesPackages() {
        return Set.of(ArgsClassGenerator.SYNTHETIC_PACKAGE);
    }
}
