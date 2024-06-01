/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm.enumextension;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.neoforged.coremod.api.ASMAPI;
import net.neoforged.fml.common.asm.ListGeneratorAdapter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.slf4j.Logger;
import org.spongepowered.asm.transformers.MixinClassWriter;
import org.spongepowered.asm.util.Constants;

public class RuntimeEnumExtender implements ILaunchPluginService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EnumSet<Phase> YAY = EnumSet.of(Phase.AFTER);
    private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);
    private static final Type MARKER_IFACE = Type.getType(IExtensibleEnum.class);
    private static final Type INDEXED_ANNOTATION = Type.getType(IndexedEnum.class);
    private static final Type NAMED_ANNOTATION = Type.getType(NamedEnum.class);
    private static final Type RESERVED_ANNOTATION = Type.getType(ReservedConstructor.class);
    private static final Type ENUM_PROXY = Type.getType(EnumProxy.class);
    private static final Type NET_CHECK = Type.getType(NetworkedEnum.NetworkCheck.class);
    private static final Type EXT_INFO = Type.getType(ExtensionInfo.class);
    private static final String EXT_INFO_GETTER_DESC = Type.getMethodDescriptor(EXT_INFO);
    private static final String EXT_INFO_CTOR_DESC = Type.getMethodDescriptor(
            Type.VOID_TYPE, Type.BOOLEAN_TYPE, Type.INT_TYPE, Type.INT_TYPE, NET_CHECK);
    private static final Type NETWORKED_ANNOTATION = Type.getType(NetworkedEnum.class);
    private static final Type EXTENDER = Type.getType(RuntimeEnumExtender.class);
    private static final int ENUM_FLAGS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM;
    private static final int EXT_INFO_FLAGS = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
    private static volatile Map<String, List<EnumPrototype>> prototypes = Map.of();

    @Override
    public String name() {
        return "runtime_enum_extender";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return isEmpty ? NAY : YAY;
    }

    @Override
    public boolean processClass(final Phase phase, final ClassNode classNode, final Type classType) {
        if ((classNode.access & Opcodes.ACC_ENUM) == 0 || !classNode.interfaces.contains(MARKER_IFACE.getInternalName())) {
            return false;
        }

        List<EnumPrototype> protos = prototypes.getOrDefault(classType.getInternalName(), List.of());
        if (protos.isEmpty()) {
            return false;
        }

        MethodNode clinit = findMethod(classNode, mth -> mth.name.equals("<clinit>"));
        MethodNode $values = findMethod(classNode, mth -> mth.name.equals("$values"));
        MethodNode getExtInfo = findMethod(classNode, mth -> mth.name.equals("getExtensionInfo") && mth.desc.equals(EXT_INFO_GETTER_DESC));
        Set<String> ctors = classNode.methods.stream()
                .filter(mth -> mth.name.equals("<init>"))
                .filter(RuntimeEnumExtender::isAllowedConstructor)
                .map(mth -> mth.desc)
                .collect(Collectors.toSet());

        int vanillaEntryCount = getVanillaEntryCount(classNode, classType);
        int idParamIdx = getParameterIndexFromAnnotation(classNode, INDEXED_ANNOTATION);
        int nameParamIdx = getParameterIndexFromAnnotation(classNode, NAMED_ANNOTATION);

        if (idParamIdx != -1 && idParamIdx == nameParamIdx) {
            throw new IllegalStateException("ID and name parameter cannot have the same index on enum " + classType);
        }

        FieldNode infoField = new FieldNode(EXT_INFO_FLAGS, "FML$ENUM_EXT_INFO", EXT_INFO.getDescriptor(), null, null);
        classNode.fields.add(infoField);

        clearMethod(getExtInfo);
        InsnList getExtInfoInsnList = getExtInfo.instructions;
        getExtInfoInsnList.add(new FieldInsnNode(Opcodes.GETSTATIC, classType.getInternalName(), infoField.name, infoField.desc));
        getExtInfoInsnList.add(new InsnNode(Opcodes.ARETURN));

        InsnList clinitInsnList = new InsnList();
        List<FieldNode> enumEntries = createEnumEntries(classType, clinitInsnList, ctors, idParamIdx, nameParamIdx, vanillaEntryCount, protos);
        MethodInsnNode $valuesInsn = ASMAPI.findFirstMethodCall(clinit, ASMAPI.MethodType.STATIC, classType.getInternalName(), "$values", $values.desc);
        clinit.instructions.insertBefore($valuesInsn, clinitInsnList);

        InsnList clinitTailInsnList = new InsnList();
        buildExtensionInfo(classNode, classType, clinitTailInsnList, infoField, vanillaEntryCount, protos.size());
        returnValuesToExtender(classType, clinitTailInsnList, protos, enumEntries);
        AbstractInsnNode clinitRetNode = ASMAPI.findFirstInstructionBefore(clinit, Opcodes.RETURN, clinit.instructions.size() - 1);
        clinit.instructions.insertBefore(clinitRetNode, clinitTailInsnList);
        classNode.fields.addAll(vanillaEntryCount, enumEntries);

        InsnList $valuesInsnList = new InsnList();
        appendValuesArray(classType, $valuesInsnList, enumEntries);
        AbstractInsnNode aretInsn = ASMAPI.findFirstInstructionBefore($values, Opcodes.ARETURN, $values.instructions.size() - 1);
        $values.instructions.insertBefore(aretInsn, $valuesInsnList);

        exportClassBytes(classNode, classType.getInternalName());
        return true;
    }

    private static MethodNode findMethod(ClassNode classNode, Predicate<MethodNode> predicate) {
        return classNode.methods.stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow();
    }

    private static void clearMethod(MethodNode mth) {
        mth.instructions.clear();
        mth.localVariables.clear();
        if (mth.tryCatchBlocks != null) {
            mth.tryCatchBlocks.clear();
        }
        if (mth.visibleLocalVariableAnnotations != null) {
            mth.visibleLocalVariableAnnotations.clear();
        }
        if (mth.invisibleLocalVariableAnnotations != null) {
            mth.invisibleLocalVariableAnnotations.clear();
        }
    }

    private static int getVanillaEntryCount(ClassNode classNode, Type classType) {
        return (int) classNode.fields.stream()
                .takeWhile(field -> (field.access & Opcodes.ACC_ENUM) != 0 && field.desc.equals(classType.getDescriptor()))
                .count();
    }

    private static int getParameterIndexFromAnnotation(ClassNode classNode, Type annoType) {
        if (classNode.invisibleAnnotations == null) {
            return -1;
        }

        AnnotationNode annotation = classNode.invisibleAnnotations.stream()
                .filter(anno -> anno.desc.equals(annoType.getDescriptor()))
                .findFirst()
                .orElse(null);
        if (annotation == null) {
            return -1;
        }
        if (annotation.values == null) {
            return 0; // No explicit value specified
        }

        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals("value")) {
                return (Integer) annotation.values.get(i + 1);
            }
        }
        return 0;
    }

    private static boolean isAllowedConstructor(MethodNode mth) {
        if (mth.invisibleAnnotations == null) {
            return true;
        }

        AnnotationNode annotation = mth.invisibleAnnotations.stream()
                .filter(anno -> anno.desc.equals(RESERVED_ANNOTATION.getDescriptor()))
                .findFirst()
                .orElse(null);
        return annotation == null;
    }

    private static void exportClassBytes(ClassNode classNode, String fileName) {
        try {
            MixinClassWriter cw = new MixinClassWriter(ClassWriter.COMPUTE_FRAMES);
            classNode.accept(cw);
            byte[] bytes = cw.toByteArray();
            File outputFile = new File(new File(Constants.DEBUG_OUTPUT_DIR, "class"), fileName + ".class");
            outputFile.getParentFile().mkdirs();
            com.google.common.io.Files.write(bytes, outputFile);
        } catch (Throwable t) {
            throw new RuntimeException("Fuck", t);
        }
    }

    private static List<FieldNode> createEnumEntries(
            Type classType,
            InsnList insnList,
            Set<String> ctors,
            int idParamIdx,
            int nameParamIdx,
            int vanillaEntryCount,
            List<EnumPrototype> prototypes) {
        List<FieldNode> enumFields = new ArrayList<>(prototypes.size());
        int ordinal = vanillaEntryCount;
        for (EnumPrototype proto : prototypes) {
            if (!ctors.contains(proto.ctorDesc())) {
                throw new IllegalArgumentException("Invalid, non-existant or disallowed constructor: " + proto.ctorDesc());
            }

            String fieldName = proto.fieldName();
            FieldNode field = new FieldNode(ENUM_FLAGS, fieldName, classType.getDescriptor(), null, null);
            enumFields.add(field);

            // NEW_FIELD = new EnumType(fieldName, ordinal, ...);
            insnList.add(new TypeInsnNode(Opcodes.NEW, classType.getInternalName()));
            insnList.add(new InsnNode(Opcodes.DUP));
            insnList.add(new LdcInsnNode(fieldName));
            insnList.add(new LdcInsnNode(ordinal));
            loadConstructorParams(insnList, idParamIdx, nameParamIdx, ordinal, proto); // additional parameters
            insnList.add(ASMAPI.buildMethodCall(classType.getInternalName(), "<init>", proto.ctorDesc(), ASMAPI.MethodType.SPECIAL));
            insnList.add(new FieldInsnNode(Opcodes.PUTSTATIC, classType.getInternalName(), field.name, field.desc));

            ordinal++;
        }
        return enumFields;
    }

    private static void loadConstructorParams(InsnList insnList, int idParamIdx, int nameParamIdx, int ordinal, EnumPrototype proto) {
        ListGeneratorAdapter generator = new ListGeneratorAdapter(insnList);
        Type[] argTypes = Type.getType(proto.ctorDesc()).getArgumentTypes();
        switch (proto.ctorParams()) {
            case EnumParameters.FieldReference(Type owner, String fieldName) -> {
                for (int idx = 2; idx < argTypes.length; idx++) {
                    if (idx - 2 == idParamIdx) {
                        generator.push(ordinal);
                        continue;
                    }
                    generator.getStatic(owner, fieldName, ENUM_PROXY);
                    generator.push(idx - 2);
                    generator.invokeVirtual(ENUM_PROXY, new Method("getParameter", "(I)Ljava/lang/Object;"));
                    generator.unbox(argTypes[idx]);
                    if (idx - 2 == nameParamIdx) {
                        generator.push(proto.owningMod());
                        generator.invokeStatic(EXTENDER, new Method("validateNameParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                    }
                }
            }
            case EnumParameters.MethodReference(Type owner, String methodName) -> {
                for (int idx = 2; idx < argTypes.length; idx++) {
                    if (idx - 2 == idParamIdx) {
                        generator.push(ordinal);
                        continue;
                    }
                    generator.push(idx - 2);
                    generator.push(argTypes[idx]);
                    generator.invokeStatic(owner, new Method(methodName, "(ILjava/lang/Class;)Ljava/lang/Object;"));
                    generator.unbox(argTypes[idx]);
                    if (idx - 2 == nameParamIdx) {
                        generator.push(proto.owningMod());
                        generator.invokeStatic(EXTENDER, new Method("validateNameParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                    }
                }
            }
            case EnumParameters.Constant(List<Object> paramList) -> {
                for (int idx = 2; idx < argTypes.length; idx++) {
                    if (idx - 2 == idParamIdx) {
                        if (!(paramList.get(idx - 2) instanceof Integer i) || i != -1) {
                            throw new IllegalArgumentException("Expected -1 as ID parameter");
                        }
                        generator.push(ordinal);
                        continue;
                    }
                    if (idx - 2 == nameParamIdx) {
                        if (!(paramList.get(idx - 2) instanceof String str)) {
                            throw new IllegalArgumentException("Expected String at index " + (idx - 2));
                        }
                        validateNameParameter(str, proto.owningMod());
                    }
                    switch (paramList.get(idx - 2)) {
                        case null -> generator.push((String) null);
                        case String string -> generator.push(string);
                        case Character ch -> generator.push(ch);
                        case Byte b -> generator.push(b);
                        case Short s -> generator.push(s);
                        case Integer i -> generator.push(i);
                        case Long l -> generator.push(l);
                        case Float f -> generator.push(f);
                        case Double d -> generator.push(d);
                        case Boolean bool -> generator.push(bool);
                        default -> throw new IllegalArgumentException("Unsupported constant type");
                    }
                }
            }
        }
    }

    private static void buildExtensionInfo(ClassNode classNode, Type classType, InsnList insnList, FieldNode infoField, int vanillaCount, int moddedCount) {
        String netCheckValue = null;
        if (classNode.invisibleAnnotations != null) {
            netCheckValue = classNode.invisibleAnnotations.stream()
                    .filter(anno -> anno.desc.equals(NETWORKED_ANNOTATION.getDescriptor()))
                    .findFirst()
                    .flatMap(anno -> {
                        if (anno.values == null) {
                            throw new IllegalStateException("Expected values on NetworkedEnum annotation");
                        }
                        for (int i = 0; i < anno.values.size(); i += 2) {
                            if (anno.values.get(i).equals("value")) {
                                String[] value = (String[]) anno.values.get(i + 1);
                                return Optional.of(value[1]);
                            }
                        }
                        throw new IllegalStateException("Expected NetworkedEnum.NetworkCheck value on NetworkedEnum annotation");
                    })
                    .orElse(null);
        }

        insnList.add(new TypeInsnNode(Opcodes.NEW, EXT_INFO.getInternalName()));
        insnList.add(new InsnNode(Opcodes.DUP));
        insnList.add(new InsnNode(moddedCount > 0 ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        insnList.add(new LdcInsnNode(vanillaCount));
        insnList.add(new LdcInsnNode(vanillaCount + moddedCount));
        if (netCheckValue != null) {
            insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, NET_CHECK.getInternalName(), netCheckValue, NET_CHECK.getDescriptor()));
        } else {
            insnList.add(new InsnNode(Opcodes.ACONST_NULL));
        }
        insnList.add(ASMAPI.buildMethodCall(EXT_INFO.getInternalName(), "<init>", EXT_INFO_CTOR_DESC, ASMAPI.MethodType.SPECIAL));
        insnList.add(new FieldInsnNode(Opcodes.PUTSTATIC, classType.getInternalName(), infoField.name, infoField.desc));
    }

    private static void returnValuesToExtender(Type classType, InsnList insnList, List<EnumPrototype> protos, List<FieldNode> entries) {
        for (int i = 0; i < protos.size(); i++) {
            EnumPrototype prototype = protos.get(i);
            if (!(prototype.ctorParams() instanceof EnumParameters.FieldReference(Type owner, String fieldName))) {
                continue;
            }

            FieldNode field = entries.get(i);
            insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.getInternalName(), fieldName, ENUM_PROXY.getDescriptor()));
            insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, classType.getInternalName(), field.name, field.desc));
            insnList.add(ASMAPI.buildMethodCall(ENUM_PROXY.getInternalName(), "setValue", "(Ljava/lang/Enum;)V", ASMAPI.MethodType.VIRTUAL));
        }
    }

    private static void appendValuesArray(Type classType, InsnList insnList, List<FieldNode> enumEntries) {
        // values = Arrays.copyOf(values, values.length + listSize);
        insnList.add(new InsnNode(Opcodes.DUP));
        insnList.add(new InsnNode(Opcodes.ARRAYLENGTH));
        insnList.add(new LdcInsnNode(enumEntries.size()));
        insnList.add(new InsnNode(Opcodes.IADD));
        insnList.add(ASMAPI.buildMethodCall("java/util/Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", ASMAPI.MethodType.STATIC));
        insnList.add(new TypeInsnNode(Opcodes.CHECKCAST, "[" + classType.getDescriptor()));

        // values[NEW_FIELD.ordinal()] = NEW_FIELD;
        for (FieldNode entry : enumEntries) {
            insnList.add(new InsnNode(Opcodes.DUP));
            insnList.add(new FieldInsnNode(Opcodes.GETSTATIC, classType.getInternalName(), entry.name, entry.desc));
            insnList.add(new InsnNode(Opcodes.DUP));
            insnList.add(ASMAPI.buildMethodCall(classType.getInternalName(), "ordinal", "()I", ASMAPI.MethodType.VIRTUAL));
            insnList.add(new InsnNode(Opcodes.SWAP));
            insnList.add(new InsnNode(Opcodes.AASTORE));
        }
    }

    public static void loadEnumPrototypes(Map<String, Path> paths) {
        prototypes = paths.entrySet()
                .stream()
                .map(entry -> EnumPrototype.load(entry.getKey(), entry.getValue()))
                .flatMap(List::stream)
                .sorted()
                .reduce(
                        new HashMap<>(),
                        (map, proto) -> {
                            map.computeIfAbsent(proto.enumName(), $ -> new ArrayList<>()).add(proto);
                            return map;
                        },
                        (protoOne, protoTwo) -> {
                            throw new IllegalStateException("Duplicate EnumPrototype: " + protoOne);
                        });
        prototypes.forEach((cls, prototypes) -> {
            Map<String, EnumPrototype> distinctPrototypes = new HashMap<>();
            for (EnumPrototype proto : prototypes) {
                EnumPrototype prevProto = distinctPrototypes.put(proto.fieldName(), proto);
                if (prevProto != null) {
                    throw new IllegalStateException(String.format(
                            Locale.ROOT,
                            "Found duplicate field '%s' for enum '%s' provided by mods '%s' and '%s'",
                            proto.fieldName(),
                            proto.enumName(),
                            proto.owningMod(),
                            prevProto.owningMod()));
                }
            }
        });
    }

    @SuppressWarnings("UnusedReturnValue") // Return value used via transformer
    public static String validateNameParameter(String fieldName, String owningMod) {
        if (!fieldName.startsWith(owningMod + ":")) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT, "Name parameter must be prefixed by mod ID: '%s' provided by mod '%s'", fieldName, owningMod));
        }
        return fieldName;
    }
}
