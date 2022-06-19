package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.SecureJar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TestSecureJarLoading {

    @BeforeAll
    static void setup() {
        //System.setProperty("securejarhandler.debugVerifier", "true");
        System.setProperty("securejarhandler.useUnsafeAccessor", "true");
    }

    @Test // All files are signed
    void testSecureJar() throws Exception {
        final var path = Paths.get("src", "test", "resources", "signed.zip");
        SecureJar jar = SecureJar.from(path);
        try (var is = Files.newInputStream(path)) {
            ZipInputStream zis = new ZipInputStream(is);
            for (var ze = zis.getNextEntry(); ze!=null; ze=zis.getNextEntry()) {
                if (SecureJarVerifier.isSigningRelated(ze.getName())) continue;
                if (ze.isDirectory()) continue;
                final var zeName = ze.getName();
                var cs = ((Jar)jar).verifyAndGetSigners(ze.getName(), zis.readAllBytes());
                jar.getTrustedManifestEntries(zeName);
                assertAll("Behaves as a properly secured JAR",
                        ()->assertNotNull(cs, "Has code signers array"),
                        ()->assertTrue(cs.length>0, "With length > 0"),
                        ()->assertEquals("8c6124aab9db357d6616492b40d0ea4cd6e4f3f8", SecureJarVerifier.toHexString(MessageDigest.getInstance("SHA-1").digest(cs[0].getSignerCertPath().getCertificates().get(0).getEncoded())), "and the digest is correct for the code signer"),
                        ()->assertNotNull(jar.getTrustedManifestEntries(zeName), "Has trusted manifest entries")
                );
            }
        }
    }

    @Test // Nothing is signed
    void testInsecureJar() throws Exception {
        final var path = Paths.get("src", "test", "resources", "unsigned.zip");
        SecureJar jar = SecureJar.from(path);
        try (var is = Files.newInputStream(path)) {
            ZipInputStream zis = new ZipInputStream(is);
            for (var ze = zis.getNextEntry(); ze!=null; ze=zis.getNextEntry()) {
                if (SecureJarVerifier.isSigningRelated(ze.getName())) continue;
                if (ze.isDirectory()) continue;
                final var zeName = ze.getName();
                var cs = ((Jar)jar).verifyAndGetSigners(ze.getName(), zis.readAllBytes());
                assertAll("Jar behaves correctly",
                        ()->assertNull(cs, "No code signers")
                );
            }
        }
    }

    @Test
    void testNotJar() throws Exception {
        final var path = Paths.get("build");
        SecureJar jar = SecureJar.from(path);
        assertAll(
                ()->assertFalse(jar.hasSecurityData(), "Jar is not marked secure"),
                ()->assertTrue(jar.moduleDataProvider().getManifest().getMainAttributes().isEmpty(), "Empty manifest returned")
        );
    }

    @Test
    void testNonExistent() throws Exception {
        final var path = Paths.get("thisdoesnotexist");
        assertThrows(UncheckedIOException.class, ()->SecureJar.from(path), "File does not exist");
    }

    @Test // Has a file that is signed, but modified
    void testTampered() throws Exception {
        final var path = Paths.get("src", "test", "resources", "tampered.zip");
        SecureJar jar = SecureJar.from(path);
        ZipFile zf = new ZipFile(path.toFile());
        final var entry = zf.getEntry("test/Signed.class");
        var cs = ((Jar)jar).verifyAndGetSigners(entry.getName(), zf.getInputStream(entry).readAllBytes());
        assertNull(cs);
    }

    @Test // Contained a signed file, as well as a unsigned file.
    void testPartial() throws Exception {
        final var path = Paths.get("src", "test", "resources", "partial.zip");
        SecureJar jar = SecureJar.from(path);
        ZipFile zf = new ZipFile(path.toFile());
        final var sentry = zf.getEntry("test/Signed.class");
        final var scs = ((Jar)jar).verifyAndGetSigners(sentry.getName(), zf.getInputStream(sentry).readAllBytes());
        assertAll("Behaves as a properly secured JAR",
                ()->assertNotNull(scs, "Has code signers array"),
                ()->assertTrue(scs.length>0, "With length > 0"),
                ()->assertEquals("8c6124aab9db357d6616492b40d0ea4cd6e4f3f8", SecureJarVerifier.toHexString(MessageDigest.getInstance("SHA-1").digest(scs[0].getSignerCertPath().getCertificates().get(0).getEncoded())), "and the digest is correct for the code signer"),
                ()->assertNotNull(jar.getTrustedManifestEntries(sentry.getName()), "Has trusted manifest entries")
        );
        final var uentry = zf.getEntry("test/UnSigned.class");
        final var ucs = ((Jar)jar).verifyAndGetSigners(uentry.getName(), zf.getInputStream(uentry).readAllBytes());
        assertNull(ucs);
    }

    @Test // Has a jar with only a manifest
    void testEmptyJar() throws Exception {
        final var path = Paths.get("src", "test", "resources", "empty.zip");
        SecureJar jar = SecureJar.from(path);
        try (var is = Files.newInputStream(path)) {
            ZipInputStream zis = new ZipInputStream(is);
            for (var ze = zis.getNextEntry(); ze!=null; ze=zis.getNextEntry()) {
                if (SecureJarVerifier.isSigningRelated(ze.getName())) continue;
                if (ze.isDirectory()) continue;
                final var zeName = ze.getName();
                var cs = ((Jar)jar).verifyAndGetSigners(ze.getName(), zis.readAllBytes());
                assertAll("Jar behaves correctly",
                        ()->assertNull(cs, "No code signers")
                );
            }
        }
    }
}
