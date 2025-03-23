package net.neoforged.fml.earlydisplay.theme;

import org.jetbrains.annotations.Nullable;

public record ThemeTexture(ThemeResource resource, @Nullable AnimationMetadata animation) {
    public ThemeTexture(ThemeResource resource) {
        this(resource, null);
    }

    @Override
    public String toString() {
        return resource.toString();
    }
}
