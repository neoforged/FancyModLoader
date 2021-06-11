package cpw.mods.jarhandling;

import cpw.mods.jarhandling.impl.Jar;
import cpw.mods.jarhandling.impl.SimpleJarMetadata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public interface SecureJar {
    Optional<URI> findFile(String name);

    Manifest getManifest();

    CodeSigner[] getManifestSigners();

    CodeSigner[] verifyAndGetSigners(String name, byte[] bytes);

    Status getFileStatus(String name);

    Attributes getTrustedManifestEntries(String name);

    boolean hasSecurityData();

    static SecureJar from(final Path... paths) {
        return new Jar(Manifest::new, jar -> JarMetadata.from(jar, paths), paths);
    }

    Set<String> getPackages();

    List<Provider> getProviders();

    String name();

    record Provider(String serviceName, List<String> providers) {
        public static Provider fromPath(final Path path) {
            final var sname = path.getFileName().toString();
            try {
                var entries = Files.readAllLines(path).stream()
                        .map(String::trim)
                        .filter(l->l.length() > 0 && !l.startsWith("#"))
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
