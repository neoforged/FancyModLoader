package net.neoforged.fml.loading.mixin;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;

import java.util.List;
import java.util.Set;

public class FMLMixinTransformationService implements ITransformationService {
    private MixinFacade facade;
    
    @Override
    public String name() {
        return FMLMixinLaunchPlugin.NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {
        var plugin = environment.findLaunchPlugin(FMLMixinLaunchPlugin.NAME).orElseThrow(() -> new IllegalStateException("FMLMixinLaunchPlugin not found!"));
        this.facade = ((FMLMixinLaunchPlugin) plugin).getFacade();
    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        return List.of(new Resource(IModuleLayerManager.Layer.SERVICE, List.of(facade.createGeneratedCodeContainer())));
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {}

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of();
    }
}
