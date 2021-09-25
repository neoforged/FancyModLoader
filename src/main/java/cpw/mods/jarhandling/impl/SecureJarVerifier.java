package cpw.mods.jarhandling.impl;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.CodeSigner;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarInputStream;

public class SecureJarVerifier {
    private static final boolean USE_UNSAAFE = Boolean.parseBoolean(System.getProperty("securejarhandler.useUnsafeAccessor", "true"));
    private static IAccessor ACCESSOR = USE_UNSAAFE ? new UnsafeAccessor() : new Reflection();

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

    public static Object getJarVerifier(Object inst) {
        return ACCESSOR.getJarVerifier(inst);
    }
    public static boolean isParsingMeta(Object inst) { return ACCESSOR.isParsingMeta(inst); }
    public static boolean hasSignatures(Object inst) { return ACCESSOR.hasSignatures(inst); }
    public static Map<String, CodeSigner[]> getVerifiedSigners(Object inst){ return ACCESSOR.getVerifiedSigners(inst); }
    public static Map<String, CodeSigner[]> getPendingSigners(Object inst){ return ACCESSOR.getPendingSigners(inst); }

    private interface IAccessor {
        Object getJarVerifier(Object inst);
        boolean isParsingMeta(Object inst);
        boolean hasSignatures(Object inst);
        Map<String, CodeSigner[]> getVerifiedSigners(Object inst);
        Map<String, CodeSigner[]> getPendingSigners(Object inst);
    }

    private static class Reflection implements IAccessor {
        private static final Field jarVerifier;
        private static final Field parsingMeta;
        private static final Field verifiedSigners;
        private static final Field sigFileSigners;
        private static final Field anyToVerify;

        static {
            final var moduleLayer = ModuleLayer.boot();
            final var myModule = moduleLayer.findModule("cpw.mods.securejarhandler");
            if (myModule.isPresent()) {
                final var gj9h = myModule.get();
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
                verifiedSigners = jarVerifier.getType().getDeclaredField("verifiedSigners");
                parsingMeta = jarVerifier.getType().getDeclaredField("parsingMeta");
                anyToVerify = jarVerifier.getType().getDeclaredField("anyToVerify");
                jarVerifier.setAccessible(true);
                sigFileSigners.setAccessible(true);
                verifiedSigners.setAccessible(true);
                parsingMeta.setAccessible(true);
                anyToVerify.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Missing essential fields", e);
            }
        }

        @Override public Object getJarVerifier(Object inst) {
            return getField(jarVerifier, inst);
        }
        @Override public boolean isParsingMeta(Object inst) { return (Boolean)getField(parsingMeta, inst); }
        @Override public boolean hasSignatures(Object inst) { return (Boolean)getField(anyToVerify, inst); }
        @SuppressWarnings("unchecked")
        @Override public Map<String, CodeSigner[]> getVerifiedSigners(Object inst){ return (Map<String, CodeSigner[]>)getField(verifiedSigners, inst); }
        @SuppressWarnings("unchecked")
        @Override public Map<String, CodeSigner[]> getPendingSigners(Object inst){ return (Map<String, CodeSigner[]>)getField(verifiedSigners, inst); }

        private static Object getField(Field f, Object inst) {
            try {
                return f.get(inst);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

    }

    private static class UnsafeAccessor implements IAccessor {
        private static final Unsafe UNSAFE;
        private static final Class<?> JV_TYPE;
        static {
            try {
                var f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                UNSAFE = (Unsafe)f.get(null);
                JV_TYPE = JarInputStream.class.getDeclaredField("jv").getType();
            } catch (Exception e) {
                throw new RuntimeException("Unable to get Unsafe reference, this should never be possible," +
                        " be sure to report this will exact details on what JVM you're running.", e);
            }
        }

        private static final long jarVerifier = getOffset(JarInputStream.class, "jv");
        private static final long sigFileSigners = getOffset(JV_TYPE, "sigFileSigners");
        private static final long verifiedSigners = getOffset(JV_TYPE, "verifiedSigners");
        private static final long parsingMeta = getOffset(JV_TYPE, "parsingMeta");
        private static final long anyToVerify = getOffset(JV_TYPE, "anyToVerify");

        private static long getOffset(Class<?> clz, String name) {
            try {
                return UNSAFE.objectFieldOffset(clz.getDeclaredField(name));
            } catch (Exception e) {
                throw new RuntimeException("Unable to get index for " + clz.getName() + "." + name + ", " +
                        " be sure to report this will exact details on what JVM you're running.", e);
            }
        }

        @Override public Object getJarVerifier(Object inst) { return UNSAFE.getObject(inst, jarVerifier); }
        @Override public boolean isParsingMeta(Object inst) { return UNSAFE.getBoolean(inst, parsingMeta); }
        @Override public boolean hasSignatures(Object inst) { return UNSAFE.getBoolean(inst, anyToVerify); }
        @SuppressWarnings("unchecked")
        @Override public Map<String, CodeSigner[]> getVerifiedSigners(Object inst) { return (Map<String, CodeSigner[]>)UNSAFE.getObject(inst, verifiedSigners); }
        @SuppressWarnings("unchecked")
        @Override public Map<String, CodeSigner[]> getPendingSigners(Object inst) { return (Map<String, CodeSigner[]>)UNSAFE.getObject(inst, sigFileSigners); }
    }
}
