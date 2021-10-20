package cpw.mods.niofs.pathfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PathFileSystemProvider extends FileSystemProvider {
    private final Map<String, PathFileSystem> fileSystems = new HashMap<>();

    @Override
    public String getScheme() {
        return "path";
    }

    /**
     * Invoked by FileSystems.newFileSystem, Only returns a value if env contains an entry with the name of
     *          "package" and Path targeting the file in question.
     * If not specified, throws IllegalArgumentException
     * If uri.getScheme() is not "path" throws IllegalArgumentException
     * If you wish to create a PathFileSystem explicitly, invoke newFileSystem(Path)
     */
    @Override
    public FileSystem newFileSystem(final URI uri, final Map<String, ?> env) throws IOException {
        @SuppressWarnings("unchecked")
        var packagePath = ((Map<String, Path>)env).getOrDefault("packagePath", null);

        if (packagePath == null)
            throw new UnsupportedOperationException("Missing packagePath");

        var key = makeKey(uri);

        if (this.fileSystems.containsKey(key))
            return this.fileSystems.get(key);

        try {
            return newFileSystemInternal(key, packagePath);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Invoked by FileSystems.newFileSystem, Only returns a value if env contains an entry with the name of
     *          "package" and Path targeting the file in question.
     * If none specified, throws UnsupportedOperationException instead of IllegalArgumentException
     *   so that FileSystems.newFileSystem will search for the next provider.
     * If you wish to create a UnionFileSystem explicitly, invoke newFileSystem(BiPredicate, Path...)
     */
    @Override
    public FileSystem newFileSystem(final Path path, final Map<String, ?> env) throws IOException {
        @SuppressWarnings("unchecked")
        var packagePath = ((Map<String, Path>)env).getOrDefault("packagePath", null);

        if (packagePath == null)
            throw new UnsupportedOperationException("Missing packagePath");

        var key = makeKey(path);
        try {
            return newFileSystemInternal(key, packagePath);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public PathFileSystem newFileSystem(final Path path) {
        if (path == null) throw new IllegalArgumentException("Path is null");
        var key = makeKey(path);
        return newFileSystemInternal(key, path);
    }

    private PathFileSystem newFileSystemInternal(final String key, final Path path) {
        var normalizedPath = path.toAbsolutePath().normalize();

        synchronized (fileSystems) {
            var ufs = new PathFileSystem(this, key, normalizedPath);
            fileSystems.put(key, ufs);
            return ufs;
        }
    }

    private String makeKey(URI uri) {
        final String keyValue = uri.getRawSchemeSpecificPart();
        if (keyValue.startsWith("//"))
            return keyValue.substring(2);

        return keyValue;
    }

    private String makeKey(Path path) {
        return path.getFileName().toString();
    }

    @Override
    public Path getPath(final URI uri) {
        var parts = uri.getRawSchemeSpecificPart().split("~");
        if (parts.length > 1) {
            return getFileSystem(uri).getPath(parts[1]);
        } else {
            return ((PathFileSystem)getFileSystem(uri)).getRoot();
        }
    }

    @Override
    public FileSystem getFileSystem(final URI uri) {
        var parts = uri.getRawSchemeSpecificPart().split("~");
        if (!fileSystems.containsKey(parts[0])) throw new FileSystemNotFoundException();
        return fileSystems.get(parts[0]);
    }

    @Override
    public SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options, final FileAttribute<?>... attrs) throws IOException {
        if (path instanceof PathPath up) {
            return up.getFileSystem().newByteChannel(path, options, attrs);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir, final DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (dir instanceof PathPath up) {
            return up.getFileSystem().newDirectoryStream(dir, filter);
        }
        throw new UnsupportedOperationException();
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
        //Noop for now.
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(final Path path, final Class<V> type, final LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(final Path path, final Class<A> type, final LinkOption... options) throws IOException {
        if (path instanceof PathPath p) {
            return p.getFileSystem().readAttributes(path, type, options);
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

    void removeFileSystem(PathFileSystem fs) {
        synchronized (fileSystems) {
            fileSystems.remove(fs.getKey());
        }
    }

    protected URI buildUriFor(final PathPath path) throws URISyntaxException, IllegalArgumentException
    {
        return new URI(
          path.getFileSystem().provider().getScheme(),
          path.getFileSystem().getKey() + '~' + this,
          null
        );
    }
}
