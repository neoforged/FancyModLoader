package net.neoforged.fmlstartup;

import org.jetbrains.annotations.Nullable;

/**
 * @param moduleName
 */
public record CachedMetadata(@Nullable String moduleName,
                             boolean forceBootLayer) {
}
