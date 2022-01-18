package cpw.mods.niofs.union;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public class UnionFileSystemProvider extends FileSystemProvider {
    private final Map<String, UnionFileSystem> fileSystems = new HashMap<>();
    private int index = 0;

    @Override
    public String getScheme() {
        return "union";
    }

    /**
     * Copied from ZipFileSystem, we should just extend ZipFileSystem, but I need to ask cpw
     * if there was a reason we are not.
     */
    protected Path uriToPath(URI uri) {
        String scheme = uri.getScheme();
        if ((scheme == null) || !scheme.equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException("URI scheme is not '" + getScheme() + "'");
        }
        try {
            // only support legacy JAR URL syntax  jar:{uri}!/{entry} for now
            String spec = uri.getRawSchemeSpecificPart();
            int sep = spec.indexOf("!/");
            if (sep != -1) {
                spec = spec.substring(0, sep);
            }
            return Paths.get(new URI(spec)).toAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Invoked by FileSystems.newFileSystem, Only returns a value if env contains one of more of:
     *   "filter": BiPredicate<String, String> - A filter to apply to the opened path
     *   "additional": List<Path> - Additional paths to join together
     * If none specified, throws IllegalArgumentException
     * If uri.getScheme() is not "union" throws IllegalArgumentException
     * If you wish to create a UnionFileSystem explicitly, invoke newFileSystem(BiPredicate, Path...)
     */
    @Override
    public FileSystem newFileSystem(final URI uri, final Map<String, ?> env) throws IOException {
        @SuppressWarnings("unchecked")
        var additional = ((Map<String, List<Path>>)env).getOrDefault("additional", List.<Path>of());
        @SuppressWarnings("unchecked")
        var filter = ((Map<String, BiPredicate<String, String>>)env).getOrDefault("filter", null);

        if (filter == null && additional.isEmpty())
            throw new IllegalArgumentException("Missing additional and/or filter");

        if (filter == null)
            filter = (p, b) -> true;

        var path = uriToPath(uri);
        var key = makeKey(path);
        try {
            return newFileSystemInternal(key, filter, Stream.concat(Stream.of(path), additional.stream()).toArray(Path[]::new));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Invoked by FileSystems.newFileSystem, Only returns a value if env contains one of more of:
     *   "filter": BiPredicate<String, String> - A filter to apply to the opened path
     *   "additional": List<Path> - Additional paths to join together
     * If none specified, throws UnsupportedOperationException instead of IllegalArgumentException
     *   so that FileSystems.newFileSystem will search for the next provider.
     * If you wish to create a UnionFileSystem explicitly, invoke newFileSystem(BiPredicate, Path...)
     */
    @Override
    public FileSystem newFileSystem(final Path path, final Map<String, ?> env) throws IOException {
        @SuppressWarnings("unchecked")
        var additional = ((Map<String, List<Path>>)env).getOrDefault("additional", List.<Path>of());
        @SuppressWarnings("unchecked")
        var filter = ((Map<String, BiPredicate<String, String>>)env).getOrDefault("filter", null);

        if (filter == null && additional.isEmpty())
            throw new UnsupportedOperationException("Missing additional and/or filter");

        var key = makeKey(path);
        try {
            return newFileSystemInternal(key, filter, Stream.concat(Stream.of(path), additional.stream()).toArray(Path[]::new));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public UnionFileSystem newFileSystem(final BiPredicate<String, String> pathfilter, final Path... paths) {
        if (paths.length == 0) throw new IllegalArgumentException("Need at least one path");
        var key = makeKey(paths[0]);
        return newFileSystemInternal(key, pathfilter, paths);
    }

    private UnionFileSystem newFileSystemInternal(final String key, final BiPredicate<String, String> pathfilter, final Path... paths) {
        var normpaths = Arrays.stream(paths)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toArray(Path[]::new);

        synchronized (fileSystems) {
            var ufs = new UnionFileSystem(this, pathfilter, key, normpaths);
            fileSystems.put(key, ufs);
            return ufs;
        }
    }

    private synchronized String makeKey(Path path) {
        var key= (path instanceof UnionPath p) ? p.getFileSystem().getKey() :
                        path.toAbsolutePath().normalize().toUri().getPath();
        return key.replace('!', '_') + "#" + index++;
    }

    @Override
    public Path getPath(final URI uri) {
        var parts = uri.getPath().split("!");
        if (parts.length > 1) {
            return getFileSystem(uri).getPath(parts[1]);
        } else {
            return ((UnionFileSystem)getFileSystem(uri)).getRoot();
        }
    }

    @Override
    public FileSystem getFileSystem(final URI uri) {
        var parts = uri.getPath().split("!");
        if (!fileSystems.containsKey(parts[0])) throw new FileSystemNotFoundException();
        return fileSystems.get(parts[0]);
    }

    @Override
    public SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options, final FileAttribute<?>... attrs) throws IOException {
        if (path instanceof UnionPath up) {
            if (options.size() > 1) throw new UnsupportedOperationException();
            if (!options.isEmpty() && !options.contains(StandardOpenOption.READ)) throw new UnsupportedOperationException();
            return up.getFileSystem().newReadByteChannel(up);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir, final DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (dir instanceof UnionPath up) {
            return up.getFileSystem().newDirStream(up, filter);
        }
        return null;
    }

    @Override
    public void createDirectory(final Path dir, final FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(final Path path) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copy(final Path source, final Path target, final CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void move(final Path source, final Path target, final CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSameFile(final Path path, final Path path2) throws IOException {
        return false;
    }

    @Override
    public boolean isHidden(final Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(final Path path) throws IOException {
        return null;
    }

    @Override
    public void checkAccess(final Path path, final AccessMode... modes) throws IOException {
        if (path instanceof UnionPath p) {
            p.getFileSystem().checkAccess(p, modes);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V extends FileAttributeView> V getFileAttributeView(final Path path, final Class<V> type, final LinkOption... options) {
        if (path instanceof UnionPath && type == BasicFileAttributeView.class) {
            return (V) new UnionBasicFileAttributeView(path, options);
        }
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(final Path path, final Class<A> type, final LinkOption... options) throws IOException {
        if (path instanceof UnionPath p) {
            return p.getFileSystem().readAttributes(p, type);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> readAttributes(final Path path, final String attributes, final LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(final Path path, final String attribute, final Object value, final LinkOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    void removeFileSystem(UnionFileSystem fs) {
        synchronized (fileSystems) {
            fileSystems.remove(fs.getKey());
        }
    }

    private class UnionBasicFileAttributeView implements BasicFileAttributeView {

        private final Path path;
        private final LinkOption[] options;

        public UnionBasicFileAttributeView(Path path, LinkOption[] options) {
            this.path = path;
            this.options = options;
        }

        @Override
        public String name() {
            return "union";
        }

        @Override
        public BasicFileAttributes readAttributes() throws IOException {
            return UnionFileSystemProvider.this.readAttributes(path, BasicFileAttributes.class, options);
        }

        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) {
        }

    }
}
