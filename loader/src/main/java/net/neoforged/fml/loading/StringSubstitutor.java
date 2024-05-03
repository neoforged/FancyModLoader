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

@SuppressWarnings("deprecation")
public class StringSubstitutor {
    private static final Supplier<Map<String, String>> globals = Suppliers.memoize(() -> {
        final var globals = new HashMap<String, String>();
        globals.put("mcVersion", FMLLoader.versionInfo() == null ? null : FMLLoader.versionInfo().mcVersion());
        globals.put("neoForgeVersion", FMLLoader.versionInfo() == null ? null : FMLLoader.versionInfo().neoForgeVersion());
        return globals;
    });

    public static String replace(final String in, final ModFile file) {
        return new StrSubstitutor(getStringLookup(file)).replace(in);
    }

    private static StrLookup<String> getStringLookup(final ModFile file) {
        return new StrLookup<String>() {
            @Override
            public String lookup(String key) {
                final String[] parts = key.split("\\.");
                if (parts.length == 1) return key;
                final String pfx = parts[0];
                if ("global".equals(pfx)) {
                    return globals.get().get(parts[1]);
                } else if ("file".equals(pfx) && file != null) {
                    return String.valueOf(file.getSubstitutionMap().get().get(parts[1]));
                }
                return key;
            }
        };
    }
}
