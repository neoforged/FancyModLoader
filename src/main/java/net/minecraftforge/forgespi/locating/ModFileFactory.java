package net.minecraftforge.forgespi.locating;

import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.language.IModFileInfo;

public interface ModFileFactory {
    ModFileFactory FACTORY = Environment.get().getModFileFactory();
    IModFile build(final SecureJar jar, final IModLocator locator, ModFileInfoParser parser);

    interface ModFileInfoParser {
        IModFileInfo build(IModFile file);
    }

}
