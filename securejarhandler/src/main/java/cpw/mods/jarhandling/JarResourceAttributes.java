package cpw.mods.jarhandling;

import java.nio.file.attribute.FileTime;

/**
 * Metadata attributes of a {@link JarResource}.
 *
 * @param lastModified The last modification time of the resource.
 * @param size         The file size of the resource in bytes.
 */
public record JarResourceAttributes(FileTime lastModified, long size) {}
