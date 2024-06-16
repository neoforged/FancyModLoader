/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class MetadataCache {
    private static final String FILENAME = "fml_startup_metadata.bin";
    private final File cacheFile;
    private Map<FileCacheKey, CachedMetadata> data;

    public MetadataCache(File cacheDir) {
        cacheFile = new File(cacheDir, FILENAME);
        StartupLog.debug("Loading metadata cache from {}", cacheFile);
        try (var in = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {
            var entryCount = in.readInt();
            data = new HashMap<>(entryCount);
            for (var i = 0; i < entryCount; i++) {
                var key = FileCacheKey.read(in);
                var value = CachedMetadata.read(in);
                data.put(key, value);
            }
        } catch (FileNotFoundException ignored) {
        } catch (Exception e) {
            System.err.println("Failed to load metadata cache from " + cacheFile + ": " + e);
        }
        if (data == null) {
            data = new HashMap<>();
        }

        StartupLog.debug("Loaded {} cache entries", data.size());
    }

    public CachedMetadata get(FileCacheKey key) {
        return data.get(key);
    }

    public void set(FileCacheKey key, CachedMetadata metadata) {
        data.put(key, metadata);
    }

    public void save() {
        try (var out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)))) {
            out.writeInt(data.size());
            for (var entry : data.entrySet()) {
                entry.getKey().write(out);
                entry.getValue().write(out);
            }
        } catch (IOException e) {
            System.err.println("Failed to store metadata cache at " + cacheFile + ": " + e);
        }
    }
}

record FileCacheKey(String filename, long size, long lastModified) implements Serializable {
    FileCacheKey {
        filename = Objects.requireNonNull(filename, "filename");
    }

    static FileCacheKey read(DataInput in) throws IOException {
        return new FileCacheKey(in.readUTF(), in.readLong(), in.readLong());
    }

    void write(DataOutput out) throws IOException {
        out.writeUTF(filename);
        out.writeLong(size);
        out.writeLong(lastModified);
    }
}

record CachedMetadata(@Nullable String moduleName, boolean hasDiscoveryServices) implements Serializable {
    static CachedMetadata read(DataInput in) throws IOException {
        var moduleName = in.readUTF();
        if (moduleName.isEmpty()) {
            moduleName = null;
        }
        return new CachedMetadata(
                moduleName,
                in.readBoolean()
        );
    }

    void write(DataOutput out) throws IOException {
        out.writeUTF(Objects.requireNonNullElse(moduleName, ""));
        out.writeBoolean(hasDiscoveryServices);
    }
}
