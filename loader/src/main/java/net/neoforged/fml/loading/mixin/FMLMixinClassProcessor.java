package net.neoforged.fml.loading.mixin;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.IEnvironment;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
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

    private void registerMixinConfigs() {
        Map<String, String> configModIds = new HashMap<>();
        var modList = LoadingModList.get();

        extraMixinConfigs.forEach(Mixins::addConfiguration);

        modList.getModFiles().stream()
                .map(ModFileInfo::getFile)
                .forEach(file -> {
                    final String modId = file.getModInfos().getFirst().getModId();
                    for (ModFileParser.MixinConfig potential : file.getMixinConfigs()) {
                        var existingModId = configModIds.putIfAbsent(potential.config(), modId);
                        if (existingModId != null && !existingModId.equals(modId)) {
                            LOGGER.error("Mixin config {} is registered by multiple mods: {} and {}", potential.config(), existingModId, modId);
                            ModLoader.addLoadingIssue(ModLoadingIssue.error(
                                    "fml.modloadingissue.mixin.duplicate_config",
                                    potential.config(),
                                    existingModId,
                                    modId));
                        }
                        if (potential.requiredMods().stream().allMatch(id -> modList.getModFileById(id) != null)) {
                            Mixins.addConfiguration(potential.config());
                        } else {
                            LOGGER.debug("Mixin config {} for mod {} not applied as required mods are missing", potential.config(), modId);
                        }
                    }
                });

        final var configMap = Mixins.getConfigs().stream().collect(
                Collectors.toMap(Config::getName, Config::getConfig));
        configModIds.forEach((fileName, modId) -> {
            if (modId == null) return;

            final var config = configMap.get(fileName);
            if (config == null) {
                LOGGER.error("Config file {} was not registered!", fileName);
            } else {
                config.decorate(FabricUtil.KEY_MOD_ID, modId);
            }
        });
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
