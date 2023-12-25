open module net.neoforged.fancymodloader.events {
    requires net.neoforged.fancymodloader.core;
    requires net.neoforged.mergetool.api;
    requires net.neoforged.fancymodloader.loader;
    requires cpw.mods.modlauncher;
    requires net.neoforged.bus;

    exports net.neoforged.fml.core;
    exports net.neoforged.fml.event.config;
    exports net.neoforged.fml.event.lifecycle;

    provides net.neoforged.fml.IModStateProvider with 
        net.neoforged.fml.core.ModStateProvider;
}