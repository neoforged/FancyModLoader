/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import ca.weblite.objc.Client;
import java.util.Base64;

final class MacosApplicationIcon {
    private MacosApplicationIcon() {}

    static void set(byte[] iconData) {
        String encodedIcon = Base64.getEncoder().encodeToString(iconData);
        Client objc = Client.getInstance();
        Object data = objc.sendProxy("NSData", "alloc").send("initWithBase64Encoding:", encodedIcon);
        Object image = objc.sendProxy("NSImage", "alloc").send("initWithData:", data);
        objc.sendProxy("NSApplication", "sharedApplication").send("setApplicationIconImage:", image);
    }
}
