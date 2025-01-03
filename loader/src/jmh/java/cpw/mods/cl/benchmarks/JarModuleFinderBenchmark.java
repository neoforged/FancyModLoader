package cpw.mods.cl.benchmarks;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.SecureJar;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public void benchJarModuleFinderOf(Blackhole blackhole) {
        var secureJar1 = SecureJar.from(path1, path2);
        var secureJar2 = SecureJar.from(path3);
        var jarModuleFinder = JarModuleFinder.of(secureJar1, secureJar2);

        blackhole.consume(jarModuleFinder);
    }
}
