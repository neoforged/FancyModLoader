/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.function.Supplier;

public class ModLoadingContext {
    private static final ThreadLocal<ModLoadingContext> context = ThreadLocal.withInitial(ModLoadingContext::new);

    public static ModLoadingContext get() {
        return context.get();
    }

    private ModContainer activeContainer;

    public void setActiveContainer(final ModContainer container) {
        this.activeContainer = container;
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
}
