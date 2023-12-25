open module net.neoforged.neoforgespi {
    requires maven.artifact;
    requires net.neoforged.mergetool.api;
    requires org.apache.logging.log4j;
    requires org.objectweb.asm;
    requires cpw.mods.securejarhandler;
    requires cpw.mods.modlauncher;

    exports net.neoforged.neoforgespi;
    exports net.neoforged.neoforgespi.coremod;
    exports net.neoforged.neoforgespi.language;
    exports net.neoforged.neoforgespi.locating;
}