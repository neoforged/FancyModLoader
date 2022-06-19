import cpw.mods.cl.ModularURLHandler;
import cpw.mods.cl.UnionURLStreamHandler;
import cpw.mods.niofs.union.UnionFileSystemProvider;

module cpw.mods.securejarhandler {
    exports cpw.mods.jarhandling;
    exports cpw.mods.jarhandling.impl; // TODO - Bump version, and remove this export, you don't need our implementation
    exports cpw.mods.cl;
    exports cpw.mods.niofs.union;
    requires jdk.unsupported;
    requires org.objectweb.asm;
    requires org.objectweb.asm.tree;
    requires java.base;
    provides java.nio.file.spi.FileSystemProvider with UnionFileSystemProvider;
    uses cpw.mods.cl.ModularURLHandler.IURLProvider;
    provides ModularURLHandler.IURLProvider with UnionURLStreamHandler;
}