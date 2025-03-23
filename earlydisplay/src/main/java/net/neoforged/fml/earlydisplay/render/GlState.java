/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.render;

import static org.lwjgl.opengl.GL20C.glIsProgram;
import static org.lwjgl.opengl.GL32C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_BLEND;
import static org.lwjgl.opengl.GL32C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL32C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL32C.GL_COLOR_CLEAR_VALUE;
import static org.lwjgl.opengl.GL32C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL32C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL32C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL32C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL32C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL32C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL32C.GL_VIEWPORT;
import static org.lwjgl.opengl.GL32C.glActiveTexture;
import static org.lwjgl.opengl.GL32C.glBindBuffer;
import static org.lwjgl.opengl.GL32C.glBindFramebuffer;
import static org.lwjgl.opengl.GL32C.glBindTexture;
import static org.lwjgl.opengl.GL32C.glBindVertexArray;
import static org.lwjgl.opengl.GL32C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL32C.glClearColor;
import static org.lwjgl.opengl.GL32C.glDisable;
import static org.lwjgl.opengl.GL32C.glEnable;
import static org.lwjgl.opengl.GL32C.glGetFloatv;
import static org.lwjgl.opengl.GL32C.glGetInteger;
import static org.lwjgl.opengl.GL32C.glGetIntegerv;
import static org.lwjgl.opengl.GL32C.glIsEnabled;
import static org.lwjgl.opengl.GL32C.glUseProgram;
import static org.lwjgl.opengl.GL32C.glViewport;

import org.jetbrains.annotations.ApiStatus;

/**
 * A static state manager for a subset of OpenGL states to minimize redundant state changes.
 * <p>
 * This class tracks the current state of various OpenGL state elements and only applies changes
 * when necessary, reducing overhead from redundant state changes.
 */
@ApiStatus.Internal
public final class GlState {
    // Viewport state
    private static int viewportX;
    private static int viewportY;
    private static int viewportWidth;
    private static int viewportHeight;

    // Clear color state
    private static float clearColorRed;
    private static float clearColorGreen;
    private static float clearColorBlue;
    private static float clearColorAlpha;

    // Blend state
    private static boolean blendEnabled;
    private static int blendSrcRGB;
    private static int blendDstRGB;
    private static int blendSrcAlpha;
    private static int blendDstAlpha;

    // Program state
    private static int currentProgram;

    // Texture state
    private static int boundTexture2D;
    private static int activeTextureUnit;

    // Vertex array state
    private static int boundVertexArray;

    // Framebuffer states
    private static int boundDrawFramebuffer;
    private static int boundReadFramebuffer;

    // Buffer states
    private static int boundElementArrayBuffer;
    private static int boundArrayBuffer;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private GlState() {
        // This class should not be instantiated
    }

    /**
     * Reads the current OpenGL state into this state manager.
     */
    public static void readFromOpenGL() {
        // Read viewport state
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        viewportX = viewport[0];
        viewportY = viewport[1];
        viewportWidth = viewport[2];
        viewportHeight = viewport[3];

        // Read clear color state
        float[] clearColor = new float[4];
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        clearColorRed = clearColor[0];
        clearColorGreen = clearColor[1];
        clearColorBlue = clearColor[2];
        clearColorAlpha = clearColor[3];

        // Read blend state
        blendEnabled = glIsEnabled(GL_BLEND);
        blendSrcRGB = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRGB = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);

        // Read program state
        currentProgram = glGetInteger(GL_CURRENT_PROGRAM);

        // Read texture state
        activeTextureUnit = GL_TEXTURE0 + glGetInteger(GL_ACTIVE_TEXTURE) - GL_TEXTURE0;
        boundTexture2D = glGetInteger(GL_TEXTURE_BINDING_2D);

        // Read vertex array state
        boundVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);

        // Read framebuffer states
        boundDrawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        boundReadFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);

        // Read buffer states
        boundElementArrayBuffer = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        boundArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    }

    /**
     * Sets the viewport state.
     *
     * @param x      The lower left corner x-coordinate
     * @param y      The lower left corner y-coordinate
     * @param width  The viewport width
     * @param height The viewport height
     */
    public static void viewport(int x, int y, int width, int height) {
        if (x != viewportX || y != viewportY || width != viewportWidth || height != viewportHeight) {
            glViewport(x, y, width, height);
            viewportX = x;
            viewportY = y;
            viewportWidth = width;
            viewportHeight = height;
        }
    }

    /**
     * Sets the clear color state.
     *
     * @param red   The red component [0.0, 1.0]
     * @param green The green component [0.0, 1.0]
     * @param blue  The blue component [0.0, 1.0]
     * @param alpha The alpha component [0.0, 1.0]
     */
    public static void clearColor(float red, float green, float blue, float alpha) {
        if (red != clearColorRed || green != clearColorGreen ||
                blue != clearColorBlue || alpha != clearColorAlpha) {
            glClearColor(red, green, blue, alpha);
            clearColorRed = red;
            clearColorGreen = green;
            clearColorBlue = blue;
            clearColorAlpha = alpha;
        }
    }

    /**
     * Enables or disables blending.
     *
     * @param enabled Whether blending should be enabled
     */
    public static void enableBlend(boolean enabled) {
        if (enabled != blendEnabled) {
            if (enabled) {
                glEnable(GL_BLEND);
            } else {
                glDisable(GL_BLEND);
            }
            blendEnabled = enabled;
        }
    }

    /**
     * Sets the blend function parameters.
     *
     * @param srcRGB   The source RGB factor
     * @param dstRGB   The destination RGB factor
     * @param srcAlpha The source alpha factor
     * @param dstAlpha The destination alpha factor
     */
    public static void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (srcRGB != blendSrcRGB || dstRGB != blendDstRGB ||
                srcAlpha != blendSrcAlpha || dstAlpha != blendDstAlpha) {
            glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            blendSrcRGB = srcRGB;
            blendDstRGB = dstRGB;
            blendSrcAlpha = srcAlpha;
            blendDstAlpha = dstAlpha;
        }
    }

    /**
     * Sets the current shader program.
     *
     * @param program The program ID to use
     */
    public static void useProgram(int program) {
        if (program != currentProgram) {
            glUseProgram(program);
            currentProgram = program;
        }
    }

    /**
     * Sets the active texture unit.
     *
     * @param textureUnit The texture unit (e.g., GL_TEXTURE0)
     */
    public static void activeTexture(int textureUnit) {
        if (textureUnit != activeTextureUnit) {
            glActiveTexture(textureUnit);
            activeTextureUnit = textureUnit;
        }
    }

    /**
     * Binds a 2D texture.
     *
     * @param textureId The texture ID to bind
     */
    public static void bindTexture2D(int textureId) {
        if (textureId != boundTexture2D) {
            glBindTexture(GL_TEXTURE_2D, textureId);
            boundTexture2D = textureId;
        }
    }

    /**
     * Binds a vertex array object.
     *
     * @param vaoId The vertex array object ID to bind
     */
    public static void bindVertexArray(int vaoId) {
        if (vaoId != boundVertexArray) {
            glBindVertexArray(vaoId);
            boundVertexArray = vaoId;
        }
    }

    /**
     * Binds a framebuffer to both draw and read targets.
     *
     * @param framebufferId The framebuffer ID to bind to both GL_FRAMEBUFFER targets
     */
    public static void bindFramebuffer(int framebufferId) {
        if (framebufferId != boundDrawFramebuffer || framebufferId != boundReadFramebuffer) {
            glBindFramebuffer(GL_FRAMEBUFFER, framebufferId);
            boundDrawFramebuffer = framebufferId;
            boundReadFramebuffer = framebufferId;
        }
    }

    /**
     * Binds a framebuffer to the draw target.
     *
     * @param framebufferId The framebuffer ID to bind to GL_DRAW_FRAMEBUFFER
     */
    public static void bindDrawFramebuffer(int framebufferId) {
        if (framebufferId != boundDrawFramebuffer) {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebufferId);
            boundDrawFramebuffer = framebufferId;
        }
    }

    /**
     * Binds a framebuffer to the read target.
     *
     * @param framebufferId The framebuffer ID to bind to GL_READ_FRAMEBUFFER
     */
    public static void bindReadFramebuffer(int framebufferId) {
        if (framebufferId != boundReadFramebuffer) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebufferId);
            boundReadFramebuffer = framebufferId;
        }
    }

    /**
     * Binds a buffer to the element array buffer target.
     *
     * @param bufferId The buffer ID to bind
     */
    public static void bindElementArrayBuffer(int bufferId) {
        if (bufferId != boundElementArrayBuffer) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufferId);
            boundElementArrayBuffer = bufferId;
        }
    }

    /**
     * Binds a buffer to the array buffer target.
     *
     * @param bufferId The buffer ID to bind
     */
    public static void bindArrayBuffer(int bufferId) {
        if (bufferId != boundArrayBuffer) {
            glBindBuffer(GL_ARRAY_BUFFER, bufferId);
            boundArrayBuffer = bufferId;
        }
    }

    /**
     * A snapshot of the OpenGL state.
     */
    public record StateSnapshot(
            int viewportX, int viewportY, int viewportWidth, int viewportHeight,
            float clearColorRed, float clearColorGreen, float clearColorBlue, float clearColorAlpha,
            boolean blendEnabled,
            int blendSrcRGB, int blendDstRGB, int blendSrcAlpha, int blendDstAlpha,
            int currentProgram,
            int boundTexture2D, int activeTextureUnit,
            int boundVertexArray,
            int boundDrawFramebuffer, int boundReadFramebuffer,
            int boundElementArrayBuffer, int boundArrayBuffer) {}

    /**
     * Creates a snapshot of the current OpenGL state.
     *
     * @return A StateSnapshot object containing the current state
     */
    public static StateSnapshot createSnapshot() {
        return new StateSnapshot(
                viewportX, viewportY, viewportWidth, viewportHeight,
                clearColorRed, clearColorGreen, clearColorBlue, clearColorAlpha,
                blendEnabled,
                blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha,
                currentProgram,
                boundTexture2D, activeTextureUnit,
                boundVertexArray,
                boundDrawFramebuffer, boundReadFramebuffer,
                boundElementArrayBuffer, boundArrayBuffer);
    }

    /**
     * Applies the state from a snapshot to both this state manager and OpenGL.
     *
     * @param snapshot The snapshot to apply
     */
    public static void applySnapshot(StateSnapshot snapshot) {
        viewport(snapshot.viewportX, snapshot.viewportY, snapshot.viewportWidth, snapshot.viewportHeight);
        clearColor(snapshot.clearColorRed, snapshot.clearColorGreen, snapshot.clearColorBlue, snapshot.clearColorAlpha);
        enableBlend(snapshot.blendEnabled);
        blendFuncSeparate(snapshot.blendSrcRGB, snapshot.blendDstRGB, snapshot.blendSrcAlpha, snapshot.blendDstAlpha);
        // The program might have been flagged for deletion and may no longer be available
        if (glIsProgram(snapshot.currentProgram)) {
            useProgram(snapshot.currentProgram);
        } else {
            useProgram(0);
        }
        activeTexture(snapshot.activeTextureUnit);
        bindTexture2D(snapshot.boundTexture2D);
        bindVertexArray(snapshot.boundVertexArray);
        // Handle framebuffers - check if both are the same
        if (snapshot.boundDrawFramebuffer == snapshot.boundReadFramebuffer) {
            bindFramebuffer(snapshot.boundDrawFramebuffer);
        } else {
            bindDrawFramebuffer(snapshot.boundDrawFramebuffer);
            bindReadFramebuffer(snapshot.boundReadFramebuffer);
        }
        bindElementArrayBuffer(snapshot.boundElementArrayBuffer);
        bindArrayBuffer(snapshot.boundArrayBuffer);
    }
}
