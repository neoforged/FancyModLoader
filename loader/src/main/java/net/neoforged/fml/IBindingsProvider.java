/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigEvent;

public interface IBindingsProvider {
    IEventBus getNeoForgeBus();

    I18NParser getMessageParser();

    IConfigEvent.ConfigConfig getConfigConfiguration();
}
