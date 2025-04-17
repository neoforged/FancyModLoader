package net.neoforged.fml.earlydisplay.theme;

import org.jetbrains.annotations.Nullable;

public record ThemeTexture(ThemeResource resource, TextureScaling scaling, @Nullable AnimationMetadata animation) {
    public ThemeTexture(ThemeResource resource, TextureScaling scaling) {
        this(resource, scaling, null);
    }

    @Override
    public String toString() {
        return resource.toString();
    }
}
