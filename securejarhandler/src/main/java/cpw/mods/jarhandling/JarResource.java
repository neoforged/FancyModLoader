package cpw.mods.jarhandling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public interface JarResource {
    InputStream open() throws IOException;

    default BufferedReader bufferedReader() throws IOException {
        return bufferedReader(StandardCharsets.UTF_8);
    }

    default BufferedReader bufferedReader(Charset charset) throws IOException {
        return new BufferedReader(new InputStreamReader(open(), charset));
    }

    default byte[] readAllBytes() throws IOException {
        try (var stream = open()) {
            return stream.readAllBytes();
        }
    }

    JarResourceAttributes attributes() throws IOException;

    /**
     * Create a copy of this jar resource reference that can be held onto.
     * <p>Useful in the context of {@link JarResourceVisitor}, where resource objects are reused between visits,
     * and copies must be made to hold onto resources for later use.
     */
    JarResource retain();
}
