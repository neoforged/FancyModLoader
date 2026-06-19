/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;
import net.neoforged.fml.earlydisplay.render.backend.ELSBuffer;
import net.neoforged.fml.earlydisplay.render.backend.ELSRenderBackend;
import net.neoforged.fml.earlydisplay.render.backend.VertexFormat;
import net.neoforged.fml.earlydisplay.theme.ThemeColor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * A very simple, Mojang inspired BufferBuilder.
 * <em>This has been customized for 2d rendering such as text and simple planar textures</em>
 * <p>
 * Not bound to any specific format, ideally should be held onto for re-use.
 * <p>
 * This is a Triangles only buffer, all data uploaded is in Triangles.
 * Quads are converted to triangles using {@code 0, 1, 2, 0, 2, 3}.
 * <p>
 * Any given {@link VertexFormat} should have its individual {@link VertexFormat.Element} components
 * buffered in the order specified by the {@link VertexFormat},
 * followed by an {@link #endVertex()} call to prepare for the next vertex.
 * <p>
 * It is illegal to buffer primitives in any format other than the one specified to
 * {@link #begin(VertexFormat, VertexFormat.Mode)}.
 *
 * @author covers1624
 */
public class SimpleBufferBuilder implements Closeable {
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);
    private static final Set<ELSBuffer.Usage> BUFFER_USAGE = EnumSet.of(ELSBuffer.Usage.VERTEX);

    private final String label;
    private long bufferAddr;   // Pointer to the backing buffer.
    private ByteBuffer buffer; // ByteBuffer view of the backing buffer.
    private ELSBuffer gpuBuffer; // GPU-side buffer
    private long gpuBufferOffset;
    private VertexFormat format;     // The current format we are buffering.
    private VertexFormat.Mode mode;         // The current mode we are buffering.
    private boolean building;  // If we are building the buffer.
    private int elementIndex;  // The current element index we are buffering. if elementIndex == format.types.length, we expect 'endVertex'
    private int index;         // The current index into the buffer we are writing to.
    private int vertices;      // The number of complete vertices we have buffered.

    /**
     * Create a new SimpleBufferBuilder with an initial capacity.
     * <p>
     * The buffer will be doubled as required.
     * <p>
     * Generally picking a small number, around 128/256 should be a
     * safe bet. Provided you cache your buffers, it should not mean much overall.
     *
     * @param capacity The initial capacity in bytes.
     */
    public SimpleBufferBuilder(String label, int capacity) {
        this.label = label;
        bufferAddr = ALLOCATOR.malloc(capacity);
        buffer = MemoryUtil.memByteBuffer(bufferAddr, capacity);
    }

    /**
     * Start building a new set of vertex data in the
     * given format and mode.
     *
     * @param format The format to start building in.
     * @param mode   The mode to start building in.
     */
    public SimpleBufferBuilder begin(VertexFormat format, VertexFormat.Mode mode) {
        if (bufferAddr == MemoryUtil.NULL) {
            throw new IllegalStateException("Buffer has been freed."); // You already free'd the buffer
        }
        if (building) {
            throw new IllegalStateException("Already building."); // Your already building verticies.
        }
        this.format = format;
        this.mode = mode;
        building = true;
        elementIndex = 0;
        ensureSpace(format.stride);
        // Rewind ready for new data.
        buffer.rewind();
        buffer.limit(buffer.capacity());
        return this;
    }

    /**
     * Buffer a position element.
     *
     * @param x The x.
     * @param y The y.
     * @return The same builder.
     */
    public SimpleBufferBuilder pos(float x, float y) {
        if (!building) {
            throw new IllegalStateException("Not building."); // You did not call begin.
        }
        if (elementIndex == format.elementCount()) {
            throw new IllegalStateException("Expected endVertex"); // we have reached the end of elements to buffer for this vertex, we expected an endVertex call.
        }
        if (format.element(elementIndex) != VertexFormat.Element.POS) {
            throw new IllegalArgumentException("Expected " + format.element(elementIndex)); // You called the wrong method for the format order.
        }

        // Assumes that our POS element specifies the FLOAT data type.
        buffer.putFloat(index + 0, x);
        buffer.putFloat(index + 4, y);

        // Increment index for the number of bytes we wrote and increment the element index.
        index += format.element(elementIndex).width;
        elementIndex++;
        return this;
    }

    /**
     * Buffer a texture element.
     *
     * @param u The u.
     * @param v The v.
     * @return The same builder.
     */
    public SimpleBufferBuilder tex(float u, float v) {
        if (!building) {
            throw new IllegalStateException("Not building."); // You did not call begin.
        }
        if (elementIndex == format.elementCount()) {
            throw new IllegalStateException("Expected endVertex"); // we have reached the end of elements to buffer for this vertex, we expected an endVertex call.
        }
        if (format.element(elementIndex) != VertexFormat.Element.TEX) {
            throw new IllegalArgumentException("Expected " + format.element(elementIndex)); // You called the wrong method for the format order.
        }

        // Assumes our TEX element specifies the FLOAT data type.
        buffer.putFloat(index + 0, u);
        buffer.putFloat(index + 4, v);

        // Increment index for the number of bytes we wrote and increment the element index.
        index += format.element(elementIndex).width;
        elementIndex++;
        return this;
    }

    /**
     * Buffer a color element.
     *
     * @param r The red component. (0-1)
     * @param g The green component. (0-1)
     * @param b The blue component. (0-1)
     * @param a The alpha component. (0-1)
     * @return The same buffer.
     */
    public SimpleBufferBuilder colour(float r, float g, float b, float a) {
        // Expand floats to 0-255 and forward.
        return colour((byte) (r * 255F), (byte) (g * 255F), (byte) (b * 255F), (byte) (a * 255F));
    }

    /**
     * @param packedColor an ARGB packed int
     * @return the same buffer.
     * @see ThemeColor#toArgb()
     */
    public SimpleBufferBuilder colour(int packedColor) {
        var color = ThemeColor.ofArgb(packedColor);
        return colour(color.r(), color.g(), color.b(), color.a());
    }

    /**
     * Buffer a color element.
     *
     * @param r The red component. (0-255)
     * @param g The green component. (0-255)
     * @param b The blue component. (0-255)
     * @param a The alpha component. (0-255)
     * @return The same buffer.
     */
    public SimpleBufferBuilder colour(byte r, byte g, byte b, byte a) {
        if (!building) {
            throw new IllegalStateException("Not building."); // You did not call begin.
        }
        if (elementIndex == format.elementCount()) {
            throw new IllegalStateException("Expected endVertex"); // we have reached the end of elements to buffer for this vertex, we expected an endVertex call.
        }
        if (format.element(elementIndex) != VertexFormat.Element.COLOR) {
            throw new IllegalArgumentException("Expected " + format.element(elementIndex)); // You called the wrong method for the format order.
        }

        // Assumes our COLOR element specifies the UNSIGNED_BYTE data type.
        buffer.put(index + 0, r);
        buffer.put(index + 1, g);
        buffer.put(index + 2, b);
        buffer.put(index + 3, a);

        // Increment index for the number of bytes we wrote and increment the element index.
        index += format.element(elementIndex).width;
        elementIndex++;
        return this;
    }

    /**
     * End building the current vertex and prepare for the next.
     *
     * @return The same builder.
     */
    public SimpleBufferBuilder endVertex() {
        if (!building) {
            throw new IllegalStateException("Not building."); // You did not call begin.
        }
        if (elementIndex != format.elementCount()) {
            throw new IllegalStateException("Expected " + format.element(elementIndex)); // You did not finish building the vertex.
        }

        // Reset elementIndex
        elementIndex = 0;
        // Increment the number of vertices we have so far buffered.
        vertices++;
        // Make sure there is space for the next vertex.
        ensureSpace(format.stride);
        return this;
    }

    // Checks there is enough space in the buffer for specified number of bytes.
    // If there is not enough space, the buffer is increased by 50%.
    private void ensureSpace(int newBytes) {
        int cap = buffer.capacity();
        if (index + newBytes > cap) {
            int newCap = Math.max(3 * cap / 2, 3 * newBytes / 2);
            bufferAddr = ALLOCATOR.realloc(bufferAddr, newCap);
            buffer = MemoryUtil.memByteBuffer(bufferAddr, newCap);
            buffer.rewind();
        }
    }

    /**
     * Upload the current buffer.
     * <p>
     * This will bind a {@link org.lwjgl.opengl.GL32C#GL_ARRAY_BUFFER} and {@link org.lwjgl.opengl.GL32C#GL_ELEMENT_ARRAY_BUFFER}
     * <p>
     * The vertex data and index data is uploaded to their respective buffers.
     * <p>
     * Uploading the buffers finishes drawing and resets for the next buffer operation.
     * <p>
     *
     * @return The number of indexes that were uploaded.
     */
    @Nullable
    public Result finishAndUpload(ELSRenderBackend backend) {
        if (!building) {
            throw new IllegalStateException("Not building.");
        }

        try {
            if (elementIndex == format.elementCount()) {
                throw new IllegalStateException("Expected endVertex"); // You didn't finish building your vertex.
            }
            if (elementIndex != 0) {
                throw new IllegalStateException("Not finished building vertex, Expected: " + format.element(elementIndex)); // You didn't finish building your vertex data.
            }
            if (vertices == 0) {
                return null; // No vertices buffered, lets not do anything.
            }
            if (vertices % mode.vertices != 0) {
                throw new IllegalStateException("Does not contain vertices aligned to " + mode); // You did not put in enough vertices to cleanly slice the data into TRIANGLES/QUADS
            }

            // Reset position to 0, limit the buffer to our index.
            buffer.position(0);
            buffer.limit(index);

            // Upload the raw vertex data in dynamic mode.
            long bufferSize = this.gpuBufferOffset + this.index;
            if (this.gpuBuffer == null || this.gpuBuffer.size() < bufferSize) {
                // expand buffer, it's not big enough
                long newVBOSize = Math.max(1024, this.gpuBuffer != null ? this.gpuBuffer.size() : 0);
                while (newVBOSize < bufferSize) {
                    newVBOSize *= 2;
                }
                ELSBuffer oldBuffer = this.gpuBuffer;
                this.gpuBuffer = backend.createBuffer(this.label, BUFFER_USAGE, newVBOSize);
                if (oldBuffer != null) {
                    backend.copyBufferToBuffer(oldBuffer, this.gpuBuffer);
                    oldBuffer.close();
                }
            }
            backend.writeToBuffer(this.gpuBuffer.slice(this.gpuBufferOffset, this.index), this.buffer);
            long resultOffset = this.gpuBufferOffset;
            this.gpuBufferOffset += this.index;

            // The number of indices for triangles is equal to our vertex count, as that is
            // what we operate in. However, for Quads, we have exactly vertices + vertices / 2
            // vertices once we convert the quads to triangles.
            int indices = mode == VertexFormat.Mode.TRIANGLES ? vertices : vertices + vertices / 2;

            return new Result(this.format, resultOffset, this.vertices, indices, this.mode == VertexFormat.Mode.QUADS);
        } finally {
            // Reset builder state for next begin call.
            building = false;
            vertices = 0;
            index = 0;
        }
    }

    public void endFrame() {
        this.gpuBufferOffset = 0L;
    }

    public ELSBuffer getGpuBuffer() {
        return this.gpuBuffer;
    }

    /**
     * Clear this builder's cached buffer.
     */
    @Override
    public void close() {
        ALLOCATOR.free(bufferAddr);
        bufferAddr = MemoryUtil.NULL;
    }

    public record Result(VertexFormat format, long vertexOffset, int vertexCount, int indexCount, boolean indexed) {}
}
