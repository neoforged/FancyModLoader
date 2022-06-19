package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.Jar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public interface SecureJar {
    interface ModuleDataProvider {
        String name();
        ModuleDescriptor descriptor();
        URI uri();
        Optional<URI> findFile(String name);
        Optional<InputStream> open(final String name);

        Manifest getManifest();

        CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes);
    }

    ModuleDataProvider moduleDataProvider();

    Path getPrimaryPath();

    CodeSigner[] getManifestSigners();

    Status verifyPath(Path path);

    Status getFileStatus(String name);

    Attributes getTrustedManifestEntries(String name);

    boolean hasSecurityData();

    static SecureJar from(final Path... paths) {
        return from(jar -> JarMetadata.from(jar, paths), paths);
    }

    static SecureJar from(BiPredicate<String, String> filter, final Path... paths) {
        return from(jar->JarMetadata.from(jar, paths), filter, paths);
    }

    static SecureJar from(Function<SecureJar, JarMetadata> metadataSupplier, final Path... paths) {
        return from(Manifest::new, metadataSupplier, paths);
    }

    static SecureJar from(Function<SecureJar, JarMetadata> metadataSupplier, BiPredicate<String, String> filter, final Path... paths) {
        return from(Manifest::new, metadataSupplier, filter, paths);
    }

    static SecureJar from(Supplier<Manifest> defaultManifest, Function<SecureJar, JarMetadata> metadataSupplier, final Path... paths) {
        return from(defaultManifest, metadataSupplier, null, paths);
    }

    static SecureJar from(Supplier<Manifest> defaultManifest, Function<SecureJar, JarMetadata> metadataSupplier, BiPredicate<String, String> filter, final Path... paths) {
        return new Jar(defaultManifest, metadataSupplier, filter, paths);
    }

    Set<String> getPackages();

    List<Provider> getProviders();

    String name();

    Path getPath(String first, String... rest);

    Path getRootPath();

    record Provider(String serviceName, List<String> providers) {
        public static Provider fromPath(final Path path, final BiPredicate<String, String> pkgFilter) {
            final var sname = path.getFileName().toString();
            try {
                var entries = Files.readAllLines(path).stream()
                        .map(String::trim)
                        .filter(l->l.length() > 0 && !l.startsWith("#"))
                        .filter(p-> pkgFilter == null || pkgFilter.test(p.replace('.','/'), ""))
                        .toList();
                return new Provider(sname, entries);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    enum Status {
        NONE, INVALID, UNVERIFIED, VERIFIED
    }
}
