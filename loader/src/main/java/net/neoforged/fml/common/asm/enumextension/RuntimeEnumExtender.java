package net.neoforged.fml.common.asm.enumextension;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.neoforged.coremod.api.ASMAPI;
import net.neoforged.fml.common.asm.ListGeneratorAdapter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
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
    private static final Type NUMBERED_ANNOTATION = Type.getType(NumberedEnum.class);
    private static final Type BLACKLIST_ANNOTATION = Type.getType(BlacklistedConstructor.class);
    private static final Type LIST = Type.getType("Ljava/util/List;");
    private static final int ENUM_FLAGS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM;
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
        if ((classNode.access & Opcodes.ACC_ENUM) == 0) {
            return false;
        }

        if (!classNode.interfaces.contains(MARKER_IFACE.getInternalName())) {
            return false;
        }

        MethodNode isExtended = classNode.methods.stream()
                .filter(mth -> mth.name.equals("isExtended"))
                .findFirst()
                .orElseThrow();

        List<EnumPrototype> protos = prototypes.getOrDefault(classType.getInternalName(), List.of());

        InsnList isExtInsnList = isExtended.instructions;
        isExtInsnList.clear();
        isExtended.localVariables.clear();
        if (isExtended.tryCatchBlocks != null) {
            isExtended.tryCatchBlocks.clear();
        }
        if (isExtended.visibleLocalVariableAnnotations != null) {
            isExtended.visibleLocalVariableAnnotations.clear();
        }
        if (isExtended.invisibleLocalVariableAnnotations != null) {
            isExtended.invisibleLocalVariableAnnotations.clear();
        }
        isExtInsnList.add(new InsnNode(protos.isEmpty() ? Opcodes.ICONST_0 : Opcodes.ICONST_1));
        isExtInsnList.add(new InsnNode(Opcodes.IRETURN));

        if (protos.isEmpty()) {
            return true;
        }

        MethodNode clinit = classNode.methods.stream()
                .filter(mth -> mth.name.equals("<clinit>"))
                .findFirst()
                .orElseThrow();
        MethodNode $values = classNode.methods.stream()
                .filter(mth -> mth.name.equals("$values"))
                .findFirst()
                .orElseThrow();
        int vanillaEntryCount = (int) classNode.fields.stream()
                .takeWhile(field -> (field.access & Opcodes.ACC_ENUM) != 0 && field.desc.equals(classType.getDescriptor()))
                .count();

        int idParamIdx = getIdParameterIndex(classNode);
        Set<String> ctors = classNode.methods.stream()
                .filter(mth -> mth.name.equals("<init>"))
                .filter(RuntimeEnumExtender::isAllowedConstructor)
                .map(mth -> mth.desc)
                .collect(Collectors.toSet());

        InsnList clinitInsnList = new InsnList();
        List<FieldNode> enumEntries = createEnumEntries(classType, clinitInsnList, ctors, idParamIdx, vanillaEntryCount, protos);
        MethodInsnNode $valuesInsn = ASMAPI.findFirstMethodCall(clinit, ASMAPI.MethodType.STATIC, classType.getInternalName(), "$values", $values.desc);
        clinit.instructions.insertBefore($valuesInsn, clinitInsnList);
        classNode.fields.addAll(vanillaEntryCount, enumEntries);

        InsnList $valuesInsnList = new InsnList();
        appendValuesArray(classType, $valuesInsnList, enumEntries);
        AbstractInsnNode aretInsn = ASMAPI.findFirstInstructionBefore($values, Opcodes.ARETURN, $values.instructions.size() - 1);
        $values.instructions.insertBefore(aretInsn, $valuesInsnList);

        exportClassBytes(classNode, classType.getInternalName());
        return true;
    }

    private static int getIdParameterIndex(ClassNode classNode) {
        if (classNode.invisibleAnnotations == null) {
            return -1;
        }

        AnnotationNode annotation = classNode.invisibleAnnotations.stream()
                .filter(anno -> anno.desc.equals(NUMBERED_ANNOTATION.getDescriptor()))
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
                .filter(anno -> anno.desc.equals(BLACKLIST_ANNOTATION.getDescriptor()))
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
            Type classType, InsnList insnList, Set<String> ctors, int idParamIdx, int vanillaEntryCount, List<EnumPrototype> prototypes) {
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
            loadConstructorParams(insnList, idParamIdx, ordinal, proto.ctorDesc(), proto.ctorParams()); // additional parameters
            insnList.add(ASMAPI.buildMethodCall(classType.getInternalName(), "<init>", proto.ctorDesc(), ASMAPI.MethodType.SPECIAL));
            insnList.add(new FieldInsnNode(Opcodes.PUTSTATIC, classType.getInternalName(), field.name, field.desc));

            ordinal++;
        }
        return enumFields;
    }

    private static void loadConstructorParams(InsnList insnList, int idParamIdx, int ordinal, String ctorDesc, EnumParameters params) {
        ListGeneratorAdapter generator = new ListGeneratorAdapter(insnList);
        Type[] argTypes = Type.getType(ctorDesc).getArgumentTypes();
        if (params instanceof EnumParameters.ListBased listBased) {
            for (int idx = 2; idx < argTypes.length; idx++) {
                if (idx - 2 == idParamIdx) {
                    generator.push(ordinal);
                    continue;
                }
                generator.getStatic(listBased.owner(), listBased.fieldName(), LIST);
                generator.push(idx - 2);
                insnList.add(ASMAPI.buildMethodCall(LIST.getInternalName(), "get", "(I)Ljava/lang/Object;", ASMAPI.MethodType.INTERFACE));
                generator.unbox(argTypes[idx]);
            }
        } else if (params instanceof EnumParameters.Constant constant) {
            for (int idx = 2; idx < argTypes.length; idx++) {
                if (idx - 2 == idParamIdx) {
                    if (!(constant.params().get(idx - 2) instanceof Integer i) || i != -1) {
                        throw new IllegalArgumentException("Expected -1 as ID parameter");
                    }
                    generator.push(ordinal);
                    continue;
                }
                switch (constant.params().get(idx - 2)) {
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
        } else {
            throw new IllegalArgumentException("Unexpected enum parameter source type");
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

    public static void loadEnumPrototypes(List<Path> paths) {
        prototypes = paths.stream()
                .map(EnumPrototype::load)
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
    }
}
