package cpw.mods.cl;

import cpw.mods.util.LambdaExceptionUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class ModuleClassLoader extends ClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
        URL.setURLStreamHandlerFactory(ModularURLHandler.INSTANCE);
        ModularURLHandler.initFrom(ModuleClassLoader.class.getModule().getLayer());
    }

    // Reflect into JVM internals to associate each ModuleClassLoader with all of its parent layers.
    // This is necessary to let ServiceProvider find service implementations in parent module layers.
    // At the moment, this does not work for providers in the bootstrap or platform class loaders,
    // but any other provider (defined by the application class loader or child layers) should work.
    //
    // The only mechanism the JVM has for this is to also look for layers defined by the parent class loader.
    // We don't want to set a parent because we explicitly do not want to delegate to a parent class loader,
    // and that wouldn't even handle the case of multiple parent layers anyway.
    private static final MethodHandle LAYER_BIND_TO_LOADER;

    static {
        try {
            var hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            hackfield.setAccessible(true);
            MethodHandles.Lookup hack = (MethodHandles.Lookup) hackfield.get(null);

            LAYER_BIND_TO_LOADER = hack.findSpecial(ModuleLayer.class, "bindToLoader", MethodType.methodType(void.class, ClassLoader.class), ModuleLayer.class);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invokes {@code ModuleLayer.bindToLoader(ClassLoader)}.
     */
    private static void bindToLayer(ModuleClassLoader classLoader, ModuleLayer layer) {
        try {
            LAYER_BIND_TO_LOADER.invokeExact(layer, (ClassLoader) classLoader);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private final Configuration configuration;
    private final Map<String, JarModuleFinder.JarModuleReference> resolvedRoots;
    private final Map<String, ResolvedModule> packageLookup;
    private final Map<String, ClassLoader> parentLoaders;
    private ClassLoader fallbackClassLoader;

    public ModuleClassLoader(final String name, final Configuration configuration, final List<ModuleLayer> parentLayers) {
        this(name, configuration, parentLayers, null);
    }

    /**
     * This constructor allows setting the parent {@linkplain ClassLoader classloader}. Use this with caution since
     * it will allow loading of classes from the classpath directly if the {@linkplain ClassLoader#getSystemClassLoader() system classloader}
     * is reachable from the given parent classloader.
     * <p>
     * Generally classes that are in packages covered by reachable modules are preferably loaded from these modules.
     * If a class-path entry is not shadowed by a module, specifying a parent class-loader may lead to those
     * classes now being loadable instead of throwing a {@link ClassNotFoundException}.
     * <p>
     * This relaxed classloader isolation is used in unit-testing, where testing libraries are loaded on the
     * system class-loader outside our control (by the Gradle test runner). We must not reload these classes
     * inside the module layers again, otherwise tests throw incompatible exceptions or may not be found at all.
     */
    @VisibleForTesting
    public ModuleClassLoader(final String name, final Configuration configuration, final List<ModuleLayer> parentLayers, @Nullable ClassLoader parentLoader) {
        super(name, parentLoader);
        this.fallbackClassLoader = Objects.requireNonNullElse(parentLoader, ClassLoader.getPlatformClassLoader());
        this.configuration = configuration;
        this.packageLookup = new HashMap<>();
        this.resolvedRoots = configuration.modules().stream()
                .filter(m -> m.reference() instanceof JarModuleFinder.JarModuleReference)
                .peek(mod -> {
                    // Populate packageLookup at the same time, for speed
                    mod.reference().descriptor().packages().forEach(pk -> this.packageLookup.put(pk, mod));
                })
                .collect(Collectors.toMap(mod -> mod.reference().descriptor().name(), mod -> (JarModuleFinder.JarModuleReference) mod.reference()));

        this.parentLoaders = new HashMap<>();
        Set<ModuleDescriptor> processedAutomaticDescriptors = new HashSet<>();
        Map<ResolvedModule, ClassLoader> classLoaderMap = new HashMap<>();
        Function<ResolvedModule, ClassLoader> findClassLoader = k -> {
            // Loading a class requires its module to be part of resolvedRoots
            // If it's not, we delegate loading to its module's classloader
            if (!this.resolvedRoots.containsKey(k.name())) {
                return parentLayers.stream()
                        .filter(l -> l.configuration() == k.configuration())
                        .flatMap(layer -> Optional.ofNullable(layer.findLoader(k.name())).stream())
                        .findFirst()
                        .orElse(ClassLoader.getPlatformClassLoader());
            } else {
                return ModuleClassLoader.this;
            }
        };
        // This loop will be O(n^2) for the average set of mods, since they all read one another.
        // However, we amortize some of the cost by optimizing the common automatic module path.
        for (var rm : configuration.modules()) {
            for (var other : rm.reads()) {
                ClassLoader cl = classLoaderMap.computeIfAbsent(other, findClassLoader);
                final var descriptor = other.reference().descriptor();
                if (descriptor.isAutomatic()) {
                    // No need to run this logic more than once per automatic module
                    if (processedAutomaticDescriptors.add(descriptor)) {
                        descriptor.packages().forEach(pn -> this.parentLoaders.put(pn, cl));
                    }
                } else {
                    // We actually use "rm" for this path, so we have to run it each time
                    descriptor.exports().stream()
                            .filter(e -> !e.isQualified() || (e.isQualified() && other.configuration() == configuration && e.targets().contains(rm.name())))
                            .map(ModuleDescriptor.Exports::source)
                            .forEach(pn -> this.parentLoaders.put(pn, cl));
                }
            }
        }
        // Bind this classloader to all parent layers recursively,
        // to make sure ServiceLoader can find providers defined in parent layers
        Set<ModuleLayer> visitedLayers = new HashSet<>();
        parentLayers.forEach(p -> forLayerAndParents(p, visitedLayers, l -> bindToLayer(this, l)));
    }

    private static void forLayerAndParents(ModuleLayer layer, Set<ModuleLayer> visited, Consumer<ModuleLayer> operation) {
        if (visited.contains(layer)) return;
        visited.add(layer);
        operation.accept(layer);

        if (layer != ModuleLayer.boot()) {
            layer.parents().forEach(l -> forLayerAndParents(l, visited, operation));
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
        var cname = name.replace('.', '/') + ".class";

        try (var istream = closeHandler(Optional.of(reader).flatMap(LambdaExceptionUtils.rethrowFunction(r -> r.open(cname))))) {
            return istream.map(LambdaExceptionUtils.rethrowFunction(InputStream::readAllBytes))
                    .findFirst()
                    .orElseGet(() -> new byte[0]);
        }
    }

    private Class<?> readerToClass(final ModuleReader reader, final ModuleReference ref, final String name) {
        var bytes = maybeTransformClassBytes(getClassBytes(reader, ref, name), name, null);
        if (bytes.length == 0) return null;
        var cname = name.replace('.', '/') + ".class";
        var modroot = this.resolvedRoots.get(ref.descriptor().name());
        ProtectionDomainHelper.tryDefinePackage(this, name, modroot.jar().getManifest(), t -> modroot.jar().getManifest().getAttributes(t), this::definePackage); // Packages are dirctories, and can't be signed, so use raw attributes instead of signed.
        var cs = ProtectionDomainHelper.createCodeSource(toURL(ref.location()), modroot.jar().verifyAndGetSigners(cname, bytes));
        var cls = defineClass(name, bytes, 0, bytes.length, ProtectionDomainHelper.createProtectionDomain(cs, this));
        ProtectionDomainHelper.trySetPackageModule(cls.getPackage(), cls.getModule());
        return cls;
    }

    protected byte[] maybeTransformClassBytes(final byte[] bytes, final String name, final @Nullable String context) {
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
        var pkgname = (idx == -1 || idx == name.length() - 1) ? "" : name.substring(0, idx).replace('/', '.');
        var module = packageLookup.get(pkgname);
        if (module != null) {
            var res = findResource(module.name(), name);
            return res != null ? List.of(res) : List.of();
        } else {
            return resolvedRoots.values().stream()
                    .map(JarModuleFinder.JarModuleReference::jar)
                    .map(jar -> jar.findFile(name))
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
        var module = configuration.findModule(moduleName);
        if (module.isEmpty()) {
            throw new NoSuchFileException("module " + moduleName);
        }
        var ref = module.get().reference();
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
                bytes = loadFromModule(classNameToModuleName(name), (reader, ref) -> this.getClassBytes(reader, ref, name));
            } else if (this.parentLoaders.containsKey(pname)) {
                var cname = name.replace('.', '/') + ".class";
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
