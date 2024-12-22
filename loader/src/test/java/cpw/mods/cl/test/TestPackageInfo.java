package cpw.mods.cl.test;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestPackageInfo {
    @Test
    public void testPackageInfoAvailability() throws Exception {
        // package-info classes can be loaded
        TestjarUtil.withTestjar1Setup(cl -> {
            String annotationType = "cpw.mods.cl.testjar1.TestAnnotation";
            // Reference package through a class to ensure correct behavior of ModuleClassLoader#findClass(String,String)
            Class<?> cls = Class.forName("cpw.mods.cl.testjar1.SomeClass", true, cl);
            Annotation[] annotations = cls.getPackage().getDeclaredAnnotations();
            Assertions.assertTrue(Arrays.stream(annotations).anyMatch(ann -> ann.annotationType().getName().equals(annotationType)), "Expected package to be annotated with " + annotationType);
        });
    }
}
