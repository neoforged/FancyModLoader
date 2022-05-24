package cpw.mods.cl.test;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

public class TestClassLoader {
    public static void main(String[] args) {
        new TestClassLoader().testCL();
    }
    private static final String[] CL = "/home/cpw/minecraft/libraries/net/minecraftforge/fmlloader/36.1.24/fmlloader-36.1.24.jar:/home/cpw/minecraft/libraries/org/ow2/asm/asm/9.0/asm-9.0.jar:/home/cpw/minecraft/libraries/org/ow2/asm/asm-commons/9.0/asm-commons-9.0.jar:/home/cpw/minecraft/libraries/org/ow2/asm/asm-tree/9.0/asm-tree-9.0.jar:/home/cpw/minecraft/libraries/cpw/mods/modlauncher/9.0.1/modlauncher-9.0.1.jar:/home/cpw/minecraft/libraries/cpw/mods/grossjava9hacks/2.0.7/grossjava9hacks-2.0.7.jar:/home/cpw/minecraft/libraries/org/ow2/asm/asm-util/9.0/asm-util-9.0.jar:/home/cpw/minecraft/libraries/org/ow2/asm/asm-analysis/9.0/asm-analysis-9.0.jar:/home/cpw/minecraft/libraries/net/minecraftforge/accesstransformers/3.0.1/accesstransformers-3.0.1.jar:/home/cpw/minecraft/libraries/org/antlr/antlr4-runtime/4.9.1/antlr4-runtime-4.9.1.jar:/home/cpw/minecraft/libraries/net/minecraftforge/eventbus/4.0.0/eventbus-4.0.0.jar:/home/cpw/minecraft/libraries/net/minecraftforge/forgespi/3.2.0/forgespi-3.2.0.jar:/home/cpw/minecraft/libraries/net/minecraftforge/coremods/4.0.6/coremods-4.0.6.jar:/home/cpw/minecraft/libraries/net/minecraftforge/unsafe/0.2.0/unsafe-0.2.0.jar:/home/cpw/minecraft/libraries/com/electronwill/night-config/core/3.6.3/core-3.6.3.jar:/home/cpw/minecraft/libraries/com/electronwill/night-config/toml/3.6.3/toml-3.6.3.jar:/home/cpw/minecraft/libraries/org/jline/jline/3.12.1/jline-3.12.1.jar:/home/cpw/minecraft/libraries/org/apache/maven/maven-artifact/3.6.3/maven-artifact-3.6.3.jar:/home/cpw/minecraft/libraries/net/jodah/typetools/0.8.3/typetools-0.8.3.jar:/home/cpw/minecraft/libraries/net/minecrell/terminalconsoleappender/1.2.0/terminalconsoleappender-1.2.0.jar:/home/cpw/minecraft/libraries/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar:/home/cpw/minecraft/libraries/org/spongepowered/mixin/0.8.2/mixin-0.8.2.jar:/home/cpw/minecraft/libraries/net/minecraftforge/nashorn-core-compat/15.1.1.1/nashorn-core-compat-15.1.1.1.jar:/home/cpw/minecraft/libraries/com/mojang/blocklist/1.0.5/blocklist-1.0.5.jar:/home/cpw/minecraft/libraries/com/mojang/patchy/2.1.6/patchy-2.1.6.jar:/home/cpw/minecraft/libraries/com/github/oshi/oshi-core/5.3.4/oshi-core-5.3.4.jar:/home/cpw/minecraft/libraries/net/java/dev/jna/jna/5.6.0/jna-5.6.0.jar:/home/cpw/minecraft/libraries/net/java/dev/jna/jna-platform/5.6.0/jna-platform-5.6.0.jar:/home/cpw/minecraft/libraries/org/slf4j/slf4j-api/1.8.0-beta4/slf4j-api-1.8.0-beta4.jar:/home/cpw/minecraft/libraries/org/apache/logging/log4j/log4j-slf4j18-impl/2.14.1/log4j-slf4j18-impl-2.14.1.jar:/home/cpw/minecraft/libraries/com/ibm/icu/icu4j/66.1/icu4j-66.1.jar:/home/cpw/minecraft/libraries/com/mojang/javabridge/1.1.23/javabridge-1.1.23.jar:/home/cpw/minecraft/libraries/net/sf/jopt-simple/jopt-simple/5.0.3/jopt-simple-5.0.3.jar:/home/cpw/minecraft/libraries/io/netty/netty-all/4.1.25.Final/netty-all-4.1.25.Final.jar:/home/cpw/minecraft/libraries/com/google/guava/guava/21.0/guava-21.0.jar:/home/cpw/minecraft/libraries/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar:/home/cpw/minecraft/libraries/commons-io/commons-io/2.5/commons-io-2.5.jar:/home/cpw/minecraft/libraries/commons-codec/commons-codec/1.10/commons-codec-1.10.jar:/home/cpw/minecraft/libraries/net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar:/home/cpw/minecraft/libraries/net/java/jutils/jutils/1.0.0/jutils-1.0.0.jar:/home/cpw/minecraft/libraries/com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar:/home/cpw/minecraft/libraries/com/mojang/datafixerupper/4.0.26/datafixerupper-4.0.26.jar:/home/cpw/minecraft/libraries/com/google/code/gson/gson/2.8.0/gson-2.8.0.jar:/home/cpw/minecraft/libraries/com/mojang/authlib/2.2.30/authlib-2.2.30.jar:/home/cpw/minecraft/libraries/org/apache/commons/commons-compress/1.8.1/commons-compress-1.8.1.jar:/home/cpw/minecraft/libraries/org/apache/httpcomponents/httpclient/4.3.3/httpclient-4.3.3.jar:/home/cpw/minecraft/libraries/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3.jar:/home/cpw/minecraft/libraries/org/apache/httpcomponents/httpcore/4.3.2/httpcore-4.3.2.jar:/home/cpw/minecraft/libraries/it/unimi/dsi/fastutil/8.2.1/fastutil-8.2.1.jar:/home/cpw/minecraft/libraries/org/apache/logging/log4j/log4j-api/2.14.1/log4j-api-2.14.1.jar:/home/cpw/minecraft/libraries/org/apache/logging/log4j/log4j-core/2.14.1/log4j-core-2.14.1.jar:/home/cpw/minecraft/libraries/org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2.jar:/home/cpw/minecraft/libraries/org/lwjgl/lwjgl-jemalloc/3.2.2/lwjgl-jemalloc-3.2.2.jar:/home/cpw/minecraft/libraries/org/lwjgl/lwjgl-openal/3.2.2/lwjgl-openal-3.2.2.jar:/home/cpw/minecraft/libraries/org/lwjgl/lwjgl-opengl/3.2.2/lwjgl-opengl-3.2.2.jar:/home/cpw/minecraft/libraries/org/lwjgl/lwjgl-glfw/3.2.2/lwjgl-glfw-3.2.2.jar:/home/cpw/minecraft/libraries/org/lwjgl/lwjgl-stb/3.2.2/lwjgl-stb-3.2.2.jar:/home/cpw/minecraft/libraries/org/lwjgl/lwjgl-tinyfd/3.2.2/lwjgl-tinyfd-3.2.2.jar:/home/cpw/minecraft/libraries/com/mojang/text2speech/1.11.3/text2speech-1.11.3.jar".split(":");

    @SuppressWarnings("unchecked")
    @Test
    @Disabled
    void testCL() {
        var cl = Arrays.stream(CL)
                .map(Paths::get)
                .filter(Files::exists)
                .map(SecureJar::from)
                .toArray(SecureJar[]::new);
        if (cl.length == 0)
            return;
        var jf = JarModuleFinder.of(cl);
        var cf = ModuleLayer.boot().configuration();
        var newcf = cf.resolveAndBind(jf, ModuleFinder.ofSystem(), List.of("cpw.mods.modlauncher"));
        var mycl = new ModuleClassLoader("test", newcf, List.of());
        var layer = ModuleLayer.defineModules(newcf, List.of(ModuleLayer.boot()), m->mycl);
        Thread.currentThread().setContextClassLoader(mycl);
        var sl = ServiceLoader.load(layer.layer(), Consumer.class);
        var c = sl.stream().map(ServiceLoader.Provider::get).toList();
        c.get(0).accept(new String[0]);
    }
}
