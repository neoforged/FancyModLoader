/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.io.IOException;
import java.nio.file.Path;
import net.neoforged.fml.earlydisplay.theme.NativeBuffer;
import net.neoforged.fml.earlydisplay.theme.ThemeResource;
import org.jetbrains.annotations.Nullable;

public class ElementShader implements AutoCloseable {
    public static final String UNIFORM_SCREEN_SIZE = "screenSize";
    public static final String UNIFORM_SAMPLER0 = "tex";

    private final String name;
    private final ThemeResource vertexShader;
    private final ThemeResource fragmentShader;
    private final @Nullable Path externalThemeDirectory;

    public ElementShader(String name, ThemeResource vertexShader, ThemeResource fragmentShader, @Nullable Path externalThemeDirectory) {
        this.name = name;
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        this.externalThemeDirectory = externalThemeDirectory;
    }

    public String getName() {
        return this.name;
    }

    public String getVertexShaderPath() {
        return this.vertexShader.path();
    }

    public String getFragmentShaderPath() {
        return this.fragmentShader.path();
    }

    public NativeBuffer loadVertexShader() throws IOException {
        return this.vertexShader.toNativeBuffer(this.externalThemeDirectory);
    }

    public NativeBuffer loadFragmentShader() throws IOException {
        return this.fragmentShader.toNativeBuffer(this.externalThemeDirectory);
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return name;
    }
}
