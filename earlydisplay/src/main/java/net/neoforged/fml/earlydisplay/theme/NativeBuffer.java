package net.neoforged.fml.earlydisplay.theme;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class NativeBuffer implements AutoCloseable {
    private final ByteBuffer buffer;
    private final Consumer<ByteBuffer> deallocator;
    private final AtomicBoolean deallocated = new AtomicBoolean();

    public NativeBuffer(ByteBuffer buffer, Consumer<ByteBuffer> deallocator) {
        this.buffer = buffer;
        this.deallocator = deallocator;
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
