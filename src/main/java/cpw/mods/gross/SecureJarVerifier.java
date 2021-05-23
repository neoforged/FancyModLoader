package cpw.mods.gross;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.security.CodeSigner;
import java.util.Hashtable;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import sun.security.util.ManifestEntryVerifier;

public class SecureJarVerifier {

    private static final Field jarVerifier;
    private static final Field sigFileSigners;

    public static class SecureJar {
        private final Manifest manifest;
        private final ManifestEntryVerifier mev;
        private final Hashtable<String, CodeSigner[]> pendingSigners;
        private final Hashtable<String, CodeSigner[]> existingSigners;

        @SuppressWarnings("unchecked")
        public SecureJar(final JarFile jarFile) {
            try {
                // Force the manifest to be parsed by the jarfile
                jarFile.getInputStream(jarFile.entries().nextElement()).readAllBytes();
                this.manifest = new Manifest(jarFile.getManifest());
                this.mev = new ManifestEntryVerifier(this.manifest);
                pendingSigners = new Hashtable<>((Hashtable<String, CodeSigner[]>) sigFileSigners.get(jarVerifier.get(this.manifest)));
            } catch (IllegalAccessException | IOException e) {
                throw new RuntimeException(e);
            }
            existingSigners = new Hashtable<>();
            existingSigners.put(JarFile.MANIFEST_NAME, jarFile.getJarEntry(JarFile.MANIFEST_NAME).getCodeSigners());
        }

        public Manifest getManifest() {
            return manifest;
        }

        public synchronized CodeSigner[] computeSigners(final String name, final byte[] bytes) {
            try {
                mev.setEntry(name, null);
                mev.update(bytes, 0, bytes.length);
                final var codeSigners = mev.verify(existingSigners, pendingSigners);
                mev.setEntry(null, null);
                return codeSigners;
            } catch (IOException e) {
                // return null?
                throw new UncheckedIOException(e);
            }
        }
    }

    static {
        final var moduleLayer = ModuleLayer.boot();
        final var gj9h = moduleLayer.findModule("cpw.mods.grossjava9hacks").orElseThrow();
        moduleLayer.findModule("java.base").filter(m-> m.isOpen("java.util.jar", gj9h) && m.isExported("sun.security.util", gj9h)).orElseThrow(()->new IllegalStateException("""
                Missing JVM arguments. Please correct your runtime profile and run again.
                    --add-opens java.base/java.util.jar=cpw.mods.grossjava9hacks
                    --add-exports java.base/sun.security.util=cpw.mods.grossjava9hacks"""));
        try {
            jarVerifier = Manifest.class.getDeclaredField("jv");
            sigFileSigners = jarVerifier.getType().getDeclaredField("sigFileSigners");
            jarVerifier.setAccessible(true);
            sigFileSigners.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing essential fields", e);
        }
    }

    public static SecureJar from(final JarFile jarFile) {
        return new SecureJar(jarFile);
    }
}
