package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.SecureJar;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * The signing data for a {@link Jar}.
 */
public class JarSigningData {
    private static final CodeSigner[] EMPTY_CODESIGNERS = new CodeSigner[0];

    private final Hashtable<String, CodeSigner[]> pendingSigners = new Hashtable<>();
    private final Hashtable<String, CodeSigner[]> verifiedSigners = new Hashtable<>();
    private final ManifestVerifier verifier = new ManifestVerifier();
    private final Map<String, StatusData> statusData = new HashMap<>();

    record StatusData(String name, SecureJar.Status status, CodeSigner[] signers) {
        static void add(final String name, final SecureJar.Status status, final CodeSigner[] signers, JarSigningData data) {
            data.statusData.put(name, new StatusData(name, status, signers));
        }
    }

    /**
     * Read signing data from a {@link JarInputStream}.
     * For now this is the only way of reading signing data.
     */
    void readJarSigningData(JarInputStream jis) throws IOException {
        var jv = SecureJarVerifier.getJarVerifier(jis);
        if (jv != null) {
            while (SecureJarVerifier.isParsingMeta(jv)) {
                if (jis.getNextJarEntry() == null) break;
            }

            if (SecureJarVerifier.hasSignatures(jv)) {
                pendingSigners.putAll(SecureJarVerifier.getPendingSigners(jv));
                var manifestSigners = SecureJarVerifier.getVerifiedSigners(jv).get(JarFile.MANIFEST_NAME);
                if (manifestSigners != null) verifiedSigners.put(JarFile.MANIFEST_NAME, manifestSigners);
                StatusData.add(JarFile.MANIFEST_NAME, SecureJar.Status.VERIFIED, verifiedSigners.get(JarFile.MANIFEST_NAME), this);
            }
        }
    }

    @Nullable
    CodeSigner[] getManifestSigners() {
        return getData(JarFile.MANIFEST_NAME).map(r->r.signers).orElse(null);
    }

    SecureJar.Status verifyPath(Manifest manifest, Path path, String filename) {
        if (statusData.containsKey(filename)) return getFileStatus(filename);
        try {
            var bytes = Files.readAllBytes(path);
            verifyAndGetSigners(manifest, filename, bytes);
            return getFileStatus(filename);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    SecureJar.Status getFileStatus(String name) {
        return hasSecurityData() ? getData(name).map(r->r.status).orElse(SecureJar.Status.NONE) : SecureJar.Status.UNVERIFIED;
    }

    @Nullable
    Attributes getTrustedManifestEntries(Manifest manifest, String name) {
        var manattrs = manifest.getAttributes(name);
        var mansigners = getManifestSigners();
        var objsigners = getData(name).map(sd->sd.signers).orElse(EMPTY_CODESIGNERS);
        if (mansigners == null || (mansigners.length == objsigners.length)) {
            return manattrs;
        } else {
            return null;
        }
    }

    boolean hasSecurityData() {
        return !pendingSigners.isEmpty() || !this.verifiedSigners.isEmpty();
    }

    private Optional<StatusData> getData(final String name) {
        return Optional.ofNullable(statusData.get(name));
    }

    @Nullable
    synchronized CodeSigner[] verifyAndGetSigners(Manifest manifest, String name, byte[] bytes) {
        if (!hasSecurityData()) return null;
        if (statusData.containsKey(name)) return statusData.get(name).signers;

        var signers = verifier.verify(manifest, pendingSigners, verifiedSigners, name, bytes);
        if (signers == null) {
            StatusData.add(name, SecureJar.Status.INVALID, null, this);
            return null;
        } else {
            var ret = signers.orElse(null);
            StatusData.add(name, SecureJar.Status.VERIFIED, ret, this);
            return ret;
        }
    }
}
