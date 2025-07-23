package cpw.mods.jarhandling.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PathNormalizationTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "dir/file",
            "d/f",
            ".d/.f",
            "d./f.",
            "f"
    })
    void testIsNormalizedForNormalizedPaths(String path) {
        assertTrue(PathNormalization.isNormalized(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".",
            "..",
            "./",
            "../",
            "./../",
            "./file",
            "../file",
            "./../file",
            "dir/./file",
            "dir/../file",
            "dir\\file",
            "dir//file",
            "/file",
            "dir/"
    })
    void testIsNormalizedForNonNormalizedPaths(String path) {
        assertFalse(PathNormalization.isNormalized(path));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
                    'dir\\file','dir/file'
                    'dir//file','dir/file'
                    '/file','file'
                    'dir/','dir'
            """)
    void testNormalization(String input, String expected) {
        assertEquals(expected, PathNormalization.normalize(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".",
            "..",
            "./",
            "../",
            "./../",
            "./file",
            "../file",
            "./../file",
            "dir/./file",
            "dir/../file"
    })
    void testRejectedNormalization(String input) {
        assertThrows(Exception.class, () -> PathNormalization.normalize(input));
    }
}
