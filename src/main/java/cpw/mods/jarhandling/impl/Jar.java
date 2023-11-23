package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.util.LambdaExceptionUtils;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSigner;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class Jar implements SecureJar {
    private final JarContentsImpl contents;
    private final Manifest manifest;
    private final JarSigningData signingData;
    private final UnionFileSystem filesystem;

    private final JarModuleDataProvider moduleDataProvider;

    private final JarMetadata metadata;

    @Deprecated(forRemoval = true, since = "2.1.16")
    public Jar(final Supplier<Manifest> defaultManifest, final Function<SecureJar, JarMetadata> metadataFunction, final BiPredicate<String, String> pathfilter, final Path... paths) {
        this.contents = new JarContentsImpl(paths, defaultManifest, pathfilter);
        this.manifest = contents.getManifest();
        this.signingData = contents.signingData;
        this.filesystem = contents.filesystem;

        this.moduleDataProvider = new JarModuleDataProvider(this);
        this.metadata = metadataFunction.apply(this);
    }

    public Jar(JarContentsImpl contents, JarMetadata metadata) {
        this.contents = contents;
        this.manifest = contents.getManifest();
        this.signingData = contents.signingData;
        this.filesystem = contents.filesystem;

        this.moduleDataProvider = new JarModuleDataProvider(this);
        this.metadata = metadata;
    }

    public URI getURI() {
        return this.filesystem.getRootDirectories().iterator().next().toUri();
    }

    public ModuleDescriptor computeDescriptor() {
        return metadata.descriptor();
    }

    @Override
    public ModuleDataProvider moduleDataProvider() {
        return moduleDataProvider;
    }

    @Override
    public Path getPrimaryPath() {
        return filesystem.getPrimaryPath();
    }

    public Optional<URI> findFile(final String name) {
        return contents.findFile(name);
    }

    @Override
    @Nullable
    public CodeSigner[] getManifestSigners() {
        return signingData.getManifestSigners();
    }

    @Override
    public Status verifyPath(final Path path) {
        if (path.getFileSystem() != filesystem) throw new IllegalArgumentException("Wrong filesystem");
        final var pathname = path.toString();
        return signingData.verifyPath(manifest, path, pathname);
    }

    @Override
    public Status getFileStatus(final String name) {
        return signingData.getFileStatus(name);
    }

    @Override
    @Nullable
    public Attributes getTrustedManifestEntries(final String name) {
        return signingData.getTrustedManifestEntries(manifest, name);
    }

    @Override
    public boolean hasSecurityData() {
        return signingData.hasSecurityData();
    }

    @Override
    public String name() {
        return metadata.name();
    }

    @Override
    public Set<String> getPackages() {
        return contents.getPackages();
    }

    @Override
    public List<Provider> getProviders() {
        return contents.getMetaInfServices();
    }

    @Override
    public Path getPath(String first, String... rest) {
        return filesystem.getPath(first, rest);
    }

    @Override
    public Path getRootPath() {
        return filesystem.getPath("");
    }

    @Override
    public String toString() {
        return "Jar[" + getURI() + "]";
    }

    private record JarModuleDataProvider(Jar jar) implements ModuleDataProvider {
        @Override
        public String name() {
            return jar.name();
        }

        @Override
        public ModuleDescriptor descriptor() {
            return jar.computeDescriptor();
        }

        @Override
        public URI uri() {
            return jar.getURI();
        }

        @Override
        public Optional<URI> findFile(final String name) {
            return jar.findFile(name);
        }

        @Override
        public Optional<InputStream> open(final String name) {
            return jar.findFile(name).map(Paths::get).map(LambdaExceptionUtils.rethrowFunction(Files::newInputStream));
        }

        @Override
        public Manifest getManifest() {
            return jar.manifest;
        }

        @Override
        @Nullable
        public CodeSigner[] verifyAndGetSigners(final String cname, final byte[] bytes) {
            return jar.signingData.verifyAndGetSigners(jar.manifest, cname, bytes);
        }
    }
}
