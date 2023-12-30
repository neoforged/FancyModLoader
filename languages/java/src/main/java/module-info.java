open module net.neoforged.fancymodloader.language.java {
    requires cpw.mods.modlauncher;
    requires cpw.mods.securejarhandler;
    requires net.neoforged.bus;
    requires net.neoforged.neoforgespi;
    requires net.neoforged.mergetool.api;
    requires net.neoforged.fancymodloader.core;
    requires net.neoforged.fancymodloader.loader;
    requires org.apache.logging.log4j;
    requires org.objectweb.asm;

    exports net.neoforged.fml.common;
    exports net.neoforged.fml.javafmlmod;

    provides net.neoforged.neoforgespi.language.IModLanguageProvider with
        net.neoforged.fml.javafmlmod.FMLJavaModLanguageProvider;
}