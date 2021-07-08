package cpw.mods.niofs.union;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class UnionFileSystemProvider extends FileSystemProvider {
    private final Map<Path, UnionFileSystem> fileSystems = new HashMap<>();

    @Override
    public String getScheme() {
        return "union";
    }

    private final UnionFileSystem DUMMY = new UnionFileSystem(this, (e1,e2)->true);

    @Override
    public FileSystem newFileSystem(final URI uri, final Map<String, ?> env) throws IOException {
        var path = Path.of(uri.getPath().split("!")[0]).toAbsolutePath().normalize();
        if (fileSystems.get(path) == DUMMY) throw new UnsupportedOperationException();
        fileSystems.put(path, DUMMY);
        @SuppressWarnings("unchecked")
        var additional = env.containsKey("additional") ? (List<Path>)env.get("additional") : List.<Path>of();
        try {
            return newFileSystem((e1,e2)->true, Stream.concat(Stream.of(path), additional.stream()).toArray(Path[]::new));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public FileSystem newFileSystem(final Path path, final Map<String, ?> env) throws IOException {
        if (fileSystems.get(path) == DUMMY) throw new UnsupportedOperationException();
        fileSystems.put(path, DUMMY);
        @SuppressWarnings("unchecked")
        var additional = env.containsKey("additional") ? (List<Path>)env.get("additional") : List.<Path>of();
        try {
            return newFileSystem((e1, e2)->true, Stream.concat(Stream.of(path), additional.stream()).toArray(Path[]::new));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public UnionFileSystem newFileSystem(final BiPredicate<String, String> pathfilter, final Path... paths) {
        if (paths.length == 0) throw new IllegalArgumentException("Need at least one path");

        var normpaths = Arrays.stream(paths)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .toArray(Path[]::new);
        fileSystems.put(normpaths[0], DUMMY);
        var ufs = new UnionFileSystem(this, pathfilter, normpaths);
        fileSystems.put(normpaths[0], ufs);
        return ufs;
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
        var basePath = Paths.get(parts[0]).toAbsolutePath().normalize();
        if (!fileSystems.containsKey(basePath)) throw new FileSystemNotFoundException();
        return fileSystems.get(basePath);
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

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(final Path path, final Class<V> type, final LinkOption... options) {
        throw new UnsupportedOperationException();
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
}
