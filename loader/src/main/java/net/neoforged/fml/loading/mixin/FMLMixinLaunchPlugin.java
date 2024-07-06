package net.neoforged.fml.loading.mixin;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.IClassProcessor;
import org.spongepowered.asm.launch.Phases;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

public class FMLMixinLaunchPlugin implements ILaunchPluginService {
    public static final String NAME = "fml-mixin";

    private final List<IClassProcessor> processors;

    private final FMLAuditTrail auditTrail;

    public FMLMixinLaunchPlugin(FMLMixinService service) {
        this.auditTrail = (FMLAuditTrail) service.getAuditTrail();
        this.processors = new ArrayList<>(service.getProcessors());
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        throw new IllegalStateException("Outdated ModLauncher");
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        if (NAME.equals(reason)) {
            return Phases.NONE;
        }

        // All processors can nominate phases, we aggregate the results
        EnumSet<Phase> phases = EnumSet.noneOf(Phase.class);
        for (var postProcessor : processors) {
            var processorVote = postProcessor.handlesClass(classType, isEmpty, reason);
            if (processorVote != null) {
                phases.addAll(processorVote);
            }
        }

        return phases;
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        boolean processed = false;

        for (var processor : this.processors) {
            processed |= processor.processClass(phase, classNode, classType, reason);
        }

        return processed;
    }

    @Override
    public void customAuditConsumer(String className, Consumer<String[]> auditDataAcceptor) {
        if (this.auditTrail != null) {
            this.auditTrail.setConsumer(className, auditDataAcceptor);
        }
    }
}
