
module cpw.mods.securejarhandler {
    exports cpw.mods.jarhandling;
    exports cpw.mods.jarhandling.impl; // TODO - Bump version, and remove this export, you don't need our implementation
    exports cpw.mods.cl;

    requires jdk.unsupported;
    requires java.base;
    requires static org.jetbrains.annotations;
}
