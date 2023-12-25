open module net.neoforged.fancymodloader.language.lowcode {
    requires cpw.mods.modlauncher;
    requires logging;
    requires net.neoforged.bus;
    requires net.neoforged.neoforgespi;
    requires net.neoforged.fancymodloader.core;
    requires net.neoforged.fancymodloader.loader;
    requires org.apache.logging.log4j;
    requires org.slf4j;
    requires static org.jetbrains.annotations;

    exports net.neoforged.fml.lowcodemod;

    provides net.neoforged.neoforgespi.language.IModLanguageProvider with
        net.neoforged.fml.lowcodemod.LowCodeModLanguageProvider;
}