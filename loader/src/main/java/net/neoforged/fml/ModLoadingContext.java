/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import com.mojang.logging.LogUtils;
import java.util.function.Supplier;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

public class ModLoadingContext {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<ModLoadingContext> context = ThreadLocal.withInitial(ModLoadingContext::new);
    private Object languageExtension;

    public static ModLoadingContext get() {
        return context.get();
    }

    private ModContainer activeContainer;

    public void setActiveContainer(final ModContainer container) {
        this.activeContainer = container;
        this.languageExtension = container == null ? null : container.contextExtension.get();
    }

    public ModContainer getActiveContainer() {
        return activeContainer == null ? ModList.get().getModContainerById("minecraft").orElseThrow(() -> new RuntimeException("Where is minecraft???!")) : activeContainer;
    }

    public String getActiveNamespace() {
        return activeContainer == null ? "minecraft" : activeContainer.getNamespace();
    }

    /**
     * Register an {@link IExtensionPoint} with the mod container.
     * 
     * @param point     The extension point to register
     * @param extension An extension operator
     * @param <T>       The type signature of the extension operator
     */
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> point, Supplier<T> extension) {
        getActiveContainer().registerExtensionPoint(point, extension);
    }

    @SuppressWarnings("unchecked")
    public <T> T extension() {
        return (T) languageExtension;
    }
}
