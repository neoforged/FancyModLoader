package cpw.mods.cl;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

public class ModularURLHandler implements URLStreamHandlerFactory {
    public static final ModularURLHandler INSTANCE = new ModularURLHandler();
    private Map<String, IURLProvider> handlers;

    public static void initFrom(@Nullable ModuleLayer layer) {
        var handlers = new HashMap<String, IURLProvider>();

        // This handler is required for SJH to work.
        var unionHandler = new UnionURLStreamHandler();
        handlers.put(unionHandler.protocol(), unionHandler);

        if (layer == null) {
            // Support non-modular environment for testing purposes
            ServiceLoader.load(IURLProvider.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .forEach(handler -> handlers.putIfAbsent(handler.protocol(), handler));
        } else {
            ServiceLoader.load(layer, IURLProvider.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .forEach(handler -> handlers.putIfAbsent(handler.protocol(), handler));
        }

        INSTANCE.handlers = Map.copyOf(handlers);
    }

    @Override
    public URLStreamHandler createURLStreamHandler(final String protocol) {
        if (handlers == null) return null;
        if (handlers.containsKey(protocol)) {
            return new FunctionURLStreamHandler(handlers.get(protocol));
        }
        return null;
    }

    private static class FunctionURLStreamHandler extends URLStreamHandler {
        private final IURLProvider iurlProvider;

        public FunctionURLStreamHandler(final IURLProvider iurlProvider) {
            this.iurlProvider = iurlProvider;
        }

        @Override
        protected URLConnection openConnection(final URL u) throws IOException {
            return new FunctionURLConnection(u, this.iurlProvider);
        }
    }

    private static class FunctionURLConnection extends URLConnection {
        private final IURLProvider provider;

        protected FunctionURLConnection(final URL url, final IURLProvider provider) {
            super(url);
            this.provider = provider;
        }

        @Override
        public void connect() throws IOException {}

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return provider.inputStreamFunction().apply(url);
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }

        @Override
        public int getContentLength() {
            var length = getContentLengthLong();
            if (length < 0 || length > Integer.MAX_VALUE) {
                return -1;
            }
            return (int) length;
        }

        @Override
        public long getContentLengthLong() {
            return provider.getContentLength(url);
        }

        @Override
        public long getLastModified() {
            return provider.getLastModified(url);
        }
    }

    public interface IURLProvider {
        String protocol();

        Function<URL, InputStream> inputStreamFunction();

        default long getLastModified(URL url) {
            return 0;
        }

        default long getContentLength(URL url) {
            return -1;
        }
    }
}
