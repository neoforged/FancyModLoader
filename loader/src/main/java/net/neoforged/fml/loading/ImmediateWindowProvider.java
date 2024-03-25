/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

/**
 * {@inheritDoc}
 *
 * @deprecated This interface exists for binary compatibility. New
 *             implementations should use {@link net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider}
 *             instead.
 */
@Deprecated(since = "2.0.16", forRemoval = true)
public interface ImmediateWindowProvider extends net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider
{ }
