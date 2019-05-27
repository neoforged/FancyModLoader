package cpw.mods.gross.test;

import cpw.mods.gross.Java9ClassLoaderUtil;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class Java9ClassLoaderUtilTest {
    @Test
    void getSystemClassPathURLs() {
        final URL[] systemClassPathURLs = Java9ClassLoaderUtil.getSystemClassPathURLs();
        assertNotNull(systemClassPathURLs);
        assertTrue(systemClassPathURLs.length>0);
    }
}