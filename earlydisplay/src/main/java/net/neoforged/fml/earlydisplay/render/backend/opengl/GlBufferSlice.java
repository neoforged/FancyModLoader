package net.neoforged.fml.earlydisplay.render.backend.opengl;

import net.neoforged.fml.earlydisplay.render.backend.ELSBufferSlice;

record GlBufferSlice(GlBuffer buffer, long offset, long length) implements ELSBufferSlice {}
