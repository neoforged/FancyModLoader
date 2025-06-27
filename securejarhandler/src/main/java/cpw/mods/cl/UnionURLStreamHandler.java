package cpw.mods.cl;

import cpw.mods.niofs.union.UnionPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Function;

public class UnionURLStreamHandler implements ModularURLHandler.IURLProvider {
    @Override
    public String protocol() {
        return "union";
    }

    @Override
    public Function<URL, InputStream> inputStreamFunction() {
        return u -> {
            try {
                if (Paths.get(u.toURI()) instanceof UnionPath upath) {
                    return upath.buildInputStream();
                } else {
                    throw new IllegalArgumentException("Invalid Path " + u.toURI() + " at UnionURLStreamHandler");
                }
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

        };
    }

    @Override
    public long getLastModified(URL u) {
        try {
            return Files.getLastModifiedTime(Paths.get(u.toURI())).toMillis();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long getContentLength(URL u) {
        try {
            return Files.size(Paths.get(u.toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
