/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryUtil;

public final class NativeBuffer implements AutoCloseable {
    /**
     * Sanity check to stop going OOM instead of showing the loading screen.
     */
    private static final int MAX_SIZE = 100_000_000;

    private final ByteBuffer buffer;
    private final Consumer<ByteBuffer> deallocator;
    private final AtomicBoolean deallocated = new AtomicBoolean();

    public NativeBuffer(ByteBuffer buffer, Consumer<ByteBuffer> deallocator) {
        this.buffer = buffer;
        this.deallocator = deallocator;
    }

    public static NativeBuffer loadFromPath(Path path) throws IOException {
        try (var channel = Files.newByteChannel(path)) {
            long size = channel.size();
            if (size > MAX_SIZE) {
                throw new IOException("The resource " + path + " exceeds the maximum size of " + MAX_SIZE);
            }

            // Allocate a ByteBuffer with the file size
            var buffer = ByteBuffer.allocateDirect((int) size).order(ByteOrder.nativeOrder());
            channel.read(buffer);
            buffer.flip();
            return new NativeBuffer(buffer, ignored -> {});
        }
    }

    /**
     * @throws NoSuchFileException If the resource does not exist.
     */
    public static NativeBuffer loadFromClasspath(String path) throws IOException {
        var resource = NativeBuffer.class.getClassLoader().getResource(path);
        if (resource == null) {
            throw new NoSuchFileException("Couldn't find theme resource " + path);
        }

        var connection = resource.openConnection();
        try (var in = connection.getInputStream()) {
            var contentLengthHint = connection.getContentLength();
            if (contentLengthHint == -1) {
                contentLengthHint = 8 * 1024;
            }

            ByteBuffer buffer = MemoryUtil.memAlloc(contentLengthHint);
            byte[] tmp = new byte[8 * 1024];
            int read;

            while ((read = in.read(tmp)) != -1) {
                if (buffer.remaining() < read) {
                    buffer = MemoryUtil.memRealloc(buffer, buffer.capacity() * 2);
                }
                buffer.put(tmp, 0, read);
            }

            buffer.flip();

            return new NativeBuffer(buffer, MemoryUtil::memFree);
        }
    }

    public ByteBuffer buffer() {
        return buffer;
    }

    public byte[] toByteArray() {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        buffer.position(0);
        return data;
    }

    @Override
    public void close() {
        if (deallocated.compareAndSet(false, true)) {
            deallocator.accept(buffer);
        }
    }
}
