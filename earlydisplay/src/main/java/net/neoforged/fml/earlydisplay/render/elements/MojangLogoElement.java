/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.elements;

import java.io.IOException;
import net.neoforged.fml.earlydisplay.render.MaterializedTheme;
import net.neoforged.fml.earlydisplay.render.RenderContext;
import net.neoforged.fml.earlydisplay.render.Texture;
import net.neoforged.fml.earlydisplay.theme.ImageLoader;
import net.neoforged.fml.earlydisplay.theme.NativeBuffer;
import net.neoforged.fml.earlydisplay.theme.TextureScaling;
import net.neoforged.fml.earlydisplay.theme.ThemeMojangLogoElement;
import net.neoforged.fml.earlydisplay.theme.UncompressedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MojangLogoElement extends RenderElement {
    private static final Logger LOGGER = LoggerFactory.getLogger(MojangLogoElement.class);

    /**
     * Potential paths on the classpath for the Mojang logo.
     */
    private static final String[] LOGO_PATHS = {
            "assets/minecraft/textures/gui/title/mojangstudios.png"
    };

    private final Texture mojangLogo;

    public MojangLogoElement(ThemeMojangLogoElement element, MaterializedTheme theme) {
        super(element, theme);

        // Try the context classloader first, but if we cannot find a logo there,
        // try the system loader, since FML might already have isolated us from
        // the MC assets that can be found there.
        ClassLoader[] classLoaders = {
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        Texture mojangLogo = null;
        for (var logoPath : LOGO_PATHS) {
            for (var classLoader : classLoaders) {
                try (var buffer = NativeBuffer.loadFromClasspath(logoPath, classLoader)) {
                    var loadResult = ImageLoader.tryLoadImage("mojang logo", null, buffer);
                    if (loadResult instanceof ImageLoader.Result.Error(Exception exception)) {
                        LOGGER.debug("Failed to load Mojang logo from {}: {}", logoPath, exception);
                    } else if (loadResult instanceof ImageLoader.Result.Success(UncompressedImage image)) {
                        mojangLogo = Texture.create(image, "mojang logo", new TextureScaling.Stretch(512, 128, true), null);
                        break;
                    }
                } catch (IOException e) {
                    LOGGER.debug("Failed to load Mojang logo from {}", logoPath);
                }
            }
        }
        if (mojangLogo == null) {
            LOGGER.warn("Failed to find Mojang logo at any of the expected classpath locations.");
        }
        this.mojangLogo = mojangLogo;
    }

    @Override
    public void render(RenderContext context) {
        if (this.mojangLogo == null) {
            return;
        }

        var bounds = resolveBounds(context.availableWidth(), context.availableHeight(), 512, 128);

        float x0 = bounds.left();
        float x1 = (bounds.left() + bounds.right()) / 2;
        float x2 = bounds.right();
        context.blitTextureRegion(this.mojangLogo, x0, bounds.top(), x1 - x0, bounds.height(), -1, 0, 1, 0, 0.5f);
        context.blitTextureRegion(this.mojangLogo, x1, bounds.top(), x2 - x1, bounds.height(), -1, 0, 1, 0.5f, 1f);
    }

    @Override
    public void close() {
        if (mojangLogo != null) {
            mojangLogo.close();
        }
    }
}
