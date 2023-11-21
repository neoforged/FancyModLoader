package cpw.mods.testjar_cp;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

/**
 * Referenced by {@code TestServiceLoader}.
 */
public class DummyURLStreamHandlerProvider extends URLStreamHandlerProvider {
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        return null;
    }
}
