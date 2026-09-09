/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import java.nio.ByteBuffer;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.macosx.ObjCRuntime;

/**
 * Sets the macOS application icon by sending messages directly to AppKit through the Objective-C runtime.
 *
 * <p>The native function, class, and selector handles are pointers, which LWJGL represents as {@code long} values.
 */
final class MacosApplicationIcon {
    // Objective-C method calls are dispatched through objc_msgSend(receiver, selector, arguments...)
    private static final long OBJC_MSG_SEND = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");

    // handles for the AppKit/Foundation classes used below
    private static final long NS_DATA = ObjCRuntime.objc_getClass("NSData");
    private static final long NS_IMAGE = ObjCRuntime.objc_getClass("NSImage");
    private static final long NS_APPLICATION = ObjCRuntime.objc_getClass("NSApplication");

    // selectors identify the Objective-C methods passed to objc_msgSend
    private static final long DATA_WITH_BYTES = ObjCRuntime.sel_registerName("dataWithBytes:length:");
    private static final long ALLOC = ObjCRuntime.sel_registerName("alloc");
    private static final long INIT_WITH_DATA = ObjCRuntime.sel_registerName("initWithData:");
    private static final long SHARED_APPLICATION = ObjCRuntime.sel_registerName("sharedApplication");
    private static final long SET_APPLICATION_ICON_IMAGE = ObjCRuntime.sel_registerName("setApplicationIconImage:");

    private MacosApplicationIcon() {}

    static void set(byte[] iconData) {
        // Copy the Java array into native memory so Objective-C can receive a stable pointer to its bytes
        ByteBuffer iconBuffer = MemoryUtil.memAlloc(iconData.length);
        try {
            // flip() resets the position after the write so memAddress() points to the first icon byte
            iconBuffer.put(iconData).flip();

            // Equivalent to: [NSData dataWithBytes:iconBuffer length:iconData.length]
            // NSData copies the bytes, so the native buffer only needs to live until this call returns
            long data = JNI.invokePPPPP(NS_DATA, DATA_WITH_BYTES, MemoryUtil.memAddress(iconBuffer), iconData.length, OBJC_MSG_SEND);

            // Equivalent to: [[NSImage alloc] initWithData:data]
            long image = JNI.invokePPPP(JNI.invokePPP(NS_IMAGE, ALLOC, OBJC_MSG_SEND), INIT_WITH_DATA, data, OBJC_MSG_SEND);

            // Equivalent to: [NSApplication sharedApplication]
            long application = JNI.invokePPP(NS_APPLICATION, SHARED_APPLICATION, OBJC_MSG_SEND);

            // Equivalent to: [application setApplicationIconImage:image]
            JNI.invokePPPV(application, SET_APPLICATION_ICON_IMAGE, image, OBJC_MSG_SEND);
        } finally {
            // Memory allocated by MemoryUtil is not managed by the Java garbage collector so we have to free it
            MemoryUtil.memFree(iconBuffer);
        }
    }
}
