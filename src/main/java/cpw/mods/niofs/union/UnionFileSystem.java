package cpw.mods.niofs.union;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class UnionFileSystem extends FileSystem {
    private static final MethodHandle ZIPFS_EXISTS;
    static final String SEP_STRING = "/";

    static {
        try {
            var hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            hackfield.setAccessible(true);
            MethodHandles.Lookup hack = (MethodHandles.Lookup) hackfield.get(null);

            var clz = Class.forName("jdk.nio.zipfs.ZipPath");
            ZIPFS_EXISTS = hack.findSpecial(clz, "exists", MethodType.methodType(boolean.class), clz);
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    private static class NoSuchFileException extends java.nio.file.NoSuchFileException {
        public NoSuchFileException(final String file) {
            super(file);
        }
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    private static class UncheckedIOException extends java.io.UncheckedIOException {
        public UncheckedIOException(final IOException cause) {
            super(cause);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
    private final UnionPath root = new UnionPath(this, "/");
    private final UnionPath notExistingPath = new UnionPath(this, "/SNOWMAN");
    private final UnionFileSystemProvider provider;
    private final String key;
    private final List<Path> basepaths;
    private final BiPredicate<String, String> pathFilter;
    private final Map<Path,EmbeddedFileSystemMetadata> embeddedFileSystems;

    public Path getPrimaryPath() {
        return basepaths.get(basepaths.size()-1);
    }

    public BiPredicate<String, String> getFilesystemFilter() {
        return pathFilter;
    }

    String getKey()  {
        return this.key;
    }

    private record EmbeddedFileSystemMetadata(Path path, FileSystem fs) {}

    public UnionFileSystem(final UnionFileSystemProvider provider, final BiPredicate<String, String> pathFilter, final String key, final Path... basepaths) {
        this.pathFilter = pathFilter;
        this.provider = provider;
        this.key = key;
        this.basepaths = IntStream.range(0, basepaths.length)
                .mapToObj(i->basepaths[basepaths.length - i - 1])
                .filter(Files::exists)
                .toList(); // we flip the list so later elements are first in search order.
        this.embeddedFileSystems = this.basepaths.stream().filter(path -> !Files.isDirectory(path))
                .map(UnionFileSystem::openFileSystem)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(EmbeddedFileSystemMetadata::path, Function.identity()));
    }

    private static Optional<EmbeddedFileSystemMetadata> openFileSystem(final Path path) {
        try {
            return Optional.of(new EmbeddedFileSystemMetadata(path, FileSystems.newFileSystem(path)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public UnionFileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() {
        provider().removeFileSystem(this);
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return SEP_STRING;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(root);
    }

    public Path getRoot() {
        return root;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections::emptyIterator;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override
    public Path getPath(final String first, final String... more) {
        if (more.length > 0) {
            var args = new String[more.length + 1];
            args[0] = first;
            System.arraycopy(more, 0, args, 1, more.length);
            return new UnionPath(this, args);
        }
        return new UnionPath(this, first);
    }

    private Path fastPath(final String... parts) {
        return new UnionPath(this, false, parts);
    }

    @Override
    public PathMatcher getPathMatcher(final String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    List<Path> getBasePaths() {
        return this.basepaths;
    }

    private Optional<BasicFileAttributes> getFileAttributes(final Path path) {
        try {
            if (path.getFileSystem() == FileSystems.getDefault() && !path.toFile().exists()) {
                return Optional.empty();
            } else if (path.getFileSystem().provider().getScheme().equals("jar") && !zipFsExists(path)) {
                return Optional.empty();
            } else {
                return Optional.of(path.getFileSystem().provider().readAttributes(path, BasicFileAttributes.class));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<Path> findFirstPathAt(final UnionPath path) {
        return this.basepaths.stream()
                .map(p->toRealPath(p , path))
                .filter(p->p!=notExistingPath)
                .filter(Files::exists)
                .findFirst();
    }

    private static boolean zipFsExists(Path path) {
        try {
            return (boolean) ZIPFS_EXISTS.invoke(path);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
    private Optional<Path> findFirstFiltered(final UnionPath path) {
        for (Path p : this.basepaths) {
            Path realPath = toRealPath(p, path);
            if (realPath != notExistingPath && testFilter(realPath, p)) {
                if (realPath.getFileSystem() == FileSystems.getDefault()) {
                    if (realPath.toFile().exists()) {
                        return Optional.of(realPath);
                    }
                } else if (realPath.getFileSystem().provider().getScheme().equals("jar")) {
                    if (zipFsExists(realPath)) {
                        return Optional.of(realPath);
                    }
                } else if (Files.exists(realPath)) {
                    return Optional.of(realPath);
                }
            }
        }
        return Optional.empty();
    }

    private <T> Stream<T> streamPathList(final Function<Path,Optional<T>> function) {
        return this.basepaths.stream()
                .map(function)
                .flatMap(Optional::stream);
    }

    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(final UnionPath path, final Class<A> type, final LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class) {
            // We need to run the test on the actual path,
            for (Path base : this.basepaths) {
                // We need to know the full path for the filter
                Path realPath = toRealPath(base, path);
                if (realPath != notExistingPath) {
                    Optional<BasicFileAttributes> fileAttributes = this.getFileAttributes(realPath);
                        if (fileAttributes.isPresent() && testFilter(realPath, base)) {
                        return (A) fileAttributes.get();
                    }
                }
            }
            throw new NoSuchFileException(path.toString());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void checkAccess(final UnionPath p, final AccessMode... modes) throws IOException {
        try {
            findFirstFiltered(p).ifPresentOrElse(path-> {
                try {
                    if (modes.length == 0 && path.getFileSystem() == FileSystems.getDefault()) {
                        if (!path.toFile().exists()) {
                            throw new UncheckedIOException(new NoSuchFileException(p.toString()));
                        }
                    } else {
                        path.getFileSystem().provider().checkAccess(path, modes);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, ()->{
                throw new UncheckedIOException(new NoSuchFileException(p.toString()));
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private Path toRealPath(final Path basePath, final UnionPath path) {
        var embeddedpath = path.isAbsolute() ? this.root.relativize(path) : path;
        var resolvepath = embeddedpath.normalize().toString();
        var efsm = embeddedFileSystems.get(basePath);
        if (efsm != null) {
            return efsm.fs().getPath(resolvepath);
        } else {
            return basePath.resolve(resolvepath);
        }
    }

    public SeekableByteChannel newReadByteChannel(final UnionPath path) throws IOException {
        try {
            return findFirstFiltered(path)
                    .map(this::byteChannel)
                    .orElseThrow(FileNotFoundException::new);
        } catch (UncheckedIOException ioe) {
            throw ioe.getCause();
        }
    }

    private SeekableByteChannel byteChannel(final Path path) {
        try {
            return Files.newByteChannel(path, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DirectoryStream<Path> newDirStream(final UnionPath path, final DirectoryStream.Filter<? super Path> filter) throws IOException {
        final var allpaths = new LinkedHashSet<Path>();
        for (final var bp : basepaths) {
            final var dir = toRealPath(bp, path);
            if (dir == notExistingPath) {
                continue;
            } else if (dir.getFileSystem() == FileSystems.getDefault() && !dir.toFile().exists()) {
                continue;
            } else if (dir.getFileSystem().provider().getScheme() == "jar" && !zipFsExists(dir)) {
                continue;
            } else if (Files.notExists(dir)) {
                continue;
            }
            final var isSimple = embeddedFileSystems.containsKey(bp);
            try (final var ds = Files.newDirectoryStream(dir, filter)) {
                StreamSupport.stream(ds.spliterator(), false)
                        .filter(p->testFilter(p, bp))
                        .map(other -> StreamSupport.stream(Spliterators.spliteratorUnknownSize((isSimple ? other : bp.relativize(other)).iterator(), Spliterator.ORDERED), false)
                                .map(Path::getFileName).map(Path::toString).toArray(String[]::new))
                        .map(this::fastPath)
                        .forEachOrdered(allpaths::add);
            }
        }
        return new DirectoryStream<>() {
            @Override
            public Iterator<Path> iterator() {
                return allpaths.iterator();
            }

            @Override
            public void close() throws IOException {
                // noop
            }
        };
    }

    /*
     * Standardize paths:
     * Path separators converted to /
     * Directories end with /
     * Remove leading / for absolute paths
     */
    private boolean testFilter(final Path path, final Path basePath) {
        if (pathFilter == null) return true;

        var sPath = path.toString();
        if (path.getFileSystem() == basePath.getFileSystem()) // Directories, zips will be different file systems.
            sPath = basePath.relativize(path).toString().replace('\\', '/');
        if (Files.isDirectory(path))
            sPath += '/';
        if (sPath.length() > 1 && sPath.startsWith("/"))
            sPath = sPath.substring(1);
        String sBasePath = basePath.toString().replace('\\', '/');
        if (sBasePath.length() > 1 && sBasePath.startsWith("/"))
            sBasePath = sBasePath.substring(1);
        return pathFilter.test(sPath, sBasePath);
    }
}
