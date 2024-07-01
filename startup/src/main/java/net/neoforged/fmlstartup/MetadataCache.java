/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.neoforged.fmlstartup.api.FileCacheKey;

class MetadataCache {
    private static final String FILENAME = "fml_startup_metadata.bin";
    private final File cacheFile;
    private final Map<FileCacheKey, CachedMetadata> data;

    public MetadataCache(File cacheDir) {
        cacheFile = new File(cacheDir, FILENAME);
        StartupLog.debug("Loading metadata cache from {}", cacheFile);
        data = loadCache(cacheFile);
        StartupLog.debug("Loaded {} cache entries", data.size());
    }

    private static Map<FileCacheKey, CachedMetadata> loadCache(File cacheFile) {
        var expectedCacheSignature = generateCacheSignature();

        try (var in = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {
            var cacheSignature = in.readUTF();
            if (!expectedCacheSignature.equals(cacheSignature)) {
                StartupLog.info("Not using metadata cache {} due to signature difference: '{}' != '{}'", cacheFile, expectedCacheSignature, cacheSignature);
                return new HashMap<>();
            }

            var entryCount = in.readInt();
            var data = new HashMap<FileCacheKey, CachedMetadata>(entryCount);
            for (var i = 0; i < entryCount; i++) {
                var key = FileCacheKey.read(in);
                var value = readMetadata(in);
                data.put(key, value);
            }
            return data;
        } catch (FileNotFoundException ignored) {} catch (Exception e) {
            StartupLog.error("Failed to load metadata cache from {}: {}", cacheFile, e);
        }
        return new HashMap<>();
    }

    private static String generateCacheSignature() {
        return System.getProperty("java.version");
    }

    public CachedMetadata get(FileCacheKey key) {
        return data.get(key);
    }

    public void set(FileCacheKey key, CachedMetadata metadata) {
        data.put(key, metadata);
    }

    public void save() {
        try (var out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cacheFile)))) {
            out.writeUTF(generateCacheSignature());

            out.writeInt(data.size());
            for (var entry : data.entrySet()) {
                entry.getKey().write(out);
                writeMetadata(entry.getValue(), out);
            }
        } catch (IOException e) {
            System.err.println("Failed to store metadata cache at " + cacheFile + ": " + e);
        }
    }

    private static CachedMetadata readMetadata(DataInput in) throws IOException {
        var moduleName = in.readUTF();
        if (moduleName.isEmpty()) {
            moduleName = null;
        }

        var architectureCount = in.readInt();
        var onlyForArchitectures = new ArrayList<NativeArchitecture>(architectureCount);
        for (int i = 0; i < architectureCount; i++) {

            var os = in.readByte();
            var cpu = in.readByte();
            onlyForArchitectures.add(new NativeArchitecture(
                    NativeArchitectureOS.values()[os],
                    cpu == -1 ? null : NativeArchitectureCPU.values()[cpu]));
        }

        return new CachedMetadata(moduleName, onlyForArchitectures, in.readBoolean());
    }

    private static void writeMetadata(CachedMetadata metadata, DataOutput out) throws IOException {
        out.writeUTF(Objects.requireNonNullElse(metadata.moduleName(), ""));
        out.writeInt(metadata.nativeArchitectures().size());
        for (var nativeArch : metadata.nativeArchitectures()) {
            out.writeByte(nativeArch.os().ordinal());
            if (nativeArch.cpu() == null) {
                out.writeByte(-1);
            } else {
                out.writeByte(nativeArch.cpu().ordinal());
            }
        }
        out.writeBoolean(metadata.forceBootLayer());
    }
}
