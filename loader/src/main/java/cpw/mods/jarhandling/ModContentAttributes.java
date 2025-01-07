package cpw.mods.jarhandling;

import java.nio.file.attribute.FileTime;

public record ModContentAttributes(FileTime lastModified, long size) {}
