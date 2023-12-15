package net.neoforged.fml.loading.targets;

import net.neoforged.api.distmarker.Dist;

public class JUnitLaunchTarget extends CommonDevLaunchHandler {
    @Override
    public Dist getDist() {
        return Dist.CLIENT;
    }

    @Override
    protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        Class.forName(gameLayer.findModule("neoforge").orElseThrow(), "net.neoforged.neoforge.JUnitMain").getMethod("main", String[].class).invoke(null, (Object)arguments);
    }

    @Override
    public String name() {
        return "junitfml";
    }
}
