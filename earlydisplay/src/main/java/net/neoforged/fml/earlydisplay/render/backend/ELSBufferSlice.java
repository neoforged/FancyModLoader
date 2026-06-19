package net.neoforged.fml.earlydisplay.render.backend;

public interface ELSBufferSlice {
    ELSBuffer buffer();

    long offset();

    long length();
}
