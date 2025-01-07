package cpw.mods.cl.benchmarks;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.impl.Jar;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class JarModuleFinderBenchmark {
    private Path path1;
    private Path path2;
    private Path path3;

    @Setup
    public void setup() throws Exception {
        path1 = Paths.get("src", "testJars", "testjar1.jar").toAbsolutePath().normalize();
        path2 = Paths.get("src", "testJars", "testjar2.jar").toAbsolutePath().normalize();
        path3 = Paths.get("src", "testJars", "testjar3.jar").toAbsolutePath().normalize();
    }

    @Benchmark
    public void benchJarModuleFinderOf(Blackhole blackhole) throws IOException {
        var secureJar1 = Jar.of(JarContents.ofPaths(List.of(path1, path2)));
        var secureJar2 = Jar.of(path3);
        var jarModuleFinder = JarModuleFinder.of(secureJar1, secureJar2);

        blackhole.consume(jarModuleFinder);
    }
}
