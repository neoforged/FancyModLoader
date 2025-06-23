module cpw.mods.bootstraplauncher {
    uses java.util.function.Consumer;

    requires java.base;
    requires cpw.mods.securejarhandler;
    requires static org.jetbrains.annotations;

    exports cpw.mods.bootstraplauncher;
}
