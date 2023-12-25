open module net.neoforged.fancymodloader.core {
    requires com.electronwill.nightconfig.core;
    requires com.electronwill.nightconfig.toml;
    requires com.google.common;
    requires com.google.gson;
    requires cpw.mods.modlauncher;
    requires java.net.http;
    requires logging;
    requires maven.artifact;
    requires net.neoforged.bus;
    requires net.neoforged.neoforgespi;
    requires net.neoforged.mergetool.api;
    requires net.neoforged.fancymodloader.loader;
    requires org.apache.commons.io;
    requires org.apache.logging.log4j;
    requires org.slf4j;
    requires static org.jetbrains.annotations;

    exports net.neoforged.fml;
    exports net.neoforged.fml.config;
    exports net.neoforged.fml.event;
    exports net.neoforged.fml.util;
    exports net.neoforged.fml.util.thread;

    uses net.neoforged.fml.IModStateProvider;
    uses net.neoforged.fml.IBindingsProvider;
}