package net.neoforged.fml.earlydisplay.render.backend;

public interface ELSTexture extends AutoCloseable {
    int width();

    int height();

    TextureFormat format();

    @Override
    void close();
}
