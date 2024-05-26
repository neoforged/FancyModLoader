/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModLoadingExceptionTest {
    @Test
    void getMessage() {
        var w = ModLoadingIssue.warning("fml.modloading.brokenfile.unknown", "XXXX");
        var e = ModLoadingIssue.error("fml.modloading.brokenfile", "YYYY");

        var message = new ModLoadingException(List.of(w, e)).getMessage();
        assertEquals("""
                Loading errors encountered:
                \tFile YYYY is not a valid mod file
                Loading warnings encountered:
                \tFile XXXX is not a valid mod file
                """, message);
    }
}
