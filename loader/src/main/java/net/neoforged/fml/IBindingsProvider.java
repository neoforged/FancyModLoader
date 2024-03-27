/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import net.neoforged.bus.api.IEventBus;

import java.util.function.Supplier;

public interface IBindingsProvider {
    Supplier<IEventBus> getForgeBusSupplier();
    Supplier<I18NParser> getMessageParser();
}
