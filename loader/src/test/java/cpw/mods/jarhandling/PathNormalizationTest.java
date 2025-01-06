package cpw.mods.jarhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PathNormalizationTest {
    @ParameterizedTest
    @CsvSource(textBlock = """
            path path
            path/ path
            /path/ path
            //path/ path
            path// path
            path/segment path/segment
            path//segment path/segment
            path//segment// path/segment
            path\\/segment// path/segment
            """, delimiter = ' ')
    public void testIsNormalized(String input, String expected) {
        assertEquals(expected, PathNormalization.normalize(input));
    }
}
