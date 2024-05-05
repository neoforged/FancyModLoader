package net.neoforged.fml.test;

import net.neoforged.fml.i18n.FMLTranslations;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class TranslationPatternsTest {
    @Test
    void testVanillaFormatSpecifiers() {
        Assertions.assertThat(FMLTranslations.parseFormat("this is a translation %s %s", "a", "b"))
                .isEqualTo("this is a translation a b");

        Assertions.assertThat(FMLTranslations.parseFormat("this is a translation %2$s %1$s", "a", "b"))
                .isEqualTo("this is a translation b a");
   }

   @Test
   void testDoublePercent() {
       Assertions.assertThat(FMLTranslations.parseFormat("%%")).isEqualTo("%");
   }
}
