package net.minecraftforge.forgespi.locating;

import net.minecraftforge.forgespi.Environment;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.nio.file.Path;

public interface ModFileFactory {
    ModFileFactory FACTORY = Environment.get().getModFileFactory();
    IModFile build(final Path path, final IModLocator locator, ModFileInfoParser parser);

    interface ModFileInfoParser {
        IModFileInfo build(IModFile file);
    }

}
