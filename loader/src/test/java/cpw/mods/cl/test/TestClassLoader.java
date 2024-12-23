package cpw.mods.cl.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestClassLoader {
    @Test
    public void testCpIsolation() throws Exception {
        // Make sure that classes that would normally be accessible via classpath...
        assertDoesNotThrow(() -> Class.forName("cpw.mods.testjar_cp.SomeClass"));

        // ...cannot be loaded via a ModuleClassLoader
        TestjarUtil.withTestjar1Setup(cl -> {
            assertThrows(ClassNotFoundException.class, () -> {
                Class.forName("cpw.mods.testjar_cp.SomeClass", true, cl);
            });
        });
    }
}
