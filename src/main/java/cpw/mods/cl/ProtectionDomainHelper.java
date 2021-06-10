package cpw.mods.cl;

import java.net.URL;
import java.security.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class ProtectionDomainHelper {
    private static final Map<URL, CodeSource> csCache = new HashMap<>();
    public static CodeSource createCodeSource(final URL url, final CodeSigner[] signers) {
        synchronized (csCache) {
            return csCache.computeIfAbsent(url, u->new CodeSource(url, signers));
        }
    }

    private static final Map<CodeSource, ProtectionDomain> pdCache = new HashMap<>();
    public static ProtectionDomain createProtectionDomain(CodeSource codeSource, ClassLoader cl) {
        synchronized (pdCache) {
            return pdCache.computeIfAbsent(codeSource, cs->{
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return new ProtectionDomain(codeSource, perms, cl, null);
            });
        }
    }

    static Package tryDefinePackage(final ClassLoader classLoader, String name, Manifest man, Function<String, Attributes> trustedEntries, Function<String[], Package> definePackage) throws IllegalArgumentException
    {
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
                return definePackage.apply(new String[]{pname, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor});
            }
        } else {
            return classLoader.getDefinedPackage(pname);
        }
    }

}
