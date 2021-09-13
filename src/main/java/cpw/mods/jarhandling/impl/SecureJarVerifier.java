package cpw.mods.jarhandling.impl;

import java.lang.reflect.Field;
import java.net.spi.URLStreamHandlerProvider;
import java.util.Locale;
import java.util.jar.JarInputStream;

public class SecureJarVerifier {
    static final Field jarVerifier;
    static final Field parsingMeta;
    static final Field existingSigners;
    static final Field sigFileSigners;
    static final Field anyToVerify;

    static {
        final var moduleLayer = ModuleLayer.boot();
        final var gj9hOptional = moduleLayer.findModule("cpw.mods.securejarhandler");
        if (gj9hOptional.isPresent()) {
            final var gj9h = gj9hOptional.get();
            moduleLayer
                    .findModule("java.base")
                    .filter(m-> m.isOpen("java.util.jar", gj9h) && m.isExported("sun.security.util", gj9h))
                    .orElseThrow(()->new IllegalStateException("""
                    Missing JVM arguments. Please correct your runtime profile and run again.
                        --add-opens java.base/java.util.jar=cpw.mods.securejarhandler
                        --add-exports java.base/sun.security.util=cpw.mods.securejarhandler"""));
        } else if (Boolean.parseBoolean(System.getProperty("securejarhandler.throwOnMissingModule", "true"))) {
            // Hack for JMH benchmark: in JMH, SecureJarHandler does not load as a module, but we add-open to all unnamed in the jvm args
            throw new RuntimeException("Failed to find securejarhandler module!");
        }
        try {
            jarVerifier = JarInputStream.class.getDeclaredField("jv");
            sigFileSigners = jarVerifier.getType().getDeclaredField("sigFileSigners");
            existingSigners = jarVerifier.getType().getDeclaredField("verifiedSigners");
            parsingMeta = jarVerifier.getType().getDeclaredField("parsingMeta");
            anyToVerify = jarVerifier.getType().getDeclaredField("anyToVerify");
            jarVerifier.setAccessible(true);
            sigFileSigners.setAccessible(true);
            existingSigners.setAccessible(true);
            parsingMeta.setAccessible(true);
            anyToVerify.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Missing essential fields", e);
        }
    }

    private static final char[] LOOKUP = "0123456789abcdef".toCharArray();

    public static String toHexString(final byte[] bytes) {
        final var buffer = new StringBuffer(2*bytes.length);
        for (int i = 0, bytesLength = bytes.length; i < bytesLength; i++) {
            final int aByte = bytes[i] &0xff;
            buffer.append(LOOKUP[(aByte&0xf0)>>4]);
            buffer.append(LOOKUP[aByte&0xf]);
        }
        return buffer.toString();
    }

    //https://docs.oracle.com/en/java/javase/16/docs/specs/jar/jar.html#signed-jar-file
    public static boolean isSigningRelated(String path) {
        String filename = path.toLowerCase(Locale.ENGLISH);
        if (!filename.startsWith("meta-inf/")) // Must be in META-INF directory
            return false;
        filename = filename.substring(9);
        if (filename.indexOf('/') != -1)  // Can't be a sub-directory
            return false;
        if ("manifest.mf".equals(filename) || // Main manifest, which has the file hashes
                filename.endsWith(".sf") ||       // Signature file, which has hashes of the entries in the manifest file
                filename.endsWith(".dsa") ||      // PKCS7 signature, DSA
                filename.endsWith(".rsa"))        // PKCS7 signature, SHA-256 + RSA
            return true;

        if (!filename.startsWith("sig-")) // Unspecifed signature format
            return false;

        int ext = filename.lastIndexOf('.');
        if (ext == -1) // No extension, aparently is ok
            return true;
        if (ext < filename.length() - 4) // Only 1-3 character {-4 because we're at the . char}
            return false;
        for (int x = ext + 1; x < filename.length(); x++) {
            char c = filename.charAt(x);
            if ((c < 'a' || c > 'z') && (c < '0' || c > '9')) // Must be alphanumeric
                return false;
        }
        return true;
    }
}
