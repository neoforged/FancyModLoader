package cpw.mods.niofs.pathfs;

import cpw.mods.util.LambdaExceptionUtils;
import cpw.mods.util.Lazy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PathFileSystem extends FileSystem
{
    private final Path               root            = new PathPath(this, false, PathPath.ROOT).toAbsolutePath();
    private final PathFileSystemProvider provider;
    private final String                 key;

    private final Path                   target;

    private final Lazy<FileSystem> innerSystem;
    private final Lazy<Path>       innerFSTarget;

    public PathFileSystem(final PathFileSystemProvider provider, final String key, final Path target)
    {
        this.provider = provider;
        this.key = key;
        this.target = target;

        this.innerSystem = Lazy.of(() -> {
            try
            {
                return FileSystems.newFileSystem(target);
            }
            catch (Exception e)
            {
                return target.getFileSystem();
            }
        });

        this.innerFSTarget = this.innerSystem.map(fileSystem -> {
            //We need to process the new FS root directories.
            //We do this since creating an FS from a zip file changes the root to which we need to make our inner paths relative.
            final List<Path> possibleRootDirectories = new ArrayList<>();
            fileSystem.getRootDirectories().forEach(possibleRootDirectories::add);

            if (possibleRootDirectories.size() > 0) {
                for (final Path possibleRootDirectory : possibleRootDirectories)
                {
                    if (possibleRootDirectory.getClass() != target.getClass()) {
                        return possibleRootDirectory;
                    }
                }
            }

            return target;
        });
    }

    String getKey()
    {
        return this.key;
    }

    public Path getRoot()
    {
        return root;
    }

    @Override
    public PathFileSystemProvider provider()
    {
        return provider;
    }

    @Override
    public void close()
    {
        innerSystem.ifPresent(LambdaExceptionUtils.uncheckConsume(FileSystem::close));
        provider().removeFileSystem(this);
    }

    @Override
    public boolean isOpen()
    {
        return innerSystem.map(FileSystem::isOpen).orElse(true);
    }

    @Override
    public boolean isReadOnly()
    {
        return true;
    }

    public <A extends BasicFileAttributes> A readAttributes(final Path path, final Class<A> type, final LinkOption... options) throws IOException
    {
        if (path.toAbsolutePath().equals(root)) {
            return Files.readAttributes(this.target, type, options);
        }

        return innerSystem.get().provider().readAttributes(getOuterTarget(path), type, options);
    }

    @Override
    public String getSeparator()
    {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories()
    {
        return Collections.singletonList(root);
    }

    @Override
    public Iterable<FileStore> getFileStores()
    {
        return List.of();
    }

    @Override
    public Set<String> supportedFileAttributeViews()
    {
        return Set.of("basic");
    }

    public SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options, final FileAttribute<?>... attrs) throws IOException
    {
        if (path.toAbsolutePath().equals(root)) {
            try
            {
                return Files.newByteChannel(this.target, options, attrs);
            }
            catch (UncheckedIOException ioe)
            {
                throw ioe.getCause();
            }
        }

        return this.innerSystem.get().provider().newByteChannel(getOuterTarget(path), options, attrs);
    }

    private Path getOuterTarget(Path path)
    {
        if (path.isAbsolute())
            path = root.relativize(path);

        final Path finalPath = path;
        return this.innerFSTarget.map(innerTarget -> innerTarget.resolve(finalPath.toString())).get();
    }

    @Override
    public Path getPath(final String first, final String... more)
    {
        if (more.length > 0)
        {
            var args = new String[more.length + 1];
            args[0] = first;
            System.arraycopy(more, 0, args, 1, more.length);

            return provider().createSubPath(this, args);
        }
        return provider().createSubPath(this, first);
    }

    @Override
    public PathMatcher getPathMatcher(final String syntaxAndPattern)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService()
    {
        throw new UnsupportedOperationException();
    }

    public DirectoryStream<Path> newDirectoryStream(final Path dir, final DirectoryStream.Filter<? super Path> filter)
    {
        if (dir.toAbsolutePath().equals(root)) {
            try
            {
                return PathFSUtils.adapt(
                  Files.newDirectoryStream(this.innerFSTarget.get(), filter),
                  path -> new PathPath(this, this.innerFSTarget.get().relativize(path))
                );
            }
            catch (IOException e)
            {
                return PathFSUtils.NULL_STREAM;
            }
        }

        try
        {
            return PathFSUtils.adapt(
              this.innerSystem.get().provider().newDirectoryStream(getOuterTarget(dir), filter),
              path -> new PathPath(this, target.relativize(path))
            );
        }
        catch (IOException e)
        {
            return PathFSUtils.NULL_STREAM;
        }
    }

    public Path getTarget()
    {
        return target;
    }
}
