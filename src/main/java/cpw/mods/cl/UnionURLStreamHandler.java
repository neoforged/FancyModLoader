package cpw.mods.cl;

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
        return u-> {
            try {
                return Files.newInputStream(Paths.get(u.toURI()));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }


        };
    }
}
