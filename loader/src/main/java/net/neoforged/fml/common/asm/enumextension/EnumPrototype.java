package net.neoforged.fml.common.asm.enumextension;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.objectweb.asm.Type;

record EnumPrototype(String owningMod, String enumName, String fieldName, String ctorDesc, EnumParameters ctorParams) implements Comparable<EnumPrototype> {

    private static final String ENUM_CTOR_BASE_DESC = "Ljava/lang/String;I";
    private static final Gson GSON = new Gson();
    @Override
    public int compareTo(EnumPrototype other) {
        int comp = owningMod.compareTo(other.owningMod);
        return comp != 0 ? comp : fieldName.compareTo(other.fieldName);
    }

    static List<EnumPrototype> load(Path path) {
        try {
            JsonObject json = GSON.fromJson(Files.newBufferedReader(path), JsonObject.class);
            String owner = json.get("modid").getAsString();

            JsonArray entries = json.getAsJsonArray("entries");
            List<EnumPrototype> prototypes = new ArrayList<>(entries.size());
            for (JsonElement entry : entries) {
                JsonObject entryObj = entry.getAsJsonObject();

                String enumName = entryObj.get("enum").getAsString();

                String fieldName = entryObj.get("name").getAsString();
                if (!fieldName.toLowerCase(Locale.ROOT).startsWith(owner)) {
                    fieldName = owner.toUpperCase(Locale.ROOT) + "_" + fieldName;
                }

                String ctorDesc = entryObj.get("constructor").getAsString();

                EnumParameters ctorParams;
                JsonElement paramElem = entryObj.get("parameters");
                if (paramElem.isJsonArray()) {
                    ctorParams = loadConstantParameters(ctorDesc, paramElem.getAsJsonArray());
                } else if (paramElem.isJsonObject()) {
                    JsonObject obj = paramElem.getAsJsonObject();
                    ctorParams = new EnumParameters.ListBased(
                            Type.getObjectType(obj.get("class").getAsString()),
                            obj.get("field").getAsString());
                } else {
                    throw new IllegalArgumentException("Unexpected parameter declaration: " + paramElem);
                }

                // Prepend source-invisible field name and ordinal parameters after checking user-provided parameters
                ctorDesc = "(" + ENUM_CTOR_BASE_DESC + ctorDesc.substring(1);
                prototypes.add(new EnumPrototype(owner, enumName, fieldName, ctorDesc, ctorParams));
            }
            return prototypes;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to load enum extension data at " + path, e);
        }
    }

    private static EnumParameters loadConstantParameters(String ctorDesc, JsonArray obj) {
        List<Object> params = new ArrayList<>(obj.size());
        Type[] argTypes = Type.getArgumentTypes(ctorDesc);
        if (argTypes.length != obj.size()) {
            throw new IllegalArgumentException("Parameter count does not match argument count");
        }

        int idx = 0;
        for (JsonElement element : obj) {
            Type argType = argTypes[idx];
            Object value = switch (argType.getDescriptor()) {
                case "Z" -> element.getAsBoolean();
                case "C" -> {
                    String param = element.getAsString();
                    if (param.length() != 1) {
                        throw new IllegalArgumentException("Invalid character");
                    }
                    yield param.charAt(0);
                }
                case "B" -> element.getAsByte();
                case "S" -> element.getAsShort();
                case "I" -> element.getAsInt();
                case "F" -> element.getAsFloat();
                case "J" -> element.getAsLong();
                case "D" -> element.getAsDouble();
                case "Ljava/lang/String;" -> element.isJsonNull() ? null : element.getAsString();
                default -> {
                    if (element.isJsonNull() || element.getAsString().equals("null")) {
                        yield null;
                    }
                    throw new IllegalArgumentException("Unsupported immediate argument type");
                }
            };
            params.add(value);
            idx++;
        }
        return new EnumParameters.Constant(params);
    }
}
