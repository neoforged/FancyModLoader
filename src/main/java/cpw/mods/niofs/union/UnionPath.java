package cpw.mods.niofs.union;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class UnionPath implements Path {
    private final UnionFileSystem fileSystem;
    private final String[] pathParts;
    static final String ROOT = "";

    UnionPath(final UnionFileSystem fileSystem, final String... pathParts) {
        this.fileSystem = fileSystem;
        final var longstring = String.join(fileSystem.getSeparator(), pathParts);
        this.pathParts = getPathParts(longstring);
    }

    private String[] getPathParts(final String longstring) {
        return longstring.split(this.getFileSystem().getSeparator());
    }

    @Override
    public UnionFileSystem getFileSystem() {
        return this.fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return ROOT.equals(this.pathParts[0]);
    }

    @Override
    public Path getRoot() {
        return new UnionPath(this.fileSystem, ROOT);
    }


    @Override
    public Path getFileName() {
        return this.pathParts.length > 0 ? new UnionPath(this.fileSystem, this.pathParts[this.pathParts.length-1]) : null;
    }


    @Override
    public Path getParent() {
        if (this.pathParts.length > 0) {
            return new UnionPath(this.fileSystem, Arrays.copyOf(this.pathParts, this.pathParts.length - 1));
        } else {
            return null;
        }
    }

    @Override
    public int getNameCount() {
        return this.pathParts.length;
    }

    @Override
    public Path getName(final int index) {
        if (index < 0 || index > this.pathParts.length -1) throw new IllegalArgumentException();
        return new UnionPath(this.fileSystem,this.pathParts[index]);
    }

    @Override
    public Path subpath(final int beginIndex, final int endIndex) {
        if (beginIndex < 0 || beginIndex > this.pathParts.length - 1 || endIndex < 0 || endIndex > this.pathParts.length || beginIndex > endIndex) {
            throw new IllegalArgumentException();
        }
        return new UnionPath(this.fileSystem, Arrays.copyOfRange(this.pathParts, beginIndex, endIndex));
    }

    @Override
    public boolean startsWith(final Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        }
        if (other instanceof UnionPath bp) {
            return checkArraysMatch(this.pathParts, bp.pathParts);
        }
        return false;
    }


    @Override
    public boolean endsWith(final Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        }
        if (other instanceof UnionPath bp) {
            var revlists = Stream.of(this.pathParts, bp.pathParts)
                    .map(Arrays::asList)
                    .peek(Collections::reverse)
                    .map(l->l.toArray(new String[0]))
                    .toArray(String[][]::new);
            return checkArraysMatch(revlists[0], revlists[1]);
        }
        return false;
    }

    private static boolean checkArraysMatch(String[] array1, String[] array2) {
        return Arrays.mismatch(array1, 0, array2.length, array2, 0, array2.length) == -1;
    }

    @Override
    public Path normalize() {
        Deque<String> normpath = new ArrayDeque<>();
        for (String pathPart : this.pathParts) {
            switch (pathPart) {
                case ".":
                    break;
                case "..":
                    normpath.removeLast();
                    break;
                default:
                    normpath.addLast(pathPart);
                    break;
            }
        }
        return new UnionPath(this.fileSystem, String.join(this.fileSystem.getSeparator(), normpath));
    }

    @Override
    public Path resolve(final Path other) {
        if (other instanceof UnionPath path) {
            if (path.isAbsolute()) {
                return path;
            }
            return new UnionPath(this.fileSystem, this+fileSystem.getSeparator()+ other);
        }
        return other;
    }

    @Override
    public Path relativize(final Path other) {
        if (other instanceof UnionPath p) {
            if (p.getFileSystem()!=this.getFileSystem()) throw new IllegalArgumentException("Wrong filesystem");
            return p.subpath(this.getNameCount(), p.getNameCount());
        }
        throw new IllegalArgumentException("Wrong filesystem");
    }

    @Override
    public URI toUri() {
        return URI.create(fileSystem.provider().getScheme()+"://"+fileSystem.getPrimaryPath().toString()+"!"+toAbsolutePath());
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute())
            return this;
        else
            throw new IllegalStateException("Not absolute");
    }

    @Override
    public Path toRealPath(final LinkOption... options) throws IOException {
        return null;
    }

    @Override
    public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>[] events, final WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(final Path other) {
        return 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof UnionPath p) {
            return p.getFileSystem() == this.getFileSystem() && Arrays.equals(this.pathParts, p.pathParts);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.fileSystem) + 31 * Arrays.hashCode(this.pathParts);
    }

    @Override
    public String toString() {
        return String.join(fileSystem.getSeparator(), this.pathParts);
    }
}
