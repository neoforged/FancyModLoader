/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.ELSBufferSlice;

record GlBufferSlice(GlBuffer buffer, long offset, long length) implements ELSBufferSlice {}
