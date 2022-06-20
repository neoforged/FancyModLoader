package cpw.mods.cl;

import cpw.mods.util.LambdaExceptionUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleClassLoader extends ClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
        URL.setURLStreamHandlerFactory(ModularURLHandler.INSTANCE);
        ModularURLHandler.initFrom(ModuleClassLoader.class.getModule().getLayer());
    }

    private final Configuration configuration;
    private final Map<String, JarModuleFinder.JarModuleReference> resolvedRoots;
    private final Map<String, ResolvedModule> packageLookup;
    private final Map<String, ClassLoader> parentLoaders;
    private ClassLoader fallbackClassLoader = ClassLoader.getPlatformClassLoader();

    public ModuleClassLoader(final String name, final Configuration configuration, final List<ModuleLayer> parentLayers) {
        super(name, null);
        this.configuration = configuration;
        this.resolvedRoots = configuration.modules().stream()
                .map(ResolvedModule::reference)
                .filter(JarModuleFinder.JarModuleReference.class::isInstance)
                .collect(Collectors.toMap(r -> r.descriptor().name(), r -> (JarModuleFinder.JarModuleReference) r));

        this.packageLookup = new HashMap<>();
        for (var mod : this.configuration.modules()) {
            if (this.resolvedRoots.containsKey(mod.name())) {
                mod.reference().descriptor().packages().forEach(pk->this.packageLookup.put(pk, mod));
            }
        }

        this.parentLoaders = new HashMap<>();
        for (var rm : configuration.modules()) {
            for (var other : rm.reads()) {
                Supplier<ClassLoader> findClassLoader = ()->{
                    // Loading a class requires its module to be part of resolvedRoots
                    // If it's not, we delegate loading to its module's classloader
                    if (!this.resolvedRoots.containsKey(other.name())) {
                        return parentLayers.stream()
                                .filter(l -> l.configuration() == other.configuration())
                                .flatMap(layer->Optional.ofNullable(layer.findLoader(other.name())).stream())
                                .findFirst()
                                .orElse(ClassLoader.getPlatformClassLoader());
                    } else {
                        return ModuleClassLoader.this;
                    }
                };
                var cl = findClassLoader.get();
                final var descriptor = other.reference().descriptor();
                if (descriptor.isAutomatic()) {
                    descriptor.packages().forEach(pn->this.parentLoaders.put(pn, cl));
                } else {
                    descriptor.exports().stream()
                            .filter(e -> !e.isQualified() || (e.isQualified() && other.configuration() == configuration && e.targets().contains(rm.name())))
                            .map(ModuleDescriptor.Exports::source)
                            .forEach(pn->this.parentLoaders.put(pn, cl));
                }
            }
        }
    }

    private URL readerToURL(final ModuleReader reader, final ModuleReference ref, final String name) {
        try {
            return ModuleClassLoader.toURL(reader.find(name));
        } catch (IOException e) {
            return null;
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static URL toURL(final Optional<URI> uri) {
        if (uri.isPresent()) {
            try {
                return uri.get().toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return null;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Stream<InputStream> closeHandler(Optional<InputStream> supplier) {
        final var is = supplier.orElse(null);
        return Optional.ofNullable(is).stream().onClose(() -> Optional.ofNullable(is).ifPresent(LambdaExceptionUtils.rethrowConsumer(InputStream::close)));
    }
    protected byte[] getClassBytes(final ModuleReader reader, final ModuleReference ref, final String name) {
        var cname = name.replace('.','/')+".class";

        try (var istream = closeHandler(Optional.of(reader).flatMap(LambdaExceptionUtils.rethrowFunction(r->r.open(cname))))) {
            return istream.map(LambdaExceptionUtils.rethrowFunction(InputStream::readAllBytes))
                    .findFirst()
                    .orElseGet(()->new byte[0]);
        }
    }

    private Class<?> readerToClass(final ModuleReader reader, final ModuleReference ref, final String name) {
        var bytes = maybeTransformClassBytes(getClassBytes(reader, ref, name), name, null);
        if (bytes.length == 0) return null;
        var cname = name.replace('.','/')+".class";
        var modroot = this.resolvedRoots.get(ref.descriptor().name());
        ProtectionDomainHelper.tryDefinePackage(this, name, modroot.jar().getManifest(), t->modroot.jar().getManifest().getAttributes(t), this::definePackage); // Packages are dirctories, and can't be signed, so use raw attributes instead of signed.
        var cs = ProtectionDomainHelper.createCodeSource(toURL(ref.location()), modroot.jar().verifyAndGetSigners(cname, bytes));
        return defineClass(name, bytes, 0, bytes.length, ProtectionDomainHelper.createProtectionDomain(cs, this));
    }

    protected byte[] maybeTransformClassBytes(final byte[] bytes, final String name, final String context) {
        return bytes;
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            var c = findLoadedClass(name);
            if (c == null) {
                var index = name.lastIndexOf('.');
                if (index >= 0) {
                    final var pname = name.substring(0, index);
                    if (this.packageLookup.containsKey(pname)) {
                        c = findClass(this.packageLookup.get(pname).name(), name);
                    } else {
                        c = this.parentLoaders.getOrDefault(pname, fallbackClassLoader).loadClass(name);
                    }
                }
            }
            if (c == null) throw new ClassNotFoundException(name);
            if (resolve) resolveClass(c);
            return c;
        }
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        final String mname = classNameToModuleName(name);
        if (mname != null) {
            return findClass(mname, name);
        } else {
            return super.findClass(name);
        }
    }

    protected String classNameToModuleName(final String name) {
        final var pname = name.substring(0, name.lastIndexOf('.'));
        return Optional.ofNullable(this.packageLookup.get(pname)).map(ResolvedModule::name).orElse(null);
    }

    private Package definePackage(final String[] args) {
        return definePackage(args[0], args[1], args[2], args[3], args[4], args[5], args[6], null);
    }

    @Override
    public URL getResource(final String name) {
        try {
            var reslist = findResourceList(name);
            if (!reslist.isEmpty()) {
                return reslist.get(0);
            } else {
                return fallbackClassLoader.getResource(name);
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected URL findResource(final String moduleName, final String name) throws IOException {
        try {
            return loadFromModule(moduleName, (reader, ref) -> this.readerToURL(reader, ref, name));
        } catch (UncheckedIOException ioe) {
            throw ioe.getCause();
        }
    }

    @Override
    public Enumeration<URL> getResources(final String name) throws IOException {
        return Collections.enumeration(findResourceList(name));
    }

    private List<URL> findResourceList(final String name) throws IOException {
        var idx = name.lastIndexOf('/');
        var pkgname =  (idx == -1 || idx==name.length()-1) ? "" : name.substring(0,idx).replace('/','.');
        var module = packageLookup.get(pkgname);
        if (module != null) {
            var res = findResource(module.name(), name);
            return res != null ? List.of(res): List.of();
        } else {
            return resolvedRoots.values().stream()
                    .map(JarModuleFinder.JarModuleReference::jar)
                    .map(jar->jar.findFile(name))
                    .map(ModuleClassLoader::toURL)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    @Override
    protected Enumeration<URL> findResources(final String name) throws IOException {
        return Collections.enumeration(findResourceList(name));
    }

    @Override
    protected Class<?> findClass(final String moduleName, final String name) {
        try {
            return loadFromModule(moduleName, (reader, ref) -> this.readerToClass(reader, ref, name));
        } catch (IOException e) {
            return null;
        }
    }

    protected <T> T loadFromModule(final String moduleName, BiFunction<ModuleReader, ModuleReference, T> lookup) throws IOException {
        var module = configuration.findModule(moduleName).orElseThrow(FileNotFoundException::new);
        var ref = module.reference();
        try (var reader = ref.open()) {
            return lookup.apply(reader, ref);
        }
    }

    protected byte[] getMaybeTransformedClassBytes(final String name, final String context) throws ClassNotFoundException {
        byte[] bytes = new byte[0];
        Throwable suppressed = null;
        try {
            final var pname = name.substring(0, name.lastIndexOf('.'));
            if (this.packageLookup.containsKey(pname)) {
                bytes = loadFromModule(classNameToModuleName(name), (reader, ref)->this.getClassBytes(reader, ref, name));
            } else if (this.parentLoaders.containsKey(pname)) {
                var cname = name.replace('.','/')+".class";
                try (var is = this.parentLoaders.get(pname).getResourceAsStream(cname)) {
                    if (is != null)
                        bytes = is.readAllBytes();
                }
            }
        } catch (IOException e) {
            suppressed = e;
        }
        byte[] maybeTransformedBytes = maybeTransformClassBytes(bytes, name, context);
        if (maybeTransformedBytes.length == 0) {
            ClassNotFoundException e = new ClassNotFoundException(name);
            if (suppressed != null) e.addSuppressed(suppressed);
            throw e;
        }
        return maybeTransformedBytes;
    }

    public void setFallbackClassLoader(final ClassLoader fallbackClassLoader) {
        this.fallbackClassLoader = fallbackClassLoader;
    }
}
