/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.modscan.Scanner;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageProvider;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileSource;
import net.neoforged.neoforgespi.locating.ModFileInfoParser;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ModFile implements IModFile {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String jarVersion;
    private final ModFileInfoParser parser;
    @Nullable
    private final IModFile parent;
    private Map<String, Object> fileProperties;
    private List<IModLanguageProvider> loaders;
    private Throwable scanError;
    private final SecureJar jar;
    private final Type modFileType;
    private final Manifest manifest;
    private final IModFileSource source;
    private IModFileInfo modFileInfo;
    private ModFileScanData fileModFileScanData;
    private volatile CompletableFuture<ModFileScanData> futureScanResult;
    private List<CoreModFile> coreMods;
    private List<String> mixinConfigs;
    private List<Path> accessTransformers;

    public static final Attributes.Name TYPE = new Attributes.Name("FMLModType");
    private SecureJar.Status securityStatus;

    public ModFile(SecureJar jar, IModFileSource source, final ModFileInfoParser parser, @Nullable IModFile parent) {
        this(jar, source, parser, parseType(jar), parent);
    }

    public ModFile(SecureJar jar, IModFileSource source, ModFileInfoParser parser, Type type, @Nullable IModFile parent) {
        this.source = source;
        this.jar = jar;
        this.parser = parser;
        this.parent = parent;

        manifest = this.jar.moduleDataProvider().getManifest();
        modFileType = type;
        jarVersion = Optional.ofNullable(manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION)).orElse("0.0NONE");
        this.modFileInfo = ModFileParser.readModList(this, this.parser);
    }

    @Override
    public Supplier<Map<String, Object>> getSubstitutionMap() {
        return () -> ImmutableMap.<String, Object>builder().put("jarVersion", jarVersion).putAll(fileProperties).build();
    }

    @Override
    public Type getType() {
        return modFileType;
    }

    @Override
    public Path getFilePath() {
        return jar.getPrimaryPath();
    }

    @Override
    public SecureJar getSecureJar() {
        return this.jar;
    }

    @Override
    public List<IModInfo> getModInfos() {
        return modFileInfo.getMods();
    }

    public List<Path> getAccessTransformers() {
        return accessTransformers;
    }

    public boolean identifyMods() {
        this.modFileInfo = ModFileParser.readModList(this, this.parser);
        if (this.modFileInfo == null) return this.getType() != Type.MOD;
        LOGGER.debug(LogMarkers.LOADING, "Loading mod file {} with languages {}", this.getFilePath(), this.modFileInfo.requiredLanguageLoaders());
        this.coreMods = ModFileParser.getCoreMods(this);
        this.coreMods.forEach(mi -> LOGGER.debug(LogMarkers.LOADING, "Found coremod {}", mi.getDebugSource()));
        this.mixinConfigs = ModFileParser.getMixinConfigs(this.modFileInfo);
        this.mixinConfigs.forEach(mc -> LOGGER.debug(LogMarkers.LOADING, "Found mixin config {}", mc));
        this.accessTransformers = ModFileParser.getAccessTransformers(this.modFileInfo)
                .map(list -> list.stream().map(this::findResource).filter(path -> {
                    if (Files.notExists(path)) {
                        LOGGER.error(LogMarkers.LOADING, "Access transformer file {} provided by mod {} does not exist!", path, modFileInfo.moduleName());
                        return false;
                    }
                    return true;
                }))
                .orElseGet(() -> Stream.of(findResource("META-INF", "accesstransformer.cfg"))
                        .filter(Files::exists))
                .toList();
        return true;
    }

    public List<CoreModFile> getCoreMods() {
        return coreMods;
    }

    public List<String> getMixinConfigs() {
        return mixinConfigs;
    }

    /**
     * Run in an executor thread to harvest the class and annotation list
     */
    public ModFileScanData compileContent() {
        return new Scanner(this).scan();
    }

    public void scanFile(Consumer<Path> pathConsumer) {
        final Function<Path, SecureJar.Status> status = p -> getSecureJar().verifyPath(p);
        var rootPath = getSecureJar().getRootPath();
        try (Stream<Path> files = Files.find(rootPath, Integer.MAX_VALUE, (p, a) -> p.getNameCount() > 0 && p.getFileName().toString().endsWith(".class"))) {
            setSecurityStatus(files.peek(pathConsumer).map(status).reduce((s1, s2) -> SecureJar.Status.values()[Math.min(s1.ordinal(), s2.ordinal())]).orElse(SecureJar.Status.INVALID));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan " + rootPath, e);
        }
    }

    public void setFutureScanResult(CompletableFuture<ModFileScanData> future) {
        this.futureScanResult = future;
    }

    @Override
    public ModFileScanData getScanResult() {
        if (this.futureScanResult != null) {
            try {
                this.futureScanResult.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Caught unexpected exception processing scan results", e);
            }
        }
        if (this.scanError != null) {
            throw new RuntimeException(this.scanError);
        }
        return this.fileModFileScanData;
    }

    public void setScanResult(final ModFileScanData modFileScanData, final Throwable throwable) {
        this.fileModFileScanData = modFileScanData;
        if (throwable != null) {
            this.scanError = throwable;
        }
        this.futureScanResult = null;
    }

    public void setFileProperties(Map<String, Object> fileProperties) {
        this.fileProperties = fileProperties;
    }

    @Override
    public List<IModLanguageProvider> getLoaders() {
        if (loaders == null) {
            throw new IllegalStateException("Language loaders have not yet been identified");
        }
        return loaders;
    }

    @Override
    public Path findResource(String... path) {
        if (path.length < 1) {
            throw new IllegalArgumentException("Missing path");
        }
        return getSecureJar().getPath(String.join("/", path));
    }

    public void identifyLanguage() {
        this.loaders = this.modFileInfo.requiredLanguageLoaders().stream()
                .map(spec -> FMLLoader.getLanguageLoadingProvider().findLanguage(this, spec.languageName(), spec.acceptedVersions()))
                .toList();
    }

    @Override
    public String toString() {
        if (parent != null) {
            return "Nested Mod File " + this.jar.getPrimaryPath() + " in " + parent;
        } else {
            return "Mod File: " + this.jar.getPrimaryPath();
        }
    }

    @Override
    public String getFileName() {
        return getFilePath().getFileName().toString();
    }

    @Override
    public IModFileSource getSource() {
        return source;
    }

    @Nullable
    @Override
    public IModFile getParent() {
        return parent;
    }

    @Override
    public IModFileInfo getModFileInfo() {
        return modFileInfo;
    }

    @Override
    public void setSecurityStatus(final SecureJar.Status status) {
        this.securityStatus = status;
    }

    public ArtifactVersion getJarVersion() {
        return new DefaultArtifactVersion(this.jarVersion);
    }

    private static Type parseType(final SecureJar jar) {
        final Manifest m = jar.moduleDataProvider().getManifest();
        final Optional<String> value = Optional.ofNullable(m.getMainAttributes().getValue(TYPE));
        return value.map(Type::valueOf).orElse(Type.MOD);
    }
}
