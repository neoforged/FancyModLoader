package cpw.mods.jarhandling;

import java.nio.file.attribute.FileTime;

public record JarResourceAttributes(FileTime lastModified, long size) {}
