package cpw.mods.jarhandling.impl;

import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

class ManifestVerifier {
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("securejarhandler.debugVerifier", "false"));

    private static final Base64.Decoder BASE64D = Base64.getDecoder();
    private final Map<String, MessageDigest> HASHERS = new HashMap<>();
    private MessageDigest getHasher(String name) {
        return HASHERS.computeIfAbsent(name.toLowerCase(Locale.ENGLISH), k -> {
            try {
                return MessageDigest.getInstance(k);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void log(String line) {
        System.out.println(line);
    }

    /**
     * This is Dumb API, but it's a package private class so la-de-da!
     * return:
     *   null - Something went wrong, digests were not verified.
     *   Optional.empty() - No signatures to verify, missing *-Digest entry in manifest, or nobody signed that particular entry
     *   Optional.isPresent() - code signers!
     */
    Optional<CodeSigner[]> verify(final Manifest manifest, final Map<String, CodeSigner[]> pending,
                                  final Map<String, CodeSigner[]> verified, final String name, final byte[] data) {
        if (DEBUG)
            log("[SJH] Verifying: " + name);
        Attributes attr = manifest.getAttributes(name);
        if (attr == null) {
            if (DEBUG)
                log("[SJH]   No Manifest Entry");
            return Optional.empty();
        }

        record Expected(MessageDigest hash, byte[] value){};
        var expected = new ArrayList<Expected>();
        attr.forEach((k,v) -> {
            var key = k.toString();
            if (key.toLowerCase(Locale.ENGLISH).endsWith("-digest")) {
                var algo = key.substring(0, key.length() - 7);
                var hash = BASE64D.decode((String)v);
                expected.add(new Expected(getHasher(algo), hash));
            }
        });
        if (expected.isEmpty()) {
            if (DEBUG)
                log("[SJH]   No Manifest Hashes");
            return Optional.empty();
        }

        for (var exp : expected) {
            synchronized (exp.hash()) {
                exp.hash().reset();
                byte[] actual = exp.hash().digest(data);
                if (DEBUG) {
                    log("[SJH]   " + exp.hash().getAlgorithm() + " Expected: " + SecureJarVerifier.toHexString(exp.value()));
                    log("[SJH]   " + exp.hash().getAlgorithm() + " Actual:   " + SecureJarVerifier.toHexString(actual));
                }
                if (!Arrays.equals(exp.value(), actual)) {
                    if (DEBUG)
                        log("[SJH]   Failed: Invalid hashes");
                    return null;
                }
            }
        }

        var signers = pending.remove(name);
        if (signers != null)
            verified.put(name, signers);
        return Optional.ofNullable(signers);
    }
}
