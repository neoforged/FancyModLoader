/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public record FileResource(File file) implements ThemeResource {
    public NativeBuffer toNativeBuffer() throws IOException {
        try (var fis = new FileInputStream(file)) {
            var channel = fis.getChannel();

            long size = channel.size();
            if (size > MAX_SIZE) {
                throw new IOException("The resource " + this + " exceeds the maximum size of " + MAX_SIZE);
            }

            // Allocate a ByteBuffer with the file size
            var buffer = ByteBuffer.allocateDirect((int) size).order(ByteOrder.nativeOrder());
            channel.read(buffer);
            buffer.flip();
            return new NativeBuffer(buffer, ignored -> {});
        }
    }
}
