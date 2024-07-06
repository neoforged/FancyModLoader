package net.neoforged.fml.loading.mixin;

import com.google.common.collect.ImmutableList;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.launch.IClassProcessor;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.mixin.MixinEnvironment.Phase;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.Constants;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FMLMixinService implements IMixinService {
    private final ConcurrentHashMap<String, LoggerAdapterLog4j2> loggers = new ConcurrentHashMap<>();
    private final ReEntranceLock lock = new ReEntranceLock(1);

    /**
     * Class provider, either uses hacky internals or provided service
     */
    private IClassProvider classProvider;

    /**
     * Bytecode provider, either uses hacky internals or provided service
     */
    @Nullable
    private IClassBytecodeProvider bytecodeProvider;

    /**
     * Container for the mixin pipeline which is called by the launch plugin
     */
    private MixinTransformationHandler transformationHandler;

    /**
     * Class tracker, tracks class loads and registered invalid classes
     */
    private FMLClassTracker classTracker;

    /**
     * Audit trail adapter
     */
    private FMLAuditTrail auditTrail;

    @Override
    public void prepare() {
    }

    @Override
    public Phase getInitialPhase() {
        return Phase.PREINIT;
    }

    @Override
    public void init() {
    }

    @Override
    public void beginPhase() {
    }

    @Override
    public void checkEnv(Object bootSource) {
    }

    @Override
    public ReEntranceLock getReEntranceLock() {
        return lock;
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        return List.of();
    }

    @Override
    public String getSideName() {
        return switch (FMLLoader.getDist()) {
            case CLIENT -> Constants.SIDE_CLIENT;
            case DEDICATED_SERVER -> Constants.SIDE_SERVER;
        };
    }

    public void setBytecodeProvider(IClassBytecodeProvider bytecodeProvider) {
        this.bytecodeProvider = bytecodeProvider;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory) {
            this.getTransformationHandler().offer((IMixinTransformerFactory) internal);
        }
    }

    @Override
    public String getName() {
        return "FML";
    }

    @Override
    public CompatibilityLevel getMinCompatibilityLevel() {
        return CompatibilityLevel.JAVA_21;
    }

    @Override
    public CompatibilityLevel getMaxCompatibilityLevel() {
        return null;
    }

    @Override
    public ILogger getLogger(String name) {
        return loggers.computeIfAbsent(name, LoggerAdapterLog4j2::new);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public IClassProvider getClassProvider() {
        if (this.classProvider == null) {
            this.classProvider = new FMLClassProvider();
        }
        return this.classProvider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        if (this.bytecodeProvider == null) {
            throw new IllegalStateException("Service initialisation incomplete, launch plugin was not created");
        }
        return this.bytecodeProvider;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null;
    }

    @Override
    public IClassTracker getClassTracker() {
        if (this.classTracker == null) {
            this.classTracker = new FMLClassTracker();
        }
        return this.classTracker;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        if (this.auditTrail == null) {
            this.auditTrail = new FMLAuditTrail();
        }
        return this.auditTrail;
    }

    /**
     * Get (or create) the transformation handler
     */
    private MixinTransformationHandler getTransformationHandler() {
        if (this.transformationHandler == null) {
            this.transformationHandler = new MixinTransformationHandler();
        }
        return this.transformationHandler;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return List.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual("fml");
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    /**
     * Internal method to retrieve the class processors which will be called by
     * the launch plugin
     */
    public Collection<IClassProcessor> getProcessors() {
        return ImmutableList.<IClassProcessor>of(
                this.getTransformationHandler(),
                (IClassProcessor) this.getClassTracker()
        );
    }
}
