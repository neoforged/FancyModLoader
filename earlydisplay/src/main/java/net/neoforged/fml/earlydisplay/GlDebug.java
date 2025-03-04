/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import net.neoforged.fml.loading.FMLConfig;
import org.lwjgl.opengl.EXTDebugLabel;
import org.lwjgl.opengl.EXTDebugMarker;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GlDebug {
    private static final Logger LOG = LoggerFactory.getLogger(GlDebug.class);

    private GlDebug() {}

    private enum LabelMode {
        DISABLED,
        CORE,
        EXTENSION
    }

    private enum GroupMode {
        DISABLED,
        CORE,
        EXTENSION
    }

    private static LabelMode labelMode = LabelMode.DISABLED;

    private static GroupMode groupMode = GroupMode.DISABLED;

    private static int maxLabelLength;

    public static void setCapabilities(GLCapabilities capabilities) {
        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_GLDEBUG)) {
            return;
        }

        // Setup labeling and markers/groups
        if (capabilities.GL_KHR_debug) {
            labelMode = LabelMode.CORE;
            groupMode = GroupMode.CORE;
            maxLabelLength = GL32C.glGetInteger(KHRDebug.GL_MAX_LABEL_LENGTH);
        } else {
            maxLabelLength = 256;
            if (capabilities.GL_EXT_debug_label) {
                labelMode = LabelMode.EXTENSION;
            }
            if (capabilities.GL_EXT_debug_marker) {
                groupMode = GroupMode.EXTENSION;
            }
        }

        // Setup debug message callbacks, we do not support ARB debug, although it'd have the exact same interface
        if (capabilities.GL_KHR_debug) {
            KHRDebug.glDebugMessageCallback(GlDebug::khrDebugMessage, 0L);
            GL32C.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            GL32C.glEnable(KHRDebug.GL_DEBUG_OUTPUT);
        }
    }

    private static void khrDebugMessage(int source, int type, int id, int severity, int length, long message, long userParam) {
        var sourceText = switch (source) {
            case KHRDebug.GL_DEBUG_SOURCE_API -> "API";
            case KHRDebug.GL_DEBUG_SOURCE_WINDOW_SYSTEM -> "WINDOW_SYSTEM";
            case KHRDebug.GL_DEBUG_SOURCE_SHADER_COMPILER -> "SHADER_COMPILER";
            case KHRDebug.GL_DEBUG_SOURCE_THIRD_PARTY -> "THIRD_PARTY";
            case KHRDebug.GL_DEBUG_SOURCE_APPLICATION -> "APPLICATION";
            case KHRDebug.GL_DEBUG_SOURCE_OTHER -> "OTHER";
            default -> "UNKNOWN";
        };

        var typeText = switch (type) {
            case KHRDebug.GL_DEBUG_TYPE_ERROR -> "ERROR";
            case KHRDebug.GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR -> "DEPRECATED_BEHAVIOR";
            case KHRDebug.GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR -> "UNDEFINED_BEHAVIOR";
            case KHRDebug.GL_DEBUG_TYPE_PORTABILITY -> "PORTABILITY";
            case KHRDebug.GL_DEBUG_TYPE_PERFORMANCE -> "PERFORMANCE";
            case KHRDebug.GL_DEBUG_TYPE_MARKER -> "MARKER";
            case KHRDebug.GL_DEBUG_TYPE_PUSH_GROUP -> "PUSH_GROUP";
            case KHRDebug.GL_DEBUG_TYPE_POP_GROUP -> "POP_GROUP";
            case KHRDebug.GL_DEBUG_TYPE_OTHER -> "OTHER";
            default -> "UNKNOWN";
        };

        var severityText = switch (severity) {
            case KHRDebug.GL_DEBUG_SEVERITY_HIGH -> "HIGH";
            case KHRDebug.GL_DEBUG_SEVERITY_MEDIUM -> "MEDIUM";
            case KHRDebug.GL_DEBUG_SEVERITY_LOW -> "LOW";
            case KHRDebug.GL_DEBUG_SEVERITY_NOTIFICATION -> "NOTIFICATION";
            default -> "UNKNOWN";
        };

        String messageText = GLDebugMessageCallback.getMessage(length, message);
        if (severity == KHRDebug.GL_DEBUG_SEVERITY_HIGH) {
            LOG.error("OpenGL message from {} of type {}: {}", sourceText, typeText, messageText);
        } else if (severity == KHRDebug.GL_DEBUG_SEVERITY_MEDIUM) {
            LOG.warn("OpenGL message from {} of type {}: {}", sourceText, typeText, messageText);
        } else {
            LOG.debug("OpenGL message from {} of type {} and severity {}: {}", sourceText, typeText, severityText, messageText);
        }
    }

    public static void pushGroup(String group) {
        switch (groupMode) {
            case CORE -> KHRDebug.glPushDebugGroup(KHRDebug.GL_DEBUG_SOURCE_APPLICATION, 0, truncate(group));
            case EXTENSION -> EXTDebugMarker.glPushGroupMarkerEXT(group);
        }
    }

    public static void popGroup() {
        switch (groupMode) {
            case CORE -> KHRDebug.glPopDebugGroup();
            case EXTENSION -> EXTDebugMarker.glPopGroupMarkerEXT();
        }
    }

    public static void labelBuffer(int id, String label) {
        switch (labelMode) {
            case CORE -> KHRDebug.glObjectLabel(KHRDebug.GL_BUFFER, id, truncate(label));
            case EXTENSION -> EXTDebugLabel.glLabelObjectEXT(EXTDebugLabel.GL_BUFFER_OBJECT_EXT, id, truncate(label));
        }
    }

    public static void labelTexture(int id, String label) {
        switch (labelMode) {
            case CORE -> KHRDebug.glObjectLabel(GL32C.GL_TEXTURE, id, truncate(label));
            case EXTENSION -> EXTDebugLabel.glLabelObjectEXT(GL32C.GL_TEXTURE, id, truncate(label));
        }
    }

    public static void labelFramebuffer(int id, String label) {
        switch (labelMode) {
            case CORE -> KHRDebug.glObjectLabel(GL32C.GL_FRAMEBUFFER, id, truncate(label));
            case EXTENSION -> EXTDebugLabel.glLabelObjectEXT(GL32C.GL_FRAMEBUFFER, id, truncate(label));
        }
    }

    public static void labelShader(int id, String label) {
        switch (labelMode) {
            case CORE -> KHRDebug.glObjectLabel(KHRDebug.GL_SHADER, id, truncate(label));
            case EXTENSION -> EXTDebugLabel.glLabelObjectEXT(EXTDebugLabel.GL_PROGRAM_OBJECT_EXT, id, truncate(label));
        }
    }

    public static void labelProgram(int id, String label) {
        switch (labelMode) {
            case CORE -> KHRDebug.glObjectLabel(KHRDebug.GL_PROGRAM, id, truncate(label));
            case EXTENSION -> EXTDebugLabel.glLabelObjectEXT(EXTDebugLabel.GL_PROGRAM_OBJECT_EXT, id, truncate(label));
        }
    }

    public static void labelVertexArray(int id, String label) {
        switch (labelMode) {
            case CORE -> KHRDebug.glObjectLabel(GL32C.GL_VERTEX_ARRAY, id, truncate(label));
            case EXTENSION -> EXTDebugLabel.glLabelObjectEXT(EXTDebugLabel.GL_VERTEX_ARRAY_OBJECT_EXT, id, truncate(label));
        }
    }

    private static CharSequence truncate(String label) {
        if (label.length() > maxLabelLength) {
            return label.substring(0, maxLabelLength - 3) + "...";
        }
        return label;
    }
}
