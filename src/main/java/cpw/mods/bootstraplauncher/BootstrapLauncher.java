package cpw.mods.bootstraplauncher;

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class BootstrapLauncher {
    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        var legacyCP = Objects.requireNonNull(System.getProperty("legacyClassPath"), "Missing legacyClassPath, cannot bootstrap");
        var versionName = Objects.requireNonNull(System.getProperty("versionName"), "Missing versionName, cannot bootstrap");

        var fileList = Arrays.stream(legacyCP.split(File.pathSeparator))
                .filter(n->!n.endsWith(versionName+".jar"))
                .map(s->URI.create("file://"+s))
                .toList();
        var urlList = fileList.stream()
                .map(uncheck(URI::toURL));
        var pathList = fileList.stream()
                .map(Path::of);
        final ClassLoader cl = new URLClassLoader("bootstrap", urlList.toArray(URL[]::new), null);
        final var cf = ModuleLayer.boot().configuration().resolve(ModuleFinder.of(pathList.toArray(Path[]::new)), ModuleFinder.ofSystem(), List.of("cpw.mods.modlauncher"));
        final var layer = ModuleLayer.defineModulesWithOneLoader(cf, List.of(ModuleLayer.boot()), cl);
        final var module = layer.layer().findModule("cpw.mods.modlauncher").orElseThrow();
        Optional.ofNullable(Class.forName(module, "cpw.mods.modlauncher.Launcher"))
                .map(uncheck(c->c.getMethod("main", String[].class)))
                .orElseThrow(()->new IllegalStateException("Failed to find modlauncher"))
                .invoke(null, (Object)args);
    }

    public interface ExcFunction<T, R, E extends Throwable> {
        R apply(T input) throws E;
    }

    public static <T, R, E extends Throwable> Function<T, R> uncheck(ExcFunction<T,R,E> f) {
        return i->{
            try {
                return f.apply(i);
            } catch (Throwable e) {
                throwAsUnchecked(e);
                return null;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAsUnchecked(Throwable exception) throws E {
        throw (E) exception;
    }

}
