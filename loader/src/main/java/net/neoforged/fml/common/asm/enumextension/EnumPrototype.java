/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm.enumextension;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.lang.model.SourceVersion;
import org.objectweb.asm.Type;

record EnumPrototype(String owningMod, String enumName, String fieldName, String ctorDesc, String fullCtorDesc, EnumParameters ctorParams) implements Comparable<EnumPrototype> {

    private static final String ENUM_CTOR_BASE_DESC = "Ljava/lang/String;I";
    private static final Gson GSON = new Gson();
    @Override
    public int compareTo(EnumPrototype other) {
        int comp = owningMod.compareTo(other.owningMod);
        return comp != 0 ? comp : fieldName.compareTo(other.fieldName);
    }

    static List<EnumPrototype> load(String modId, Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            JsonArray entries = json.getAsJsonArray("entries");
            List<EnumPrototype> prototypes = new ArrayList<>(entries.size());
            for (JsonElement entry : entries) {
                JsonObject entryObj = entry.getAsJsonObject();

                String enumName = entryObj.get("enum").getAsString();
                if (!isValidClassDescriptor(enumName)) {
                    throw new IllegalArgumentException(String.format(
                            Locale.ROOT,
                            "Enum '%s' specified by mod '%s' is not a valid class descriptor",
                            enumName,
                            modId));
                }

                String fieldName = entryObj.get("name").getAsString();
                if (!fieldName.toLowerCase(Locale.ROOT).startsWith(modId)) {
                    fieldName = modId.toUpperCase(Locale.ROOT) + "_" + fieldName;
                }
                if (!SourceVersion.isIdentifier(fieldName)) {
                    throw new IllegalArgumentException(String.format(
                            Locale.ROOT,
                            "Enum constant name '%s' for enum '%s' specified by mod '%s' is invalid",
                            fieldName,
                            enumName,
                            modId));
                }

                String ctorDesc = entryObj.get("constructor").getAsString();
                if (!isValidConstructorDescriptor(ctorDesc)) {
                    throw new IllegalArgumentException(String.format(
                            Locale.ROOT,
                            "Constructor '%s' for enum '%s' specified by mod '%s' is not a valid constructor descriptor",
                            ctorDesc,
                            enumName,
                            modId));
                }

                EnumParameters ctorParams;
                JsonElement paramElem = entryObj.get("parameters");
                if (paramElem.isJsonArray()) {
                    ctorParams = loadConstantParameters(modId, enumName, fieldName, ctorDesc, paramElem.getAsJsonArray());
                } else if (paramElem.isJsonObject()) {
                    JsonObject obj = paramElem.getAsJsonObject();
                    String className = obj.get("class").getAsString();
                    if (!isValidClassDescriptor(className)) {
                        throw new IllegalArgumentException(String.format(
                                Locale.ROOT,
                                "Parameter source class '%s' for enum '%s' specified by mod '%s' is not a valid class descriptor",
                                className,
                                enumName,
                                modId));
                    }

                    if (obj.has("method")) {
                        String srcMethodName = obj.get("method").getAsString();
                        if (!SourceVersion.isIdentifier(srcMethodName)) {
                            throw new IllegalArgumentException(String.format(
                                    Locale.ROOT,
                                    "Parameter source method '%s' for enum '%s' specified by mod '%s' is not a valid method name",
                                    srcMethodName,
                                    enumName,
                                    modId));
                        }
                        ctorParams = new EnumParameters.MethodReference(Type.getObjectType(className), srcMethodName);
                    } else if (obj.has("field")) {
                        String srcFieldName = obj.get("field").getAsString();
                        if (!SourceVersion.isIdentifier(srcFieldName)) {
                            throw new IllegalArgumentException(String.format(
                                    Locale.ROOT,
                                    "Parameter source field '%s' for enum '%s' specified by mod '%s' is not a valid field name",
                                    srcFieldName,
                                    enumName,
                                    modId));
                        }
                        ctorParams = new EnumParameters.FieldReference(Type.getObjectType(className), srcFieldName);
                    } else {
                        throw new IllegalArgumentException(String.format(
                                Locale.ROOT,
                                "Unexpected reference parameter declaration '%s' for field '%s' in enum '%s' specified by mod '%s'",
                                paramElem,
                                fieldName,
                                enumName,
                                modId));
                    }
                } else {
                    throw new IllegalArgumentException(String.format(
                            Locale.ROOT,
                            "Unexpected parameter declaration '%s' for field '%s' in enum '%s' specified by mod '%s'",
                            paramElem,
                            fieldName,
                            enumName,
                            modId));
                }

                // Prepend source-invisible field name and ordinal parameters after checking user-provided parameters
                String fullCtorDesc = "(" + ENUM_CTOR_BASE_DESC + ctorDesc.substring(1);
                prototypes.add(new EnumPrototype(modId, enumName, fieldName, ctorDesc, fullCtorDesc, ctorParams));
            }
            return prototypes;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to load enum extension data at " + path, e);
        }
    }

    private static EnumParameters loadConstantParameters(String modId, String enumName, String fieldName, String ctorDesc, JsonArray obj) {
        List<Object> params = new ArrayList<>(obj.size());
        Type[] argTypes = Type.getArgumentTypes(ctorDesc);
        if (argTypes.length != obj.size()) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Parameter count %d does not match argument count %d of constructor '%s' for field '%s' in enum '%s' specified by mod '%s'",
                    obj.size(),
                    argTypes.length,
                    ctorDesc,
                    fieldName,
                    enumName,
                    modId));
        }

        int idx = 0;
        for (JsonElement element : obj) {
            Type argType = argTypes[idx];
            Object value = switch (argType.getDescriptor()) {
                case "Z" -> element.getAsBoolean();
                case "C" -> {
                    String param = element.getAsString();
                    if (param.length() != 1) {
                        throw new IllegalArgumentException(String.format(
                                Locale.ROOT,
                                "Invalid character '%s' at parameter index %d for field '%s' in enum '%s' specified by mod '%s'",
                                param,
                                idx,
                                fieldName,
                                enumName,
                                modId));
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
                    if (element.isJsonNull()) {
                        yield null;
                    }
                    throw new IllegalArgumentException(String.format(
                            Locale.ROOT,
                            "Unsupported immediate argument type '%s' at parameter index %d for field '%s' in enum '%s' specified by mod '%s'",
                            argType,
                            idx,
                            fieldName,
                            enumName,
                            modId));
                }
            };
            params.add(value);
            idx++;
        }
        return new EnumParameters.Constant(params);
    }

    private static boolean isValidClassDescriptor(String desc) {
        for (String part : desc.split("/", -1)) {
            if (!SourceVersion.isIdentifier(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidConstructorDescriptor(String desc) {
        if (!desc.startsWith("(")) {
            return false;
        }
        if (!desc.endsWith(")V")) {
            return false;
        }
        boolean pendingArray = false;
        for (int i = 1; i < desc.length() - 2; i++) {
            char c = desc.charAt(i);
            if ("ZCBSIFJD".indexOf(c) != -1) {
                pendingArray = false;
                continue;
            }
            if (c == '[') {
                pendingArray = true;
                continue;
            }
            if (c != 'L') {
                return false;
            }
            int semicolon = desc.indexOf(';', i);
            if (semicolon <= i) {
                return false;
            }
            String segment = desc.substring(i + 1, semicolon);
            if (!isValidClassDescriptor(segment)) {
                return false;
            }
            i = semicolon;
            pendingArray = false;
        }
        return !pendingArray;
    }
}
