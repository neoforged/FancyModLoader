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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeImageElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeLabelElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemePerformanceElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeProgressBarsElement;
import net.neoforged.fml.earlydisplay.theme.elements.ThemeStartupLogElement;
import net.neoforged.fml.earlydisplay.util.StyleLength;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiStatus.Internal
public final class ThemeSerializer {
    private static final Logger LOG = LoggerFactory.getLogger(ThemeSerializer.class);

    private ThemeSerializer() {}

    public static Theme load(Path path) throws IOException {
        try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return createGson(path.toAbsolutePath().getParent()).fromJson(in, Theme.class);
        }
    }

    public static void save(Path path, Theme theme) {
        LOG.info("Saving theme to {}", path);
        try (var out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            createGson(path.toAbsolutePath().getParent()).toJson(theme, out);
        } catch (IOException e) {
            LOG.error("Failed to save theme to {}", path, e);
        }
    }

    private static Gson createGson(Path outputFolder) {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(TextureScaling.class, new TextureScalingSerializer())
                .registerTypeAdapterFactory(new ThemeElementAdapterFactory())
                .registerTypeHierarchyAdapter(ThemeResource.class, new ThemeResourceAdapter(outputFolder))
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
        private final Path themeFolder;

        public ThemeResourceAdapter(Path themeFolder) {
            this.themeFolder = themeFolder;
        }

        @Override
        public void write(JsonWriter out, ThemeResource value) throws IOException {
            switch (value) {
                case ClasspathResource classpathResource -> {
                    var idx = Math.max(
                            classpathResource.path().lastIndexOf('/'),
                            classpathResource.path().lastIndexOf('\\'));
                    var filename = classpathResource.path().substring(idx + 1);
                    var diskPath = themeFolder.resolve(filename);
                    try (var buffer = value.toNativeBuffer()) {
                        Files.write(diskPath, buffer.toByteArray());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    out.value(filename);
                }
                case FileResource fileResource -> {
                    var diskPath = themeFolder.resolve(fileResource.file().getName());
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
            return new FileResource(themeFolder.resolve(text).toFile());
        }
    }

    private static class ThemeColorAdapter extends TypeAdapter<ThemeColor> {
        @Override
        public void write(JsonWriter out, ThemeColor value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                var hexColor = Integer.toHexString(value.toArgb());
                hexColor = "#" + "0".repeat(Math.max(0, 8 - hexColor.length())) + hexColor;
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
        private static final Map<String, Class<? extends ThemeElement>> TYPE_MAP = Map.of(
                "image", ThemeImageElement.class,
                "label", ThemeLabelElement.class,
                "performance", ThemePerformanceElement.class,
                "progress", ThemeProgressBarsElement.class,
                "startupLog", ThemeStartupLogElement.class);

        @SuppressWarnings("unchecked")
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type == null) {
                return null;
            }
            if (!ThemeElement.class.isAssignableFrom(type.getRawType())) {
                return null;
            }

            TypeAdapter<JsonElement> jsonElementAdapter = gson.getAdapter(JsonElement.class);
            Map<String, TypeAdapter<? extends ThemeElement>> labelToDelegate = new HashMap<>();
            Map<Class<?>, TypeAdapter<? extends ThemeElement>> subtypeToDelegate = new HashMap<>();
            Map<Class<?>, String> subtypeToLabel = new HashMap<>();
            for (var entry : TYPE_MAP.entrySet()) {
                var delegate = gson.getDelegateAdapter(this, TypeToken.get(entry.getValue()));
                labelToDelegate.put(entry.getKey(), delegate);
                subtypeToDelegate.put(entry.getValue(), delegate);
                subtypeToLabel.put(entry.getValue(), entry.getKey());
            }

            return (TypeAdapter<T>) new TypeAdapter<ThemeElement>() {
                @Override
                public ThemeElement read(JsonReader in) throws IOException {
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
                public void write(JsonWriter out, ThemeElement value) throws IOException {
                    Class<? extends ThemeElement> srcType = value.getClass();
                    String label = subtypeToLabel.get(srcType);
                    // The registration in this map guarantees the type bound of the key equals that of the value
                    var delegate = (TypeAdapter<ThemeElement>) subtypeToDelegate.get(srcType);
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
