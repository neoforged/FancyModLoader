package cpw.mods.bootstraplauncher;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.jarhandling.SecureJar;

import java.io.File;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BootstrapLauncher {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        var legacyCP = Objects.requireNonNull(System.getProperty("legacyClassPath"), "Missing legacyClassPath, cannot bootstrap");
        var versionName = Objects.requireNonNull(System.getProperty("versionName"), "Missing versionName, cannot bootstrap");

        var fileList = Arrays.stream(legacyCP.split(File.pathSeparator))
                .filter(n->!n.endsWith(versionName+".jar"))
                .filter(n->!n.contains("/org/ow2/asm/"))
                .map(s->URI.create("file://"+s))
                .collect(Collectors.toList());
        Collections.reverse(fileList);
        var finder = fileList.stream()
                .map(Path::of)
                .map(SecureJar::from)
                .toArray(SecureJar[]::new);
        var alltargets = Arrays.stream(finder).map(SecureJar::name).toList();
        var jf = JarModuleFinder.of(finder);
        var cf = ModuleLayer.boot().configuration();
        var newcf = cf.resolveAndBind(jf, ModuleFinder.ofSystem(), alltargets);
        var mycl = new ModuleClassLoader("MC-BOOTSTRAP", newcf, List.of(ModuleLayer.boot()));
        var layer = ModuleLayer.defineModules(newcf, List.of(ModuleLayer.boot()), m->mycl);
        Thread.currentThread().setContextClassLoader(mycl);

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
