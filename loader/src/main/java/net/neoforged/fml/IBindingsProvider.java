/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigEvent;

public interface IBindingsProvider {
    Supplier<IEventBus> getForgeBusSupplier();

    Supplier<I18NParser> getMessageParser();

    Supplier<IConfigEvent.ConfigConfig> getConfigConfiguration();
}
