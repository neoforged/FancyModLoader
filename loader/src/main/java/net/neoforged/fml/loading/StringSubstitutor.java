/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.google.common.base.Suppliers;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class StringSubstitutor {
    public static String replace(final String in, final ModFile file) {
        return new StrSubstitutor(getStringLookup(file)).replace(in);
    }

    private static StrLookup<String> getStringLookup(final ModFile file) {
        return new StrLookup<>() {
            @Override
            public String lookup(String key) {
                final String[] parts = key.split("\\.");
                if (parts.length == 1) return key;
                final String pfx = parts[0];
                if ("file".equals(pfx) && file != null) {
                    return String.valueOf(file.getSubstitutionMap().get().get(parts[1]));
                }
                return key;
            }
        };
    }
}
