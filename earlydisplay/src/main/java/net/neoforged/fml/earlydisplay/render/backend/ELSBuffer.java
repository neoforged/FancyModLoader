package net.neoforged.fml.earlydisplay.render.backend;

import java.util.Set;

public interface ELSBuffer extends AutoCloseable {
    Set<Usage> usage();

    long size();

    ELSBufferSlice slice();

    ELSBufferSlice slice(long offset, long length);

    @Override
    void close();

    enum Usage {
        MAP_READ,
        MAP_WRITE,
        HINT_CLIENT_STORAGE,
        COPY_DST,
        COPY_SRC,
        VERTEX,
        INDEX,
        UNIFORM,
    }
}
