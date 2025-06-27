package cpw.mods.cl;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.URL;
import java.security.AllPermission;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class ProtectionDomainHelper {
    private static final Map<URL, CodeSource> csCache = new HashMap<>();

    public static CodeSource createCodeSource(final URL url, final CodeSigner[] signers) {
        synchronized (csCache) {
            return csCache.computeIfAbsent(url, u -> new CodeSource(url, signers));
        }
    }

    private static final Map<CodeSource, ProtectionDomain> pdCache = new HashMap<>();

    public static ProtectionDomain createProtectionDomain(CodeSource codeSource, ClassLoader cl) {
        synchronized (pdCache) {
            return pdCache.computeIfAbsent(codeSource, cs -> {
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return new ProtectionDomain(codeSource, perms, cl, null);
            });
        }
    }

    private static final VarHandle PKG_MODULE_HANDLE;
    static {
        try {
            // Obtain VarHandle for NamedPackage#module via trusted lookup
            var trustedLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            trustedLookupField.setAccessible(true);
            MethodHandles.Lookup trustedLookup = (MethodHandles.Lookup) trustedLookupField.get(null);

            Class<?> namedPackage = Class.forName("java.lang.NamedPackage");
            PKG_MODULE_HANDLE = trustedLookup.findVarHandle(namedPackage, "module", Module.class);
        } catch (Throwable t) {
            throw new RuntimeException("Error finding package module handle", t);
        }
    }

    static void trySetPackageModule(Package pkg, Module module) {
        // Ensure named packages are bound to their module of origin
        // Necessary for loading package-info classes
        Module value = (Module) PKG_MODULE_HANDLE.get(pkg);
        if (value == null || !value.isNamed()) {
            try {
                PKG_MODULE_HANDLE.set(pkg, module);
            } catch (Throwable t) {
                throw new RuntimeException("Error setting package module", t);
            }
        }
    }

    static Package tryDefinePackage(final ClassLoader classLoader, String name, Manifest man, Function<String, Attributes> trustedEntries, Function<String[], Package> definePackage) throws IllegalArgumentException {
        final var pname = name.substring(0, name.lastIndexOf('.'));
        if (classLoader.getDefinedPackage(pname) == null) {
            synchronized (classLoader) {
                if (classLoader.getDefinedPackage(pname) != null) return classLoader.getDefinedPackage(pname);

                String path = pname.replace('.', '/').concat("/");
                String specTitle = null, specVersion = null, specVendor = null;
                String implTitle = null, implVersion = null, implVendor = null;

                if (man != null) {
                    Attributes attr = trustedEntries.apply(path);
                    if (attr != null) {
                        specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
                        specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
                        specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
                        implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
                        implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                        implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
                    }
                    attr = man.getMainAttributes();
                    if (attr != null) {
                        if (specTitle == null) {
                            specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
                        }
                        if (specVersion == null) {
                            specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
                        }
                        if (specVendor == null) {
                            specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
                        }
                        if (implTitle == null) {
                            implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
                        }
                        if (implVersion == null) {
                            implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                        }
                        if (implVendor == null) {
                            implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
                        }
                    }
                }
                return definePackage.apply(new String[] { pname, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor });
            }
        } else {
            return classLoader.getDefinedPackage(pname);
        }
    }
}
