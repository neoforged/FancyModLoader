package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.SecureJar;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import sun.security.util.SignatureFileVerifier;

import static org.junit.jupiter.api.Assertions.*;

public class TestSecureJarLoading {
    @Test
    void testLoadJar() throws Exception {
        final var path = Paths.get("forge-1.16.5-36.1.16.jar");
        SecureJar jar = SecureJar.from(path);
        try (var is = Files.newInputStream(path)) {
            ZipInputStream zis = new ZipInputStream(is);
            for (var ze = zis.getNextEntry(); ze!=null; ze=zis.getNextEntry()) {
                if (SignatureFileVerifier.isSigningRelated(ze.getName())) continue;
                if (ze.isDirectory()) continue;
                final var zeName = ze.getName();
                var cs = jar.verifyAndGetSigners(ze.getName(), zis.readAllBytes());
                assertAll("Behaves as a properly secured JAR",
                        ()->assertNotNull(cs, "Has code signers array"),
                        ()->assertTrue(cs.length>0, "With length > 0"),
                        ()->assertEquals("e3c3d50c7c986df74c645c0ac54639741c90a557", SecureJarVerifier.toHexString(MessageDigest.getInstance("SHA-1").digest(cs[0].getSignerCertPath().getCertificates().get(0).getEncoded())), "and the digest is correct for the code signer"),
                        ()->assertNotNull(jar.getTrustedManifestEntries(zeName), "Has trusted manifest entries")
                );
            }
        }
    }

    @Test
    void testInsecureJar() throws Exception {
        final var path = Paths.get("inventorysorter-1.16.1-18.0.0.jar");
        SecureJar jar = SecureJar.from(path);
        try (var is = Files.newInputStream(path)) {
            ZipInputStream zis = new ZipInputStream(is);
            for (var ze = zis.getNextEntry(); ze!=null; ze=zis.getNextEntry()) {
                if (SignatureFileVerifier.isSigningRelated(ze.getName())) continue;
                if (ze.isDirectory()) continue;
                final var zeName = ze.getName();
                var cs = jar.verifyAndGetSigners(ze.getName(), zis.readAllBytes());
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
                ()->assertTrue(jar.getManifest().getMainAttributes().isEmpty(), "Empty manifest returned")
        );
    }

    @Test
    void testNonExistent() throws Exception {
        final var path = Paths.get("thisdoesnotexist");
        assertThrows(UncheckedIOException.class, ()->SecureJar.from(path), "File does not exist");
    }

    @Test
    void testTampered() throws Exception {
        final var path = Paths.get("test.jar");
        SecureJar jar = SecureJar.from(path);
        ZipFile zf = new ZipFile(path.toFile());
        final var entry = zf.getEntry("META-INF/mods.toml");
        var cs = jar.verifyAndGetSigners("META-INF/mods.toml", zf.getInputStream(entry).readAllBytes());
        assertNull(cs);
    }

    @Test
    void testUntampered() throws Exception {
        final var path = Paths.get("Bookshelf-1.16.4-9.0.7-UNTAMPERED.jar");
        SecureJar jar = SecureJar.from(path);
        try (var is = Files.newInputStream(path)) {
            ZipInputStream zis = new ZipInputStream(is);
            for (var ze = zis.getNextEntry(); ze!=null; ze=zis.getNextEntry()) {
                if (SignatureFileVerifier.isSigningRelated(ze.getName())) continue;
                if (ze.isDirectory()) continue;
                final var zeName = ze.getName();
                var cs = jar.verifyAndGetSigners(ze.getName(), zis.readAllBytes());
                assertAll("Behaves as a properly secured JAR",
                        ()->assertNotNull(cs, "Has code signers array"),
                        ()->assertTrue(cs.length>0, "With length > 0"),
                        ()->assertEquals("1031a1aff3f542c507ea07d08cb6a1dc7da7a4d4", SecureJarVerifier.toHexString(MessageDigest.getInstance("SHA-1").digest(cs[0].getSignerCertPath().getCertificates().get(0).getEncoded())), "and the digest is correct for the code signer"),
                        ()->assertNotNull(jar.getTrustedManifestEntries(zeName), "Has trusted manifest entries")
                );
            }
        }
    }
}
