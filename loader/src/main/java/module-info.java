open module net.neoforged.fancymodloader.loader {
    requires net.neoforged.accesstransformer;
    requires net.neoforged.accesstransformer.modlauncher;
    requires com.electronwill.nightconfig.core;
    requires com.google.common;
    requires com.google.gson;
    requires cpw.mods.modlauncher;
    requires cpw.mods.securejarhandler;
    requires JarJarSelector;
    requires logging;
    requires maven.artifact;
    requires net.neoforged.neoforgespi;
    requires net.neoforged.mergetool.api;
    requires org.apache.commons.lang3;
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;
    requires org.objectweb.asm.commons;
    requires org.objectweb.asm.tree;
    requires org.objectweb.asm.tree.analysis;
    requires org.objectweb.asm.util;
    requires org.objectweb.asm;
    requires org.slf4j;
    requires org.spongepowered.mixin;
    requires terminalconsoleappender;
    requires static org.jetbrains.annotations;

    exports net.neoforged.fml.common.asm;
    exports net.neoforged.fml.loading;
    exports net.neoforged.fml.loading.log4j;
    exports net.neoforged.fml.loading.moddiscovery;
    exports net.neoforged.fml.loading.progress;
    exports net.neoforged.fml.loading.targets;
    exports net.neoforged.fml.loading.toposort;
    exports net.neoforged.fml.server;

    uses net.neoforged.neoforgespi.coremod.ICoreModProvider;
    uses net.neoforged.neoforgespi.language.IModLanguageProvider;
    uses net.neoforged.neoforgespi.locating.IModLocator;
    uses net.neoforged.neoforgespi.locating.IDependencyLocator;
    uses net.neoforged.fml.loading.ImmediateWindowProvider;

    provides cpw.mods.modlauncher.api.ILaunchHandlerService with
        net.neoforged.fml.loading.targets.ForgeClientLaunchHandler,
        net.neoforged.fml.loading.targets.ForgeClientDevLaunchHandler,
        net.neoforged.fml.loading.targets.ForgeClientUserdevLaunchHandler,
        net.neoforged.fml.loading.targets.ForgeServerLaunchHandler,
        net.neoforged.fml.loading.targets.ForgeServerDevLaunchHandler,
        net.neoforged.fml.loading.targets.ForgeServerUserdevLaunchHandler,
        net.neoforged.fml.loading.targets.ForgeDataDevLaunchHandler,
        net.neoforged.fml.loading.targets.ForgeDataUserdevLaunchHandler;
    provides cpw.mods.modlauncher.api.INameMappingService with
        net.neoforged.fml.loading.MCPNamingService;
    provides cpw.mods.modlauncher.api.ITransformationService with
        net.neoforged.fml.loading.FMLServiceProvider;
    provides cpw.mods.modlauncher.serviceapi.ILaunchPluginService with
        net.neoforged.fml.loading.log4j.SLF4JFixerLaunchPluginService,
        net.neoforged.fml.common.asm.RuntimeDistCleaner,
        net.neoforged.fml.common.asm.RuntimeEnumExtender;
    provides cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService with
        net.neoforged.fml.loading.ModDirTransformerDiscoverer,
        net.neoforged.fml.loading.ClasspathTransformerDiscoverer;
    provides net.neoforged.neoforgespi.locating.IDependencyLocator with
        net.neoforged.fml.loading.moddiscovery.JarInJarDependencyLocator;
    provides net.neoforged.neoforgespi.locating.IModLocator with
        net.neoforged.fml.loading.moddiscovery.ModsFolderLocator,
        net.neoforged.fml.loading.moddiscovery.MavenDirectoryLocator,
        net.neoforged.fml.loading.moddiscovery.ExplodedDirectoryLocator,
        net.neoforged.fml.loading.moddiscovery.MinecraftLocator,
        net.neoforged.fml.loading.moddiscovery.ClasspathLocator,
        net.neoforged.fml.loading.moddiscovery.BuiltinGameLibraryLocator;
}