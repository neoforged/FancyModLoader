package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionFileSystemProvider;
import cpw.mods.niofs.union.UnionPath;
import sun.security.util.ManifestEntryVerifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.security.CodeSigner;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.*;

import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

public class Jar implements SecureJar {
    private static final CodeSigner[] EMPTY_CODESIGNERS = new CodeSigner[0];
    private static final UnionFileSystemProvider UFSP = (UnionFileSystemProvider) FileSystemProvider.installedProviders().stream().filter(fsp->fsp.getScheme().equals("union")).findFirst().orElseThrow(()->new IllegalStateException("Couldn't find UnionFileSystemProvider"));
    private final Manifest manifest;
    private final ManifestEntryVerifier mev;
    private final Hashtable<String, CodeSigner[]> pendingSigners = new Hashtable<>();
    private final Hashtable<String, CodeSigner[]> existingSigners = new Hashtable<>();
    private final boolean secure;
    private final Map<String, StatusData> statusData = new HashMap<>();
    private final JarMetadata metadata;
    private final UnionFileSystem filesystem;
    private final boolean isMultiRelease;
    private final Map<Path, Integer> nameOverrides;

    public URI getURI() {
        return this.filesystem.getRootDirectories().iterator().next().toUri();
    }

    public ModuleDescriptor computeDescriptor() {
        return metadata.descriptor();
    }

    @Override
    public Path getPrimaryPath() {
        return filesystem.getPrimaryPath();
    }

    @Override
    public Optional<URI> findFile(final String name) {
        var rel = filesystem.getPath(name);
        if (this.nameOverrides.containsKey(rel)) {
            rel = this.filesystem.getPath("META-INF", "versions", this.nameOverrides.get(rel).toString()).resolve(rel);
        }
        return Optional.of(this.filesystem.getRoot().resolve(rel)).filter(Files::exists).map(Path::toUri);
    }

    private record StatusData(String name, Status status, CodeSigner[] signers) {
        static void add(final String name, final Status status, final CodeSigner[] signers, Jar jar) {
            jar.statusData.put(name, new StatusData(name, status, signers));
        }
    }

    @SuppressWarnings("unchecked")
    public Jar(final Supplier<Manifest> defaultManifest, final Function<SecureJar, JarMetadata> metadataFunction, final Path... paths) {
        final var path = paths[paths.length-1];
        this.filesystem = UFSP.newFileSystem(paths);
        try {
            if (Files.isDirectory(path)) {
                var manfile = path.resolve(JarFile.MANIFEST_NAME);
                if (Files.exists(manfile))
                    try (var is = Files.newInputStream(manfile)) {
                        this.manifest = new Manifest(is);
                    }
                else
                    this.manifest = defaultManifest.get();
                this.mev = null;
                secure = false;
            } else {
                try (var jis = new JarInputStream(Files.newInputStream(path))) {
                    if (SecureJarVerifier.jarVerifier.get(jis) != null) {
                        while ((Boolean) SecureJarVerifier.parsingMeta.get(SecureJarVerifier.jarVerifier.get(jis))) {
                            jis.getNextJarEntry();
                        }
                        secure = SecureJarVerifier.anyToVerify.getBoolean(SecureJarVerifier.jarVerifier.get(jis));
                        this.manifest = new Manifest(jis.getManifest());
                        this.mev = new ManifestEntryVerifier(this.manifest);
                    } else {
                        secure = false;
                        this.manifest = defaultManifest.get();
                        this.mev = null;
                    }
                    if (secure) {
                        pendingSigners.putAll((Hashtable<String, CodeSigner[]>) SecureJarVerifier.sigFileSigners.get(SecureJarVerifier.jarVerifier.get(jis)));
                        existingSigners.put(JarFile.MANIFEST_NAME, ((Hashtable<String, CodeSigner[]>) SecureJarVerifier.existingSigners.get(SecureJarVerifier.jarVerifier.get(jis))).get(JarFile.MANIFEST_NAME));
                        StatusData.add(JarFile.MANIFEST_NAME, Status.VERIFIED, existingSigners.get(JarFile.MANIFEST_NAME), this);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.isMultiRelease = Boolean.parseBoolean(getManifest().getMainAttributes().getValue("Multi-Release"));
        if (this.isMultiRelease) {
            var vers = filesystem.getRoot().resolve("META-INF/versions");
            try (var walk = Files.walk(vers)){
                var allnames = walk.filter(p1 ->!p1.isAbsolute())
                        .filter(path1 -> !Files.isDirectory(path1))
                        .map(p1 -> p1.subpath(2, p1.getNameCount()))
                        .collect(groupingBy(p->p.subpath(1, p.getNameCount()),
                                mapping(p->Integer.parseInt(p.getName(0).toString()), toUnmodifiableList())));
                this.nameOverrides = allnames.entrySet().stream()
                        .map(e->Map.entry(e.getKey(), e.getValue().stream().reduce(Integer::max).orElse(8)))
                        .filter(e-> e.getValue() < Runtime.version().feature())
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        } else {
            this.nameOverrides = Map.of();
        }
        this.metadata = metadataFunction.apply(this);
    }

    @Override
    public Manifest getManifest() {
        return manifest;
    }

    @Override
    public CodeSigner[] getManifestSigners() {
        return getData(JarFile.MANIFEST_NAME).map(r->r.signers).orElse(null);
    }

    public synchronized CodeSigner[] verifyAndGetSigners(final String name, final byte[] bytes) {
        if (!secure) return null;
        if (statusData.containsKey(name)) return statusData.get(name).signers;
        setMEVName(name);
        mev.update(bytes, 0, bytes.length);
        try {
            final var codeSigners = mev.verify(existingSigners, pendingSigners);
            StatusData.add(name, Status.VERIFIED, codeSigners, this);
            return codeSigners;
        } catch (JarException | SecurityException e) {
            StatusData.add(name, Status.INVALID, null, this);
            return null;
        } finally {
            setMEVName(null);
        }
    }

    @Override
    public Status verifyPath(final Path path) {
        if (path.getFileSystem() != filesystem) throw new IllegalArgumentException("Wrong filesystem");
        final var pathname = path.toString();
        if (statusData.containsKey(pathname)) return getFileStatus(pathname);
        try {
            var bytes = Files.readAllBytes(path);
            verifyAndGetSigners(pathname, bytes);
            return getFileStatus(pathname);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void setMEVName(final String name) {
        try {
            this.mev.setEntry(name, null);
        } catch (IOException e) {
            // ignore - never thrown
        }
    }

    private Optional<StatusData> getData(final String name) {
        return Optional.ofNullable(statusData.get(name));
    }

    @Override
    public Status getFileStatus(final String name) {
        return secure ? getData(name).map(r->r.status).orElse(Status.NONE) : Status.UNVERIFIED;
    }

    @Override
    public Attributes getTrustedManifestEntries(final String name) {
        var manattrs = manifest.getAttributes(name);
        var mansigners = getManifestSigners();
        var objsigners = getData(name).map(sd->sd.signers).orElse(EMPTY_CODESIGNERS);
        if (mansigners == null || (mansigners.length == objsigners.length)) {
            return manattrs;
        } else {
            return null;
        }
    }
    @Override
    public boolean hasSecurityData() {
        return secure;
    }

    @Override
    public String name() {
        return metadata.name();
    }

    @Override
    public Set<String> getPackages() {
        try (var walk = Files.walk(this.filesystem.getRoot())) {
            return walk.filter(path -> !Files.isDirectory(path))
                    .filter(path->!path.getName(0).toString().equals("META-INF"))
                    .filter(path->path.getFileName().toString().endsWith(".class"))
                    .map(path->path.subpath(0, path.getNameCount()-1))
                    .map(path->path.toString().replace('/','.'))
                    .filter(pkg->pkg.length()!=0)
                    .collect(toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<Provider> getProviders() {
        final var services = this.filesystem.getRoot().resolve("META-INF/services/");
        if (Files.exists(services)) {
            try (var walk = Files.walk(services)) {
                return walk.filter(path->!Files.isDirectory(path))
                        .map(Provider::fromPath)
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return List.of();
        }
    }

    @Override
    public Path getPath(String first, String... rest) {
        return filesystem.getPath(first, rest);
    }

    @Override
    public Path getRootPath() {
        return filesystem.getRoot();
    }
}
