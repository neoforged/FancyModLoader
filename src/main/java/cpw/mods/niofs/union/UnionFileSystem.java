package cpw.mods.niofs.union;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.InterruptibleChannel;
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
import java.util.ArrayList;
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
    private static final MethodHandle ZIPFS_CH;
    private static final MethodHandle FCI_UNINTERUPTIBLE;
    static final String SEP_STRING = "/";


    static {
        try {
            var hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            hackfield.setAccessible(true);
            MethodHandles.Lookup hack = (MethodHandles.Lookup) hackfield.get(null);

            var clz = Class.forName("jdk.nio.zipfs.ZipPath");
            ZIPFS_EXISTS = hack.findSpecial(clz, "exists", MethodType.methodType(boolean.class), clz);

            clz = Class.forName("jdk.nio.zipfs.ZipFileSystem");
            ZIPFS_CH = hack.findGetter(clz, "ch", SeekableByteChannel.class);

            clz = Class.forName("sun.nio.ch.FileChannelImpl");
            FCI_UNINTERUPTIBLE = hack.findSpecial(clz, "setUninterruptible", MethodType.methodType(void.class), clz);
        } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream buildInputStream(final UnionPath path) {
        try {
            var bytes = Files.readAllBytes(path);
            return new ByteArrayInputStream(bytes);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
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
    private final UnionFileSystemProvider provider;
    private final String key;
    private final List<Path> basepaths;
    private final int lastElementIndex;
    @Nullable
    private final BiPredicate<String, String> pathFilter;
    private final Map<Path, EmbeddedFileSystemMetadata> embeddedFileSystems;

    public Path getPrimaryPath() {
        return basepaths.get(basepaths.size() - 1);
    }

    @Nullable
    public BiPredicate<String, String> getFilesystemFilter() {
        return pathFilter;
    }

    String getKey() {
        return this.key;
    }

    private record EmbeddedFileSystemMetadata(Path path, FileSystem fs, SeekableByteChannel fsCh) {
    }

    public UnionFileSystem(final UnionFileSystemProvider provider, @Nullable BiPredicate<String, String> pathFilter, final String key, final Path... basepaths) {
        this.pathFilter = pathFilter;
        this.provider = provider;
        this.key = key;
        this.basepaths = IntStream.range(0, basepaths.length)
                .mapToObj(i -> basepaths[basepaths.length - i - 1])
                .filter(Files::exists)
                .toList(); // we flip the list so later elements are first in search order.
        lastElementIndex = basepaths.length - 1;
        this.embeddedFileSystems = this.basepaths.stream().filter(path -> !Files.isDirectory(path))
                .map(UnionFileSystem::openFileSystem)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(EmbeddedFileSystemMetadata::path, Function.identity()));
    }

    private static Optional<EmbeddedFileSystemMetadata> openFileSystem(final Path path) {
        try {
            var zfs = FileSystems.newFileSystem(path);
            SeekableByteChannel fci = (SeekableByteChannel) ZIPFS_CH.invoke(zfs);
            if (fci instanceof FileChannel) { // we only make file channels uninterruptible because byte channels (JIJ) already are
                FCI_UNINTERUPTIBLE.invoke(fci);
            }
            return Optional.of(new EmbeddedFileSystemMetadata(path, zfs, fci));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
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
            Boolean fastCheck = tryFastPathExists(this, path);
            if (fastCheck != null && !fastCheck) {
                return Optional.empty();
            } else {
                return Optional.of(path.getFileSystem().provider().readAttributes(path, BasicFileAttributes.class));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if a path exists using optimized filesystem-specific code.
     * (With a fallback to the regular {@link Files#exists(Path, LinkOption...)}).
     */
    private static boolean fastPathExists(UnionFileSystem ufs, Path path) {
        Boolean result = tryFastPathExists(ufs, path);
        return result != null ? result : Files.exists(path);
    }

    /**
     * Tries to check if a path exists using optimized filesystem-specific code,
     * or returns {@code null} if there is no optimized code for that particular filesystem.
     */
    @Nullable
    private static Boolean tryFastPathExists(UnionFileSystem ufs, Path path) {
        if (path.getFileSystem() == FileSystems.getDefault()) {
            return path.toFile().exists();
        } else if (path.getFileSystem().provider().getScheme().equals("jar")) {
            return zipFsExists(ufs, path);
        }

        return null;
    }

    private static boolean zipFsExists(UnionFileSystem ufs, Path path) {
        try {
            if (Optional.ofNullable(ufs.embeddedFileSystems.get(path.getFileSystem())).filter(efs -> !efs.fsCh.isOpen()).isPresent())
                throw new IllegalStateException("The zip file has closed!");
            return (boolean) ZIPFS_EXISTS.invoke(path);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /**
     * Finds the first real {@link Path} that matches the {@link UnionPath#toString() path} of the given {@code unionPath}, and the {@link #pathFilter filter}
     * of the file system.
     *
     * @param unionPath the path to find
     * @return an optional containing the first real path that {@link Files#exists(Path, LinkOption...) exists},
     * or otherwise the last path, if this file system has at least one {@link #basepaths base path}
     */
    private Optional<Path> findFirstFiltered(final UnionPath unionPath) {
        // Iterate the first base paths to try to find matching existing files
        for (int i = 0; i < lastElementIndex; i++) {
            final Path p = this.basepaths.get(i);
            final Path realPath = toRealPath(p, unionPath);
            // Test if the real path exists and matches the filter of this file system
            if (testFilter(realPath, p)) {
                if (fastPathExists(this, realPath)) {
                    return Optional.of(realPath);
                }
            }
        }

        // Otherwise, if we still haven't found an existing path, return the last possibility without checking its existence
        if (lastElementIndex >= 0) {
            final Path last = basepaths.get(lastElementIndex);
            final Path realPath = toRealPath(last, unionPath);
            // We still care about the FS filter, but not about the existence of the real path
            if (testFilter(realPath, last)) {
                return Optional.of(realPath);
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(final UnionPath path, final Class<A> type, final LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class) {
            // We need to run the test on the actual path,
            for (Path base : this.basepaths) {
                // We need to know the full path for the filter
                final Path realPath = toRealPath(base, path);
                final Optional<BasicFileAttributes> fileAttributes = this.getFileAttributes(realPath);
                if (fileAttributes.isPresent() && testFilter(realPath, base)) {
                    return (A) fileAttributes.get();
                }
            }
            throw new NoSuchFileException(path.toString());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public void checkAccess(final UnionPath p, final AccessMode... modes) throws IOException {
        try {
            findFirstFiltered(p).ifPresentOrElse(path -> {
                try {
                    if (modes.length == 0) {
                        if (!fastPathExists(this, path)) {
                            throw new UncheckedIOException(new NoSuchFileException(p.toString()));
                        }
                    } else {
                        path.getFileSystem().provider().checkAccess(path, modes);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, () -> {
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
        List<Closeable> closeables = new ArrayList<>(basepaths.size());
        Stream<Path> stream = Stream.empty();
        for (final var bp : basepaths) {
            final var dir = toRealPath(bp, path);
            if (!fastPathExists(this, dir)) {
                continue;
            }
            final var isSimple = embeddedFileSystems.containsKey(bp);
            final var ds = Files.newDirectoryStream(dir, filter);
            closeables.add(ds);
            final var currentPaths = StreamSupport.stream(ds.spliterator(), false)
                    .filter(p -> testFilter(p, bp))
                    .map(other -> fastPath(isSimple ? other : bp.relativize(other)));
            stream = Stream.concat(stream, currentPaths);
        }
        final Stream<Path> realStream = stream.distinct();
        return new DirectoryStream<>() {
            @Override
            public Iterator<Path> iterator() {
                return realStream.iterator();
            }

            @Override
            public void close() throws IOException {
                List<IOException> exceptions = new ArrayList<>();

                for (Closeable closeable : closeables) {
                    try {
                        closeable.close();
                    } catch (IOException e) {
                        exceptions.add(e);
                    }
                }

                if (!exceptions.isEmpty()) {
                    IOException aggregate = new IOException("Failed to close some streams in UnionFileSystem.newDirStream");
                    exceptions.forEach(aggregate::addSuppressed);
                    throw aggregate;
                }
            }
        };
    }

    /**
     * Create a relative UnionPath from the path elements of the given {@link Path}.
     */
    private Path fastPath(Path pathToConvert) {
        String[] parts = new String[pathToConvert.getNameCount()];

        for (int i = 0; i < parts.length; i++) {
            parts[i] = pathToConvert.getName(i).toString();
        }

        return fastPath(parts);
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
