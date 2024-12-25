/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import java.io.IOException;
import java.lang.module.ModuleReader;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

public class EmptyModuleReader implements ModuleReader {
    @Override
    public Optional<URI> find(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public Stream<String> list() throws IOException {
        return Stream.empty();
    }

    @Override
    public void close() throws IOException {}
}
