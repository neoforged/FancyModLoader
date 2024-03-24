/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class ModLoadingContext
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<ModLoadingContext> context = ThreadLocal.withInitial(ModLoadingContext::new);
    private Object languageExtension;
    private ModLoadingStage stage;

    public static ModLoadingContext get() {
        return context.get();
    }

    private ModContainer activeContainer;

    public void setActiveContainer(final ModContainer container) {
        this.activeContainer = container;
        this.languageExtension = container == null ? null : container.contextExtension.get();
    }

    public ModContainer getActiveContainer() {
        return activeContainer == null ? ModList.get().getModContainerById("minecraft").orElseThrow(()->new RuntimeException("Where is minecraft???!")) : activeContainer;
    }

    public String getActiveNamespace() {
        return activeContainer == null ? "minecraft" : activeContainer.getNamespace();
    }

    /**
     * Register an {@link IExtensionPoint} with the mod container.
     * @param point The extension point to register
     * @param extension An extension operator
     * @param <T> The type signature of the extension operator
     */
    public <T extends Record & IExtensionPoint<T>> void registerExtensionPoint(Class<? extends IExtensionPoint<T>> point, Supplier<T> extension) {
        getActiveContainer().registerExtensionPoint(point, extension);
    }

    /**
     * @deprecated Use the corresponding method {@link ModContainer#registerConfig(ModConfig.Type, IConfigSpec)}
     */
    @Deprecated(forRemoval = true)
    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec) {
        getActiveContainer().registerConfig(type, spec);
    }

    /**
     * @deprecated Use the corresponding method {@link ModContainer#registerConfig(ModConfig.Type, IConfigSpec, String)}
     */
    @Deprecated(forRemoval = true)
    public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec, String fileName) {
        getActiveContainer().registerConfig(type, spec, fileName);
    }


    @SuppressWarnings("unchecked")
    public <T> T extension() {
        return (T)languageExtension;
    }
}
