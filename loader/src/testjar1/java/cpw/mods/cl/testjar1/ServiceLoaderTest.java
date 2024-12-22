package cpw.mods.cl.testjar1;

import java.util.ServiceLoader;

public class ServiceLoaderTest {
    /**
     * Requests a service loader with context set from this (SJH-loaded!) module.
     * Referenced by {@code TestjarUtil}.
     */
    public static <S> ServiceLoader<S> load(Class<S> service) {
        return ServiceLoader.load(service);
    }
}
