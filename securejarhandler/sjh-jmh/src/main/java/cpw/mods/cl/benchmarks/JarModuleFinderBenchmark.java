package cpw.mods.cl.benchmarks;

import cpw.mods.cl.JarModuleFinder;
import cpw.mods.jarhandling.SecureJar;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Paths;

public class JarModuleFinderBenchmark {

    @Benchmark
    public void benchJarModuleFinderOf(Blackhole blackhole) {
        var path1 = Paths.get("./src/jmh/resources/testjar1.jar");
        var path2 = Paths.get("./src/jmh/resources/testjar2.jar");
        var path3 = Paths.get("./src/jmh/resources/testjar3.jar");
        var secureJar1 = SecureJar.from(path1, path2);
        var secureJar2 = SecureJar.from(path3);
        var jarModuleFinder = JarModuleFinder.of(secureJar1, secureJar2);

        blackhole.consume(jarModuleFinder);
    }
}
