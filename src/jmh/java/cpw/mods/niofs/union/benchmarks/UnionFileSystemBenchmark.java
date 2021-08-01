package cpw.mods.niofs.union.benchmarks;

import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionFileSystemProvider;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@State(Scope.Benchmark)
public class UnionFileSystemBenchmark {
    private static final UnionFileSystemProvider UFSP = (UnionFileSystemProvider) FileSystemProvider.installedProviders().stream().filter(fsp->fsp.getScheme().equals("union")).findFirst().orElseThrow(()->new IllegalStateException("Couldn't find UnionFileSystemProvider"));
    private static UnionFileSystem fileSystem;

    @Setup
    public void setup() throws Exception {
        var path1 = Paths.get("./src/jmh/resources/testjar1.jar");
        var path2 = Paths.get("./src/jmh/resources/testjar2.jar");
        var path3 = Paths.get("./src/jmh/resources/testjar3.jar");
        Map<String, List<Path>> properties = new HashMap<>();
        var additionalPaths = List.of(path2, path3);
        properties.put("additional", additionalPaths);

        fileSystem = (UnionFileSystem) UFSP.newFileSystem(path1, properties);
    }

    @Benchmark
    public void testFileExists(Blackhole blackhole) throws Exception {
        runExists("cpw/mods/niofs/union/UnionPath.class", true); //jar 1
        runExists("net/minecraftforge/client/event/GuiOpenEvent.class", true); //jar 2
        runExists("cpw/mods/modlauncher/Launcher.class", true); //jar 3
        runExists("cpw/mods/modlauncher/api/NoIDontExist.class", false);
        runExists("net/minecraftforge/client/nonexistent/Nope.class", false);
        runExists("Missing.class", false);
    }

    @Benchmark
    public void testDirectoryStream(Blackhole blackhole) throws Exception {
        runDirStream("cpw/mods/jarhandling", 5, blackhole); //jar 1
        runDirStream("net/minecraftforge/common", 72, blackhole); //jar 2
        runDirStream("cpw/mods/modlauncher/api", 34, blackhole); //jar 3
    }

    @Benchmark
    public void testByteChannel(Blackhole blackhole) throws Exception {
        runByteChannel("cpw/mods/niofs/union/UnionPath.class", blackhole); //jar 1
        runByteChannel("net/minecraftforge/client/event/GuiOpenEvent.class", blackhole); //jar 2
        runByteChannel("cpw/mods/modlauncher/Launcher.class", blackhole); //jar 3
    }

    @Benchmark
    public void testReadAttributes(Blackhole blackhole) throws Exception {
        runReadAttributes("cpw/mods/niofs/union/UnionPath.class", 9550, blackhole); //jar 1
        runReadAttributes("net/minecraftforge/client/event/GuiOpenEvent.class", 782, blackhole); //jar 2
        runReadAttributes("cpw/mods/modlauncher/Launcher.class", 12648, blackhole); //jar 3
    }

    @Benchmark
    public void testCommonPathUtilities(Blackhole blackhole) throws Exception {
        var path = fileSystem.getPath("net/minecraftforge/client/event/GuiOpenEvent.class");
        blackhole.consume(path.getFileName());
        blackhole.consume(path.getParent());
        blackhole.consume(path.normalize());
        blackhole.consume(path.subpath(0, 3));
    }

    private static void runExists(String pathString, boolean expected) throws Exception {
        if (Files.exists(fileSystem.getPath(pathString)) != expected) {
            throw new RuntimeException("Wrong exists status");
        }
    }

    private static void runDirStream(String pathString, int expectedEntries, Blackhole blackhole) throws Exception {
        int count = 0;
        try (var dirStream = Files.newDirectoryStream(fileSystem.getPath(pathString))) {
            for (Path subpath : dirStream) {
                count++;
                blackhole.consume(subpath);
            }
        }
        if (count != expectedEntries) {
            throw new RuntimeException("Wrong number of items");
        }
    }

    private static void runByteChannel(String pathString, Blackhole blackhole) throws Exception {
        try (var byteChannel = Files.newByteChannel(fileSystem.getPath(pathString))) {
            blackhole.consume(byteChannel);
        }
    }

    private static void runReadAttributes(String pathString, int expectedSize, Blackhole blackhole) throws Exception {
        BasicFileAttributes basicFileAttributes = Files.readAttributes(fileSystem.getPath(pathString), BasicFileAttributes.class);
        if (basicFileAttributes == null || basicFileAttributes.size() != expectedSize) {
            throw new RuntimeException(basicFileAttributes == null ? "Null attributes" : "Wrong size");
        }
        blackhole.consume(basicFileAttributes);
    }
}
