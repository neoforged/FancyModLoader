package cpw.mods.bootstraplauncher;

import java.io.File;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;

public class BootstrapLauncher {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws URISyntaxException {
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
        final ClassLoader cl = new URLClassLoader("bootstrapbootloader", urlList.toArray(URL[]::new), null);
        final var cf = ModuleLayer.boot()
                .configuration()
                // we resolve and bind to OURSELVES here so that we can find the people providing the Consumer interface we're looking to grab later on
                // requires the uses declaration in module-info.
                .resolveAndBind(ModuleFinder.compose(ModuleFinder.of(Path.of(BootstrapLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI())),ModuleFinder.of(pathList.toArray(Path[]::new))), ModuleFinder.ofSystem(), List.of("cpw.mods.bootstraplauncher"));
        final var layer = ModuleLayer.defineModulesWithOneLoader(cf, List.of(ModuleLayer.boot()), cl);
        final var loader = ServiceLoader.load(layer.layer(), Consumer.class);
        ((Consumer<String[]>)loader.stream().findFirst().orElseThrow().get()).accept(args);
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
