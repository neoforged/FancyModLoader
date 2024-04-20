/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.neoforged.fml.loading.EarlyLoadingException;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * General purpose mod loading error message
 */
public class ModLoadingException extends RuntimeException {
    private static final long serialVersionUID = 2048947398536935507L;
    /**
     * Mod Info for mod with issue
     */
    private final IModInfo modInfo;

    /**
     * I18N message to use for display
     */
    private final String i18nMessage;

    /**
     * Context for message display
     */
    private final List<Object> context;

    public ModLoadingException(final IModInfo modInfo, final String i18nMessage, final Throwable originalException, Object... context) {
        super("Mod Loading Exception", originalException);
        this.modInfo = modInfo;
        this.i18nMessage = i18nMessage;
        this.context = Arrays.asList(context);
    }

    static Stream<ModLoadingException> fromEarlyException(final EarlyLoadingException e) {
        return e.getAllData().stream().map(ed -> new ModLoadingException(ed.getModInfo(), ed.getI18message(), e.getCause(), ed.getArgs()));
    }

    public String getI18NMessage() {
        return i18nMessage;
    }

    public Object[] getContext() {
        return context.toArray();
    }

    public String formatToString() {
        // TODO: cleanup null here - this requires moving all indices in the translations
        return Bindings.getMessageParser().parseMessage(i18nMessage, Streams.concat(Stream.of(modInfo, null, getCause()), context.stream()).toArray());
    }

    @Override
    public String getMessage() {
        return formatToString();
    }

    public IModInfo getModInfo() {
        return modInfo;
    }

    public String getCleanMessage() {
        return Bindings.getMessageParser().stripControlCodes(formatToString());
    }
}
