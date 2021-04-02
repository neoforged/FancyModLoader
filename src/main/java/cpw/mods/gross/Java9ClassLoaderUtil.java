package cpw.mods.gross;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

/**
 * Literally from https://stackoverflow.com/questions/46519092/how-to-get-all-jars-loaded-by-a-java-application-in-java9
 */
public class Java9ClassLoaderUtil {
    @SuppressWarnings({"restriction", "unchecked"})
    public static URL[] getSystemClassPathURLs() {
        ClassLoader classLoader = Java9ClassLoaderUtil.class.getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            return ((URLClassLoader) classLoader).getURLs();
        }

        if (classLoader.getClass().getName().startsWith("jdk.internal.loader.ClassLoaders$")) {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                Unsafe unsafe = (Unsafe) field.get(null);

                // jdk.internal.loader.ClassLoaders.AppClassLoader.ucp
                Field ucpField = null;
                try {
                    ucpField = classLoader.getClass().getDeclaredField("ucp");
                } catch (NoSuchFieldException | SecurityException e) {
                    ucpField = classLoader.getClass().getSuperclass().getDeclaredField("ucp");
                }

                long ucpFieldOffset = unsafe.objectFieldOffset(ucpField);
                Object ucpObject = unsafe.getObject(classLoader, ucpFieldOffset);

                // jdk.internal.loader.URLClassPath.path
                Field pathField = ucpField.getType().getDeclaredField("path");
                long pathFieldOffset = unsafe.objectFieldOffset(pathField);
                ArrayList<URL> path = (ArrayList<URL>) unsafe.getObject(ucpObject, pathFieldOffset);

                return path.toArray(new URL[0]);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to find system class path URLs. Incompatible JDK?", e);
            }
        }
        return null;
    }
}
