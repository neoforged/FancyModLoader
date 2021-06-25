import cpw.mods.cl.ModularURLHandler;
import cpw.mods.cl.UnionURLStreamHandler;
import cpw.mods.niofs.union.UnionFileSystemProvider;

module cpw.mods.securejarhandler {
    exports cpw.mods.jarhandling;
    exports cpw.mods.jarhandling.impl;
    exports cpw.mods.cl;
    requires jdk.unsupported;
    requires org.objectweb.asm;
    provides java.nio.file.spi.FileSystemProvider with UnionFileSystemProvider;
    uses cpw.mods.cl.ModularURLHandler.IURLProvider;
    provides ModularURLHandler.IURLProvider with UnionURLStreamHandler;
}