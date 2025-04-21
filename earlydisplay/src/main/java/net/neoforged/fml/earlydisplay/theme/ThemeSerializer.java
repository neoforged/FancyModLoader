/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeDecorativeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import net.neoforged.fml.earlydisplay.util.StyleLength;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ThemeSerializer {
    private static final Logger LOG = LoggerFactory.getLogger(ThemeSerializer.class);
    private static final int VERSION = 1;

    private ThemeSerializer() {}

    public static Theme load(Path baseDirectory, String id) throws IOException {
        var sources = new LinkedHashSet<String>();
        var themeTree = readThemeTree(baseDirectory, id, sources);

        try {
            return createGson(baseDirectory, false).fromJson(themeTree, Theme.class);
        } catch (Exception e) {
            throw new IOException("Failed to load theme '" + id + "' from JSON structure.", e);
        }
    }

    private static JsonObject readThemeTree(Path baseDirectory, String id, Set<String> sources) throws IOException {
        if (!sources.add(id)) {
            throw new IllegalStateException("Detected recursion in theme extends clause: " + sources + " -> " + id);
        }

        String filename = getThemeFilename(id);

        Path themePath = baseDirectory.resolve(filename);
        try (var in = Files.newInputStream(themePath)) {
            LOG.debug("Loading theme from {}", themePath);
            return readThemeTree(baseDirectory, in, sources);
        } catch (NoSuchFileException ignored) {}

        // Try to load it from the classpath instead
        String classpathLocation = "/net/neoforged/fml/earlydisplay/theme/" + filename;
        try (var in = ThemeSerializer.class.getResourceAsStream(classpathLocation)) {
            LOG.debug("Loading built-in theme {}", id);
            if (in == null) {
                throw new NoSuchFileException("Failed to find embedded theme resource " + classpathLocation);
            }
            return readThemeTree(baseDirectory, in, sources);
        }
    }

    private static JsonObject readThemeTree(Path baseDirectory, InputStream in, Set<String> sources) throws IOException {
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        var themeRoot = createGson(baseDirectory, false).fromJson(reader, JsonObject.class);
        var themeVersion = takeInt(themeRoot, "version");
        if (themeVersion == null || themeVersion != VERSION) {
            throw new JsonParseException("Expected theme version " + VERSION + " but found: " + themeVersion);
        }

        var extendsId = takeString(themeRoot, "extends");
        if (extendsId != null) {
            var baseThemeRoot = readThemeTree(baseDirectory, extendsId, sources);
            themeRoot = mergeThemeRoot(baseThemeRoot, themeRoot);
        }

        return themeRoot;
    }

    private static JsonObject mergeThemeRoot(JsonObject baseThemeRoot, JsonObject themeRoot) {
        return mergeObject(baseThemeRoot, themeRoot, (property, baseValue, value) -> switch (property) {
            case "fonts", "shaders", "colorScheme", "sprites" -> mergeObject(baseValue, value);
            case "loadingScreen" -> mergeObject(baseValue, value, ThemeSerializer::mergeLoadingScreenProperty);
            default -> value;
        });
    }

    private static JsonElement mergeLoadingScreenProperty(String property, JsonElement baseValue, JsonElement value) {
        // Just recursively merge every property of the loading screen object
        return mergeObject(baseValue, value);
    }

    private static JsonObject mergeObject(JsonElement baseObject, JsonElement object) {
        return mergeObject(baseObject, object, (property, baseValue, value) -> value);
    }

    /**
     * Simple merge function that copies all entries from object into baseObject, overwriting
     * existing entries.
     */
    private static JsonObject mergeObject(JsonElement baseObject,
            JsonElement object,
            PropertyMerger propertyMerger) {
        var objectObj = object.getAsJsonObject();
        var baseObjectObj = baseObject.getAsJsonObject();

        for (var entry : objectObj.entrySet()) {
            var baseValue = baseObjectObj.get(entry.getKey());
            if (baseValue == null) {
                baseObjectObj.add(entry.getKey(), entry.getValue());
            } else {
                baseObjectObj.add(entry.getKey(), propertyMerger.map(entry.getKey(), baseValue, entry.getValue()));
            }
        }

        return baseObjectObj;
    }

    @FunctionalInterface
    private interface PropertyMerger {
        JsonElement map(String property, JsonElement baseValue, JsonElement value);
    }

    private static String getThemeFilename(String id) {
        return "theme-" + id + ".json";
    }

    @Nullable
    private static Integer takeInt(JsonElement el, String field) {
        var primitive = takePrimitive(el, field);
        return primitive == null ? null : primitive.getAsInt();
    }

    @Nullable
    private static String takeString(JsonElement el, String field) {
        var primitive = takePrimitive(el, field);
        return primitive == null ? null : primitive.getAsString();
    }

    private static JsonPrimitive takePrimitive(JsonElement el, String field) {
        if (!el.isJsonObject()) {
            throw new JsonParseException("Expected  " + el + " to be an object.");
        }
        var obj = (JsonObject) el;
        var v = obj.remove(field);
        if (v == null) {
            return null;
        }
        if (!(v instanceof JsonPrimitive primitive)) {
            throw new JsonParseException("Expected " + field + " of " + el + " to be a primitive");
        }
        return primitive;
    }

    public static void save(Path path, Theme theme, boolean exportResources) {
        LOG.info("Saving theme to {}", path);

        Gson gson = createGson(path.toAbsolutePath().getParent(), exportResources);
        var themeTree = (JsonObject) gson.toJsonTree(theme);
        var merged = new JsonObject();
        merged.addProperty("version", VERSION);
        for (var entry : themeTree.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }

        try (var out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(merged, out);
        } catch (IOException e) {
            LOG.error("Failed to save theme to {}", path, e);
        }
    }

    private static Gson createGson(Path baseDirectory, boolean exportResources) {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(TextureScaling.class, new TextureScalingSerializer())
                .registerTypeAdapterFactory(new ThemeElementAdapterFactory())
                .registerTypeHierarchyAdapter(ThemeResource.class, new ThemeResourceAdapter(baseDirectory, exportResources))
                .registerTypeAdapter(UncompressedImage.class, new UncompressedImageSerializer())
                .registerTypeAdapter(StyleLength.class, new StyleLengthAdapter())
                .registerTypeAdapter(ThemeColor.class, new ThemeColorAdapter())
                .create();
    }

    private static class StyleLengthAdapter extends TypeAdapter<StyleLength> {
        @Override
        public void write(JsonWriter out, StyleLength value) throws IOException {
            switch (value.unit()) {
                case UNDEFINED -> out.nullValue();
                case POINT -> out.value(value.value());
                case REM -> out.value(value.value() + "rem");
                case PERCENT -> out.value(value.value() + "%");
            }
        }

        @Override
        public StyleLength read(JsonReader in) throws IOException {
            return switch (in.peek()) {
                case NULL -> StyleLength.ofUndefined();
                case STRING -> {
                    var value = in.nextString();
                    if (value.endsWith("%")) {
                        yield StyleLength.ofPercent(Float.parseFloat(value.substring(0, value.length() - 1)));
                    } else if (value.endsWith("rem")) {
                        yield StyleLength.ofREM(Float.parseFloat(value.substring(0, value.length() - 3)));
                    } else {
                        throw new JsonParseException("Unexpected value: " + value);
                    }
                }
                case NUMBER -> StyleLength.ofPoints((float) in.nextDouble());
                default -> throw new JsonParseException("Unexpected token type @ " + in.getPath());
            };
        }
    }

    private static class UncompressedImageSerializer implements JsonSerializer<UncompressedImage>, JsonDeserializer<UncompressedImage> {
        @Override
        public UncompressedImage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            var resource = (ThemeResource) context.deserialize(json, ThemeResource.class);
            return resource.loadAsImage();
        }

        @Override
        public JsonElement serialize(UncompressedImage value, Type typeOfSrc, JsonSerializationContext context) {
            if (value.source() != null) {
                return context.serialize(value.source());
            }
            return JsonNull.INSTANCE;
        }
    }

    private static class ThemeResourceAdapter extends TypeAdapter<ThemeResource> {
        private final Path baseDirectory;
        private final boolean exportResources;

        public ThemeResourceAdapter(Path baseDirectory, boolean exportResources) {
            this.baseDirectory = baseDirectory;
            this.exportResources = exportResources;
        }

        @Override
        public void write(JsonWriter out, ThemeResource value) throws IOException {
            switch (value) {
                case ClasspathResource classpathResource -> {
                    if (exportResources) {
                        var idx = Math.max(
                                classpathResource.path().lastIndexOf('/'),
                                classpathResource.path().lastIndexOf('\\'));
                        var filename = classpathResource.path().substring(idx + 1);
                        var diskPath = baseDirectory.resolve(filename);
                        try (var buffer = value.toNativeBuffer()) {
                            Files.write(diskPath, buffer.toByteArray());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        out.value(filename);
                    } else {
                        out.value("classpath:" + classpathResource.path());
                    }
                }
                case FileResource fileResource -> {
                    var diskPath = baseDirectory.resolve(fileResource.file().getName());
                    Files.copy(fileResource.file().toPath(), diskPath, StandardCopyOption.REPLACE_EXISTING);
                    out.value(fileResource.file().getName());
                }
            }
        }

        @Override
        public ThemeResource read(JsonReader in) throws IOException {
            var text = in.nextString();
            if (text.startsWith("classpath:")) {
                return new ClasspathResource(text.substring("classpath:".length()));
            }
            return new FileResource(baseDirectory.resolve(text).toFile());
        }
    }

    private static class ThemeColorAdapter extends TypeAdapter<ThemeColor> {
        @Override
        public void write(JsonWriter out, ThemeColor value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                String hexColor;
                if (value.a() == 1) {
                    hexColor = Integer.toHexString(value.toArgb() & 0xFFFFFF);
                    hexColor = "#" + "0".repeat(Math.max(0, 6 - hexColor.length())) + hexColor;
                } else {
                    hexColor = Integer.toHexString(value.toArgb());
                    hexColor = "#" + "0".repeat(Math.max(0, 8 - hexColor.length())) + hexColor;
                }
                out.value(hexColor);
            }
        }

        @Override
        public ThemeColor read(JsonReader in) throws IOException {
            var text = in.nextString();
            if (!text.startsWith("#")) {
                throw new JsonParseException("Cannot parse theme color value '" + text + "'");
            }
            text = text.substring(1);
            if (text.length() <= 6) {
                return ThemeColor.ofRgb(Integer.parseUnsignedInt(text, 16));
            } else {
                return ThemeColor.ofArgb(Integer.parseUnsignedInt(text, 16));
            }
        }
    }

    private static class TextureScalingSerializer implements JsonSerializer<TextureScaling>, JsonDeserializer<TextureScaling> {
        @Override
        public TextureScaling deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            var type = ((JsonObject) json).getAsJsonPrimitive("type").getAsString();
            return switch (type) {
                case "nine_slice" -> context.deserialize(json, TextureScaling.NineSlice.class);
                case "stretch" -> context.deserialize(json, TextureScaling.Stretch.class);
                case "tile" -> context.deserialize(json, TextureScaling.Tile.class);
                default -> throw new JsonParseException("Unknown image type " + type);
            };
        }

        @Override
        public JsonElement serialize(TextureScaling src, Type typeOfSrc, JsonSerializationContext context) {
            var obj = new JsonObject();
            obj.addProperty("type", switch (src) {
                case TextureScaling.NineSlice ignored -> "nine_slice";
                case TextureScaling.Stretch ignored -> "stretch";
                case TextureScaling.Tile ignored -> "tile";
            });
            for (var entry : ((JsonObject) context.serialize(src, src.getClass())).entrySet()) {
                if ("type".equals(entry.getKey())) {
                    throw new IllegalStateException("Cannot serialize texture scaling with 'type' property");
                }
                obj.add(entry.getKey(), entry.getValue());
            }
            return obj;
        }
    }

    private static class ThemeElementAdapterFactory implements TypeAdapterFactory {
        private static final Map<String, Class<? extends ThemeDecorativeElement>> TYPE_MAP = Map.of(
                "image", ThemeImageElement.class,
                "label", ThemeLabelElement.class);

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type == null) {
                return null;
            }
            if (!ThemeDecorativeElement.class.isAssignableFrom(type.getRawType())) {
                return null;
            }

            TypeAdapter<JsonElement> jsonElementAdapter = gson.getAdapter(JsonElement.class);
            Map<String, TypeAdapter<? extends ThemeDecorativeElement>> labelToDelegate = new HashMap<>();
            Map<Class<?>, TypeAdapter<? extends ThemeDecorativeElement>> subtypeToDelegate = new HashMap<>();
            Map<Class<?>, String> subtypeToLabel = new HashMap<>();
            for (var entry : TYPE_MAP.entrySet()) {
                var delegate = gson.getDelegateAdapter(this, TypeToken.get(entry.getValue()));
                labelToDelegate.put(entry.getKey(), delegate);
                subtypeToDelegate.put(entry.getValue(), delegate);
                subtypeToLabel.put(entry.getValue(), entry.getKey());
            }

            return (TypeAdapter<T>) new TypeAdapter<ThemeDecorativeElement>() {
                @Override
                public ThemeDecorativeElement read(JsonReader in) throws IOException {
                    var jsonElement = jsonElementAdapter.read(in);
                    var labelJsonElement = jsonElement.getAsJsonObject().remove("type");

                    if (labelJsonElement == null) {
                        throw new JsonParseException("cannot deserialize theme element because it does not define a type field at " + in.getPath());
                    }

                    String label = labelJsonElement.getAsString();
                    var delegate = labelToDelegate.get(label);
                    if (delegate == null) {
                        throw new JsonParseException(
                                "unknown theme element type '" + label + "'. known types: " + labelToDelegate.keySet());
                    }
                    return delegate.fromJsonTree(jsonElement);
                }

                @Override
                public void write(JsonWriter out, ThemeDecorativeElement value) throws IOException {
                    Class<? extends ThemeDecorativeElement> srcType = value.getClass();
                    String label = subtypeToLabel.get(srcType);
                    // The registration in this map guarantees the type bound of the key equals that of the value
                    var delegate = (TypeAdapter<ThemeDecorativeElement>) subtypeToDelegate.get(srcType);
                    if (delegate == null) {
                        throw new JsonParseException("cannot serialize theme element " + srcType.getName());
                    }
                    JsonObject jsonObject = delegate.toJsonTree(value).getAsJsonObject();

                    JsonObject clone = new JsonObject();

                    if (jsonObject.has("type")) {
                        throw new JsonParseException("theme element " + value + " must not define its own type field");
                    }
                    clone.add("type", new JsonPrimitive(label));
                    for (var e : jsonObject.entrySet()) {
                        clone.add(e.getKey(), e.getValue());
                    }
                    jsonElementAdapter.write(out, clone);
                }
            }.nullSafe();
        }
    }
}
