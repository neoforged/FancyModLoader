package net.neoforged.fml.common.asm.enumextension;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.ModuleLayerHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformingClassLoader;
import net.neoforged.fml.loading.LauncherTest;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class RuntimeEnumExtenderTest extends LauncherTest {

    @Test
    void name() throws Exception {
        installation.buildModJar("testmod.jar")
                .withModsToml(builder -> {
                    builder.unlicensedJavaMod();
                    builder.addMod("testmod", "1.0", config -> {
                        config.set("enumExtender", "");
                    });
                })
                .build();

        var layerHandler = mock(ModuleLayerHandler.class);
        var extender = new RuntimeEnumExtender();
        var transformStore = new TransformStore();
        var lph = new LaunchPluginHandler(layerHandler);

        new TransformingClassLoader(
                transformStore,
                lph,
                layerHandler
        );
    }
}
