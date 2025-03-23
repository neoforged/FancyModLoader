package net.neoforged.fml.earlydisplay.theme;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public record ClasspathResource(String path) implements ThemeResource {
    public NativeBuffer toNativeBuffer() throws IOException {
        var resource = getClass().getClassLoader().getResource(path);
        if (resource == null) {
            throw new FileNotFoundException("Couldn't find classpath resource " + path);
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
}
