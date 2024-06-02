/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.i18n;

import com.google.common.base.CharMatcher;
import java.nio.file.Path;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.neoforged.fml.Logging;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.StringUtils;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.commons.lang3.text.ExtendedMessageFormat;
import org.apache.commons.lang3.text.FormatFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class FMLTranslations {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String ALLOWED_CHARS = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u0000";
    private static final CharMatcher DISALLOWED_CHAR_MATCHER = CharMatcher.anyOf(ALLOWED_CHARS).negate();
    private static final Map<String, FormatFactory> CUSTOM_FACTORIES;
    private static final Pattern PATTERN_CONTROL_CODE = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    static {
        CUSTOM_FACTORIES = new HashMap<>();
        // {0,modinfo,id} -> modid from ModInfo object; {0,modinfo,name} -> displayname from ModInfo object
        CUSTOM_FACTORIES.put("modinfo", (name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, objectToParse) -> parseModInfo(formatString, stringBuffer, objectToParse)));
        // {0,lower} -> lowercase supplied string
        CUSTOM_FACTORIES.put("lower", (name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, objectToParse) -> stringBuffer.append(StringUtils.toLowerCase(String.valueOf(objectToParse)))));
        // {0,upper> -> uppercase supplied string
        CUSTOM_FACTORIES.put("upper", (name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, objectToParse) -> stringBuffer.append(StringUtils.toUpperCase(String.valueOf(objectToParse)))));
        // {0,exc,cls} -> class of exception; {0,exc,msg} -> message from exception
        CUSTOM_FACTORIES.put("exc", (name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, objectToParse) -> parseException(formatString, stringBuffer, objectToParse)));
        // {0,vr} -> transform VersionRange into cleartext string using fml.messages.version.restriction.* strings
        CUSTOM_FACTORIES.put("vr", (name, formatString, locale) -> new CustomReadOnlyFormat(MavenVersionTranslator::parseVersionRange));
        // {0,featurebound} -> transform feature bound to cleartext string
        CUSTOM_FACTORIES.put("featurebound", (name, formatString, locale) -> new CustomReadOnlyFormat(MavenVersionTranslator::parseFeatureBoundValue));
        // {0,i18n,fml.message} -> pass object to i18n string 'fml.message'
        CUSTOM_FACTORIES.put("i18n", (name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, o) -> stringBuffer.append(parseMessage(formatString, o))));
        // {0,i18ntranslate} -> attempt to use the argument as a translation key
        CUSTOM_FACTORIES.put("i18ntranslate", (name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, o) -> stringBuffer.append(parseMessage((String) o))));
        // {0,ornull,fml.absent} -> append String value of o, or i18n string 'fml.absent' (message format transforms nulls into the string literal "null")
        CUSTOM_FACTORIES.put("ornull", ((name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, o) -> stringBuffer.append(Objects.equals(String.valueOf(o), "null") ? parseMessage(formatString) : String.valueOf(o)))));
        // {0,optional,[prefix]} -> append String value of o if the optional is present, with an optional prefix at the start
        CUSTOM_FACTORIES.put("optional", (name, formatString, locale) -> new CustomReadOnlyFormat((stringBuffer, o) -> ((Optional<?>) o).ifPresent(val -> stringBuffer.append(formatString == null ? "" : formatString).append(val))));
    }

    private static void parseException(final String formatString, final StringBuffer stringBuffer, final Object objectToParse) {
        Throwable t = (Throwable) objectToParse;
        if (Objects.equals(formatString, "msg")) {
            stringBuffer.append(t.getClass().getName()).append(": ").append(t.getMessage());
        } else if (Objects.equals(formatString, "cls")) {
            stringBuffer.append(t.getClass().getName());
        }
    }

    private static void parseModInfo(final String formatString, final StringBuffer stringBuffer, final Object modInfo) {
        final IModInfo info = (IModInfo) modInfo;
        if (Objects.equals(formatString, "id")) {
            stringBuffer.append(info.getModId());
        } else if (Objects.equals(formatString, "name")) {
            stringBuffer.append(info.getDisplayName());
        }
    }

    public static String getPattern(final String patternName, final Supplier<String> fallback) {
        final var translated = I18nManager.currentLocale.get(patternName);
        return translated == null ? fallback.get() : translated;
    }

    public static String parseMessage(final String i18nMessage, Object... args) {
        return parseMessageWithFallback(i18nMessage, () -> i18nMessage, args);
    }

    public static String parseMessageWithFallback(final String i18nMessage, final Supplier<String> fallback, Object... args) {
        final String pattern = getPattern(i18nMessage, fallback);
        try {
            return parseFormat(pattern, args);
        } catch (IllegalArgumentException e) {
            LOGGER.error(Logging.CORE, "Illegal format found `{}`", pattern);
            return pattern;
        }
    }

    public static String parseEnglishMessage(final String i18n, Object... args) {
        var translated = I18nManager.DEFAULT_TRANSLATIONS.getOrDefault(i18n, i18n);
        try {
            return parseFormat(translated, args);
        } catch (IllegalArgumentException e) {
            LOGGER.error(Logging.CORE, "Illegal format found `{}`", translated);
            return translated;
        }
    }

    public static String parseFormat(String format, final Object... args) {
        final AtomicInteger i = new AtomicInteger();
        // Converts Mojang translation format (%s) to the one used by Apache Commons ({0})
        format = FORMAT_PATTERN.matcher(format).replaceAll(matchResult -> {
            if (matchResult.group(0).equals("%%")) {
                return "%";
            }
            final String groupIdx = matchResult.group(1);
            final int index = groupIdx != null ? Integer.parseInt(groupIdx) - 1 : i.getAndIncrement();
            return "{" + index + "}";
        });
        final ExtendedMessageFormat extendedMessageFormat = new ExtendedMessageFormat(format, CUSTOM_FACTORIES);
        return extendedMessageFormat.format(args);
    }

    public static String translateIssueEnglish(ModLoadingIssue issue) {
        var args = getTranslationArgs(issue);
        return parseEnglishMessage(issue.translationKey(), args);
    }

    public static String translateIssue(ModLoadingIssue issue) {
        var args = getTranslationArgs(issue);
        return parseMessage(issue.translationKey(), args);
    }

    private static Object[] getTranslationArgs(ModLoadingIssue issue) {
        var args = new ArrayList<>(3 + issue.translationArgs().size());

        var modInfo = issue.affectedMod();
        var file = issue.affectedModFile();
        while (modInfo == null && file != null) {
            if (!file.getModInfos().isEmpty()) {
                modInfo = file.getModInfos().getFirst();
            }
            file = file.getDiscoveryAttributes().parent();
        }
        args.add(modInfo);
        args.add(null); // Previously mod-loading phase
        // For errors, we expose the cause
        if (issue.severity() == ModLoadingIssue.Severity.ERROR) {
            args.add(issue.cause());
        }
        args.addAll(issue.translationArgs());

        args.replaceAll(FMLTranslations::formatArg);

        return args.toArray(Object[]::new);
    }

    private static Object formatArg(Object arg) {
        if (arg instanceof Path path) {
            var gameDir = FMLLoader.getGamePath();
            if (gameDir != null && path.startsWith(gameDir)) {
                return gameDir.relativize(path).toString();
            } else {
                return path.toString();
            }
        } else {
            return arg;
        }
    }

    public static String stripControlCodes(String text) {
        return PATTERN_CONTROL_CODE.matcher(text).replaceAll("");
    }

    public static class CustomReadOnlyFormat extends Format {
        private final BiConsumer<StringBuffer, Object> formatter;

        CustomReadOnlyFormat(final BiConsumer<StringBuffer, Object> formatter) {
            this.formatter = formatter;
        }

        @Override
        public StringBuffer format(final Object obj, final StringBuffer toAppendTo, final FieldPosition pos) {
            formatter.accept(toAppendTo, obj);
            return toAppendTo;
        }

        @Override
        public Object parseObject(final String source, final ParsePosition pos) {
            throw new UnsupportedOperationException("Parsing is not supported");
        }
    }
}
