package cpw.mods.niofs.union;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.IntBinaryOperator;
import java.util.stream.IntStream;

public class UnionPath implements Path {
    private final UnionFileSystem fileSystem;
    private final boolean absolute;
    private final String[] pathParts;
    
    // Store the normalized path after it has been created first
    private UnionPath normalized;
    
    UnionPath(final UnionFileSystem fileSystem, final String... pathParts) {
        this.fileSystem = fileSystem;
        if (pathParts.length == 0) {
            this.absolute = false;
            this.pathParts = new String[0];
        } else {
            StringBuilder joiner = new StringBuilder();
            for (int i = 0; i < pathParts.length; i++) {
                final String element = pathParts[i];
                if (!element.isEmpty()) {
                    joiner.append(element);
                    if (i<pathParts.length-1) joiner.append(UnionFileSystem.SEP_STRING);
                }
            }
            final var longstring = joiner.toString();
            this.absolute = longstring.startsWith(UnionFileSystem.SEP_STRING);
            this.pathParts = getPathParts(longstring);
        }
        this.normalized = null;
    }

    // Private constructor only for known correct split and extra value for absolute
    UnionPath(final UnionFileSystem fileSystem, boolean absolute, final String... pathParts) {
        this(fileSystem, absolute, false, pathParts);
    }
    
    private UnionPath(final UnionFileSystem fileSystem, boolean absolute, boolean isNormalized, final String... pathParts) {
        this.fileSystem = fileSystem;
        this.absolute = absolute;
        this.pathParts = pathParts;
        if (isNormalized)
            this.normalized = this;
        else
            this.normalized = null;
    }

    private String[] getPathParts(final String longstring) {
        var clean = longstring.replace('\\', '/');
        int startIndex = 0;
        List<String> parts = new ArrayList<>();
        while (startIndex != longstring.length()) {
            int index = clean.indexOf('/', startIndex);
            if (index == -1) {
                parts.add(clean.substring(startIndex));
                break;
            }
            // Skips double slash and slash and start/end
            if (index != startIndex) {
                parts.add(clean.substring(startIndex, index));
            }
            startIndex = (index + 1);
        }
        return parts.toArray(String[]::new);
    }

    @Override
    public UnionFileSystem getFileSystem() {
        return this.fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return this.absolute;
    }

    @Override
    public Path getRoot() {
        // Found nothing in the docs that say a non-absolute path can't have a root
        // although this is uncommon. However, other stuff relies on it so leave it
        //if (!this.absolute)
        //    return null;
        return this.fileSystem.getRoot();
    }
    
    @Override
    public Path getFileName() {
        if (this.pathParts.length > 0) {
            return new UnionPath(this.getFileSystem(), false, this.pathParts[this.pathParts.length - 1]);
        } else {
            // normally would be null for the empty absolute path and empty string for the empty relative
            // path. But again, very much stuff relies on it and there's no current directory for union
            // paths, so it does not really matter.
            return new UnionPath(this.fileSystem, false);
        }
    }

    @Override
    public Path getParent() {
        if (this.pathParts.length > 0) {
            return new UnionPath(this.fileSystem, this.absolute, Arrays.copyOf(this.pathParts,this.pathParts.length - 1));
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
        return new UnionPath(this.fileSystem, false, this.pathParts[index]);
    }

    @Override
    public UnionPath subpath(final int beginIndex, final int endIndex) {
        if (!this.absolute && this.pathParts.length == 0 && beginIndex == 0 && endIndex == 1)
            return new UnionPath(this.fileSystem, false);
        if (beginIndex < 0 || beginIndex > this.pathParts.length - 1 || endIndex < 0 || endIndex > this.pathParts.length || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Out of range "+beginIndex+" to "+endIndex+" for length "+this.pathParts.length);
        }
        if (!this.absolute && beginIndex == 0 && endIndex == this.pathParts.length) {
            return this;
        }
        return new UnionPath(this.fileSystem, false, Arrays.copyOfRange(this.pathParts, beginIndex, endIndex));
    }

    @Override
    public boolean startsWith(final Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        }
        if (other instanceof UnionPath bp) {
            if (this.absolute != bp.absolute)
                return false;
            return checkArraysMatch(this.pathParts, bp.pathParts, false);
        }
        return false;
    }


    @Override
    public boolean endsWith(final Path other) {
        if (other.getFileSystem() != this.getFileSystem()) {
            return false;
        }
        if (other instanceof UnionPath bp) {
            if (!this.absolute && bp.absolute)
                return false;
            return checkArraysMatch(this.pathParts, bp.pathParts, true);
        }
        return false;
    }

    private static boolean checkArraysMatch(String[] array1, String[] array2, boolean reverse) {
        var length = Math.min(array1.length, array2.length);
        IntBinaryOperator offset = reverse ? (l, i) -> l - i - 1 : (l, i) -> i;
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(array1[offset.applyAsInt(array1.length, i)], array2[offset.applyAsInt(array2.length, i)]))
                return false;
        }
        return true;
    }

    @Override
    public Path normalize() {
        if (normalized != null)
            return normalized;
        Deque<String> normpath = new ArrayDeque<>();
        for (String pathPart : this.pathParts) {
            switch (pathPart) {
                case ".":
                    break;
                case "..":
                    if (normpath.isEmpty() || normpath.getLast().equals("..")) {
                        // .. on an empty path is allowed, so keep it
                        normpath.addLast(pathPart);
                    } else {
                        normpath.removeLast();
                    }
                    break;
                default:
                    normpath.addLast(pathPart);
                    break;
            }
        }
        normalized = new UnionPath(this.fileSystem, this.absolute, true, normpath.toArray(new String[0]));
        return normalized;
    }

    @Override
    public Path resolve(final Path other) {
        if (other instanceof UnionPath path) {
            if (path.isAbsolute()) {
                return path;
            }
            String[] mergedParts = new String[this.pathParts.length + path.pathParts.length];
            System.arraycopy(this.pathParts, 0, mergedParts, 0, this.pathParts.length);
            System.arraycopy(path.pathParts, 0, mergedParts, this.pathParts.length, path.pathParts.length);
            return new UnionPath(this.fileSystem, this.absolute, mergedParts);
        }
        return other;
    }

    @Override
    public Path relativize(final Path other) {
        if (other.getFileSystem()!=this.getFileSystem()) throw new IllegalArgumentException("Wrong filesystem");
        if (other instanceof UnionPath p) {
            if (this.absolute != p.absolute) {
                // Should not be allowed but union fs relies on it
                // also there is no such concept of a current directory for union paths
                // meaning absolute and relative paths should have the same effect,
                // so we just allow this.
                //throw new IllegalArgumentException("Different types of path");
            }
            var length = Math.min(this.pathParts.length, p.pathParts.length);
            int i = 0;
            while (i < length) {
                if (!Objects.equals(this.pathParts[i], p.pathParts[i]))
                    break;
                i++;
            }

            var remaining = this.pathParts.length - i;
            if (remaining == 0 && i == p.pathParts.length) {
                return new UnionPath(this.getFileSystem(), false);
            } else if (remaining == 0) {
                return p.subpath(i, p.getNameCount());
            } else {
                var updots = IntStream.range(0, remaining).mapToObj(idx -> "..").toArray(String[]::new);
                if (i == p.pathParts.length) {
                    return new UnionPath(this.getFileSystem(), false, updots);
                } else {
                    var subpath = p.subpath(i, p.getNameCount());
                    String[] mergedParts = new String[updots.length + subpath.pathParts.length];
                    System.arraycopy(updots, 0, mergedParts, 0, updots.length);
                    System.arraycopy(subpath.pathParts, 0, mergedParts, updots.length, subpath.pathParts.length);
                    return new UnionPath(this.getFileSystem(), false, mergedParts);
                }
            }
        }
        throw new IllegalArgumentException("Wrong filesystem");
    }

    @Override
    public URI toUri() {
        try {
            return new URI(
                fileSystem.provider().getScheme(),
                null,
                fileSystem.getKey() + '!' + toAbsolutePath(),
                null
            );
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Path toAbsolutePath() {
        if (isAbsolute())
            return this;
        else
            return fileSystem.getRoot().resolve(this);
    }

    @Override
    public Path toRealPath(final LinkOption... options) throws IOException {
        return this.toAbsolutePath().normalize();
    }

    @Override
    public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>[] events, final WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(final Path other) {
        if (other instanceof UnionPath path) {
            if (this.absolute && !path.absolute)
                return 1;
            else if (!this.absolute && path.absolute)
                return -1;
            else
                return Arrays.compare(this.pathParts, path.pathParts);
        } else {
            return 0;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof UnionPath p) {
            return p.getFileSystem() == this.getFileSystem() && this.absolute == p.absolute && Arrays.equals(this.pathParts, p.pathParts);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.fileSystem) + 31 * Arrays.hashCode(this.pathParts);
    }

    @Override
    public String toString() {
        return (this.absolute ? UnionFileSystem.SEP_STRING : "") + String.join(UnionFileSystem.SEP_STRING, this.pathParts);
    }
}
