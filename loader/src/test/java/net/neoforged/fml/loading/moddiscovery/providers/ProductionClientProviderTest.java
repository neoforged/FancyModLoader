/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.neoforged.fml.loading.MavenCoordinate;
import net.neoforged.fml.loading.moddiscovery.locators.ProductionClientProvider;
import org.junit.jupiter.api.Test;

class ProductionClientProviderTest {
    @Test
    void testToString() {
        var provider = new ProductionClientProvider(List.of());
        assertEquals("production client provider", provider.toString());

        var providerPlus1 = new ProductionClientProvider(List.of(MavenCoordinate.parse("g:a:v")));
        assertEquals("production client provider +g:a:v", providerPlus1.toString());

        var providerPlus2 = new ProductionClientProvider(List.of(MavenCoordinate.parse("g:a:v"), MavenCoordinate.parse("g:a:c:v")));
        assertEquals("production client provider +g:a:v +g:a:c:v", providerPlus2.toString());
    }
}
