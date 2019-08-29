package net.minecraftforge.forgespi.coremod;

import java.io.*;
import java.nio.file.*;

/**
 * Interface for core mods to discover content and properties
 * of their location and context to the coremod implementation.
 */
public interface ICoreModFile {
    String getOwnerId();
    Reader readCoreMod() throws IOException;
    Path getPath();
    Reader getAdditionalFile(final String fileName) throws IOException;
}
