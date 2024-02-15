/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery;

import com.google.common.base.Supplier;
import net.neoforged.fml.Bindings;
import net.neoforged.fml.I18NParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ModInfoTest {

    private static final String NAME_KEY = "fml.menu.mods.info.name.";
    private static final String DESCRIPTION_KEY = "fml.menu.mods.info.description.";

    private MockedStatic<Bindings> mockedBindings;
    private @Mock ModInfo modInfo;
    private @Mock I18NParser parser;

    @BeforeEach
    public void setup() {
        mockedBindings = mockStatic(Bindings.class);
        mockedBindings.when(Bindings::getMessageParser).thenReturn((Supplier<I18NParser>) () -> parser);
    }

    @AfterEach
    public void teardown() {
        mockedBindings.close();
    }

    @Test
    @DisplayName("Use translated name if present")
    public void testTranslatedDisplayName() {
        String id = "modid";
        String i18n = NAME_KEY + id;
        String expected = "Translated name";

        when(parser.parseMessage(i18n)).thenReturn(expected);
        when(modInfo.getModId()).thenReturn(id);
        when(modInfo.getDisplayNameTranslated()).thenCallRealMethod();

        assertEquals(expected, modInfo.getDisplayNameTranslated());
    }

    @Test
    @DisplayName("Use translated description if present")
    public void testTranslatedDescription() {
        String id = "modid";
        String i18n = DESCRIPTION_KEY + id;
        String expected = "Translated description";

        when(parser.parseMessage(i18n)).thenReturn(expected);
        when(modInfo.getModId()).thenReturn(id);
        when(modInfo.getDescriptionTranslated()).thenCallRealMethod();

        assertEquals(expected, modInfo.getDescriptionTranslated());
    }

    @Test
    @DisplayName("Use untranslated name when translation fails")
    public void testUntranslatedDisplayName() {
        String id = "modid";
        String i18n = NAME_KEY + id;
        String expected = "Raw name";

        // parser returns i18n key when translation is not found
        when(parser.parseMessage(i18n)).thenReturn(i18n);

        when(modInfo.getModId()).thenReturn(id);
        when(modInfo.getDisplayName()).thenReturn(expected);
        when(modInfo.getDisplayNameTranslated()).thenCallRealMethod();

        assertEquals(expected, modInfo.getDisplayNameTranslated());
    }

    @Test
    @DisplayName("Use untranslated description when translation fails")
    public void testUntranslatedDescription() {
        String id = "modid";
        String i18n = DESCRIPTION_KEY + id;
        String expected = "Raw description";

        // parser returns i18n key when translation is not found
        when(parser.parseMessage(i18n)).thenReturn(i18n);

        when(modInfo.getModId()).thenReturn(id);
        when(modInfo.getDescription()).thenReturn(expected);
        when(modInfo.getDescriptionTranslated()).thenCallRealMethod();

        assertEquals(expected, modInfo.getDescriptionTranslated());
    }
}
