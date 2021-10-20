package cpw.mods.niofs.pathfs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Function;

class PathFSUtils
{

    private PathFSUtils()
    {
        throw new IllegalStateException("Can not instantiate an instance of: PathFSUtils. This is a utility class");
    }

    public static final DirectoryStream<Path> NULL_STREAM = new DirectoryStream<>()
    {
        @Override
        public Iterator<Path> iterator()
        {
            return new Iterator<>()
            {
                @Override
                public boolean hasNext()
                {
                    return false;
                }

                @Override
                public Path next()
                {
                    return null;
                }
            };
        }

        @Override
        public void close() throws IOException
        {

        }
    };

    public static DirectoryStream<Path> adapt(final DirectoryStream<Path> inner, final Function<Path, Path> adapter) {
        return new DirectoryStream<Path>() {
            @Override
            public Iterator<Path> iterator()
            {
                final Iterator<Path> targetIterator = inner.iterator();

                return new Iterator<Path>() {
                    @Override
                    public boolean hasNext()
                    {
                        return targetIterator.hasNext();
                    }

                    @Override
                    public Path next()
                    {
                        final Path targetPath = targetIterator.next();
                        return adapter.apply(targetPath);
                    }
                };
            }

            @Override
            public void close() throws IOException
            {
                inner.close();
            }
        };
    }
}
