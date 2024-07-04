/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModLoadingExceptionTest {
    @Test
    void getMessage() {
        var w = ModLoadingIssue.warning("fml.modloadingissue.brokenfile.unknown").withAffectedPath(Paths.get("XXXX"));
        var e1 = ModLoadingIssue.error("fml.modloadingissue.brokenfile").withAffectedPath(Paths.get("YYYY"));
        var e2 = ModLoadingIssue.error("Some untranslated text\nwhich has newlines in it");

        var message = new ModLoadingException(List.of(w, e1, e2)).getMessage();
        assertEquals("""
                Loading errors encountered:
                \t- File YYYY is not a valid mod file
                \t- Some untranslated text
                \t  which has newlines in it
                Loading warnings encountered:
                \t- File XXXX is not a valid mod file
                """, message);
    }
}
