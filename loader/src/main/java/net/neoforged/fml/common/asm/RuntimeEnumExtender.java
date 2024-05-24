/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.common.asm;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

/**
 * Modifies specified enums to allow runtime extension by making the $VALUES field non-final and
 * injecting constructor calls which are not valid in normal java code.
 */
public class RuntimeEnumExtender implements ILaunchPluginService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Type STRING = Type.getType(String.class);
    private final Type ENUM = Type.getType(Enum.class);
    private final Type MARKER_IFACE = Type.getType("Lnet/neoforged/neoforge/common/IExtensibleEnum;");
    private final Type ARRAY_UTILS = Type.getType("Lorg/apache/commons/lang3/ArrayUtils;"); //Don't directly reference this to prevent class loading.
    private final String ADD_DESC = Type.getMethodDescriptor(Type.getType(Object[].class), Type.getType(Object[].class), Type.getType(Object.class));
    private final Type UNSAFE_HACKS = Type.getType("Lnet/minecraftforge/fml/unsafe/UnsafeHacks;"); //Again, not direct reference to prevent class loading.
    private final String CLEAN_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Class.class));
    private final String NAME_DESC = Type.getMethodDescriptor(STRING);
    private final String EQUALS_DESC = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, STRING);
    private final Type LIST_TYPE = Type.getType(List.class);
    private final Type ARRAY_LIST_TYPE = Type.getType(ArrayList.class);
    private final String ARRAY_LIST_INIT_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Collection.class));
    private final String VANILLA_FIELDS_SIGN = "Ljava/util/List<%s>;";
    private final Type ARRAYS_TYPE = Type.getType(Arrays.class);
    private final String AS_LIST_DESC = Type.getMethodDescriptor(LIST_TYPE, Type.getType(Object[].class));
    private final String REMOVE_ALL_DESC = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(Collection.class));
    private final Type ASM_UTILS_TYPE = Type.getType("Lnet/neoforged/fml/util/ASMUtils;");
    private final String NAME_COMPARATOR_DESC = Type.getMethodDescriptor(Type.getType(Comparator.class));
    private final String SORT_DESC = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Comparator.class));
    private final String LIST_ADD_ALL_DESC = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.INT_TYPE, Type.getType(Collection.class));
    private final String TO_ARRAY_DESC = Type.getMethodDescriptor(Type.getType(Object[].class), Type.getType(Object[].class));
    private final String SIZE_DESC = Type.getMethodDescriptor(Type.INT_TYPE);
    private final String SET_ORDINAL_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Enum.class), Type.INT_TYPE);

    @Override
    public String name() {
        return "runtime_enum_extender";
    }

    private static final EnumSet<Phase> YAY = EnumSet.of(Phase.AFTER);
    private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return isEmpty ? NAY : YAY;
    }

    @Override
    public int processClassWithFlags(final Phase phase, final ClassNode classNode, final Type classType, final String reason) {
        if ((classNode.access & Opcodes.ACC_ENUM) == 0)
            return ComputeFlags.NO_REWRITE;

        Type array = Type.getType("[" + classType.getDescriptor());
        final int flags = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;

        FieldNode values = classNode.fields.stream().filter(f -> f.desc.contentEquals(array.getDescriptor()) && ((f.access & flags) == flags)).findFirst().orElse(null);

        if (!classNode.interfaces.contains(MARKER_IFACE.getInternalName())) {
            return ComputeFlags.NO_REWRITE;
        }

        //Static methods named "create" with first argument as a string
        List<MethodNode> candidates = classNode.methods.stream()
                .filter(m -> ((m.access & Opcodes.ACC_STATIC) != 0) && m.name.equals("create"))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            throw new IllegalStateException("IExtensibleEnum has no candidate factory methods: " + classType.getClassName());
        }

        FieldNode vanillaValues = new FieldNode(Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, "FML$VANILLA_VALUES", LIST_TYPE.getDescriptor(), VANILLA_FIELDS_SIGN.formatted(classType.getDescriptor()), null);
        classNode.fields.add(vanillaValues);

        candidates.forEach(mtd -> {
            Type[] args = Type.getArgumentTypes(mtd.desc);
            if (args.length == 0 || !args[0].equals(STRING)) {
                if (LOGGER.isErrorEnabled(LogUtils.FATAL_MARKER)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Enum has create method without String as first parameter:\n");
                    sb.append("  Enum: ").append(classType.getDescriptor()).append("\n");
                    sb.append("  Target: ").append(mtd.name).append(mtd.desc).append("\n");
                    LOGGER.error(LogUtils.FATAL_MARKER, sb.toString());
                }
                throw new IllegalStateException("Enum has create method without String as first parameter: " + mtd.name + mtd.desc);
            }

            Type ret = Type.getReturnType(mtd.desc);
            if (!ret.equals(classType)) {
                if (LOGGER.isErrorEnabled(LogUtils.FATAL_MARKER)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Enum has create method with incorrect return type:\n");
                    sb.append("  Enum: ").append(classType.getDescriptor()).append("\n");
                    sb.append("  Target: ").append(mtd.name).append(mtd.desc).append("\n");
                    sb.append("  Found: ").append(ret.getClassName()).append(", Expected: ").append(classType.getClassName());
                    LOGGER.error(LogUtils.FATAL_MARKER, sb.toString());
                }
                throw new IllegalStateException("Enum has create method with incorrect return type: " + mtd.name + mtd.desc);
            }

            Type[] ctrArgs = new Type[args.length + 1];
            ctrArgs[0] = STRING;
            ctrArgs[1] = Type.INT_TYPE;
            for (int x = 1; x < args.length; x++)
                ctrArgs[1 + x] = args[x];

            String desc = Type.getMethodDescriptor(Type.VOID_TYPE, ctrArgs);

            MethodNode ctr = classNode.methods.stream().filter(m -> m.name.equals("<init>") && m.desc.equals(desc)).findFirst().orElse(null);
            if (ctr == null) {
                if (LOGGER.isErrorEnabled(LogUtils.FATAL_MARKER)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Enum has create method with no matching constructor:\n");
                    sb.append("  Enum: ").append(classType.getDescriptor()).append("\n");
                    sb.append("  Candidate: ").append(mtd.desc).append("\n");
                    sb.append("  Target: ").append(desc).append("\n");
                    classNode.methods.stream().filter(m -> m.name.equals("<init>")).forEach(m -> sb.append("        : ").append(m.desc).append("\n"));
                    LOGGER.error(LogUtils.FATAL_MARKER, sb.toString());
                }
                throw new IllegalStateException("Enum has create method with no matching constructor: " + desc);
            }

            if (values == null) {
                if (LOGGER.isErrorEnabled(LogUtils.FATAL_MARKER)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Enum has create method but we could not find $VALUES. Found:\n");
                    classNode.fields.stream().filter(f -> (f.access & Opcodes.ACC_STATIC) != 0).forEach(m -> sb.append("  ").append(m.name).append(" ").append(m.desc).append("\n"));
                    LOGGER.error(LogUtils.FATAL_MARKER, sb.toString());
                }
                throw new IllegalStateException("Enum has create method but we could not find $VALUES");
            }

            values.access &= values.access & ~Opcodes.ACC_FINAL; //Strip the final so JITer doesn't inline things.

            mtd.access |= Opcodes.ACC_SYNCHRONIZED;
            mtd.instructions.clear();
            mtd.localVariables.clear();
            if (mtd.tryCatchBlocks != null) {
                mtd.tryCatchBlocks.clear();
            }
            if (mtd.visibleLocalVariableAnnotations != null) {
                mtd.visibleLocalVariableAnnotations.clear();
            }
            if (mtd.invisibleLocalVariableAnnotations != null) {
                mtd.invisibleLocalVariableAnnotations.clear();
            }
            InstructionAdapter ins = new InstructionAdapter(mtd);

            {
                Label if_initialized_vanilla = new Label();
                //if vanillaValues == null
                //  vanillaValues = Arrays.asList(VALUES)
                ins.getstatic(classType.getInternalName(), vanillaValues.name, vanillaValues.desc);
                ins.ifnonnull(if_initialized_vanilla);
                ins.getstatic(classType.getInternalName(), values.name, values.desc);
                ins.invokestatic(ARRAYS_TYPE.getInternalName(), "asList", AS_LIST_DESC, false);
                ins.putstatic(classType.getInternalName(), vanillaValues.name, vanillaValues.desc);
                ins.mark(if_initialized_vanilla);
            }
            int vars = 0;
            for (Type arg : args)
                vars += arg.getSize();

            { //remove duplicates
                vars += 1; //int x
                Label for_start = new Label();
                Label for_condition = new Label();
                Label for_inc = new Label();

                ins.iconst(0);
                ins.store(vars, Type.INT_TYPE);
                ins.goTo(for_condition);
                //if (!VALUES[x].name().equalsIgnoreCase(name)) goto for_inc
                ins.mark(for_start);
                ins.getstatic(classType.getInternalName(), values.name, values.desc);
                ins.load(vars, Type.INT_TYPE);
                ins.aload(array);
                ins.invokevirtual(ENUM.getInternalName(), "name", NAME_DESC, false);
                ins.load(0, STRING);
                ins.invokevirtual(STRING.getInternalName(), "equalsIgnoreCase", EQUALS_DESC, false);
                ins.ifeq(for_inc);
                //return VALUES[x];
                ins.getstatic(classType.getInternalName(), values.name, values.desc);
                ins.load(vars, Type.INT_TYPE);
                ins.aload(array);
                ins.areturn(classType);
                //x++
                ins.mark(for_inc);
                ins.iinc(vars, 1);
                //if (x < VALUES.length) goto for_start
                ins.mark(for_condition);
                ins.load(vars, Type.INT_TYPE);
                ins.getstatic(classType.getInternalName(), values.name, values.desc);
                ins.arraylength();
                ins.ificmplt(for_start);
            }

            {
                vars += 1; //enum ret;
                //ret = new ThisType(name, VALUES.length, args..)
                ins.anew(classType);
                ins.dup();
                ins.load(0, STRING);
                ins.getstatic(classType.getInternalName(), values.name, values.desc);
                ins.arraylength();
                int idx = 1;
                for (int x = 1; x < args.length; x++) {
                    ins.load(idx, args[x]);
                    idx += args[x].getSize();
                }
                ins.invokespecial(classType.getInternalName(), "<init>", desc, false);
                ins.store(vars, classType);
                // VALUES = ArrayUtils.add(VALUES, ret)
                ins.getstatic(classType.getInternalName(), values.name, values.desc);
                ins.load(vars, classType);
                ins.invokestatic(ARRAY_UTILS.getInternalName(), "add", ADD_DESC, false);
                ins.checkcast(array);
                ins.putstatic(classType.getInternalName(), values.name, values.desc);
                //UnsafeHacks.cleanEnumCache(ThisType.class)
                ins.visitLdcInsn(classType);
                ins.invokestatic(UNSAFE_HACKS.getInternalName(), "cleanEnumCache", CLEAN_DESC, false);
                //init ret
                ins.load(vars, classType);
                ins.invokeinterface(MARKER_IFACE.getInternalName(), "init", "()V");
                ins.load(vars, classType);

                { // reorder enum entries
                    vars += 1;
                    int moddedElements = vars;
                    vars += 1;
                    int iterationIndex = vars;

                    //gather all modded values, sort them alphabetically and prepend vanilla values to create a new values order
                    //moddedValues = new ArrayList(Arrays.asList($VALUES))
                    ins.anew(ARRAY_LIST_TYPE);
                    ins.dup();
                    ins.getstatic(classType.getInternalName(), values.name, values.desc); //load values into stack
                    ins.invokestatic(ARRAYS_TYPE.getInternalName(), "asList", AS_LIST_DESC, false);
                    ins.invokespecial(ARRAY_LIST_TYPE.getInternalName(), "<init>", ARRAY_LIST_INIT_DESC, false);
                    ins.store(moddedElements, LIST_TYPE);
                    //moddedValues.removeAll(vanillaValues))
                    ins.load(moddedElements, LIST_TYPE);
                    ins.getstatic(classType.getInternalName(), vanillaValues.name, vanillaValues.desc);
                    ins.invokeinterface(LIST_TYPE.getInternalName(), "removeAll", REMOVE_ALL_DESC);
                    ins.pop();
                    //moddedValues.sort(ASMUtils.nameComparator())
                    ins.load(moddedElements, LIST_TYPE);
                    ins.invokestatic(ASM_UTILS_TYPE.getInternalName(), "nameComparator", NAME_COMPARATOR_DESC);
                    ins.invokeinterface(LIST_TYPE.getInternalName(), "sort", SORT_DESC);
                    //moddedValues.addAll(0, vanillaValues)
                    ins.load(moddedElements, LIST_TYPE);
                    ins.iconst(0);
                    ins.getstatic(classType.getInternalName(), vanillaValues.name, vanillaValues.desc);
                    ins.invokeinterface(LIST_TYPE.getInternalName(), "addAll", LIST_ADD_ALL_DESC);
                    ins.pop();
                    //$VALUES = moddedValues.toArray(new <EnumType>[0])
                    ins.load(moddedElements, LIST_TYPE);
                    ins.iconst(0);
                    ins.newarray(classType);
                    ins.invokeinterface(LIST_TYPE.getInternalName(), "toArray", TO_ARRAY_DESC);
                    ins.checkcast(array);
                    ins.putstatic(classType.getInternalName(), values.name, values.desc);

                    //iterate over elements and set their ordinal
                    Label for_end = new Label();
                    Label for_condition = new Label();
                    ins.getstatic(classType.getInternalName(), vanillaValues.name, vanillaValues.desc);
                    ins.invokeinterface(LIST_TYPE.getInternalName(), "size", SIZE_DESC);
                    ins.store(iterationIndex, Type.INT_TYPE);

                    ins.mark(for_condition);
                    ins.load(iterationIndex, Type.INT_TYPE);
                    ins.getstatic(classType.getInternalName(), values.name, values.desc);
                    ins.arraylength();
                    ins.ificmpge(for_end);
                    ins.getstatic(classType.getInternalName(), values.name, values.desc);
                    ins.load(iterationIndex, Type.INT_TYPE);
                    ins.aload(array);
                    ins.load(iterationIndex, Type.INT_TYPE);
                    ins.invokestatic(ASM_UTILS_TYPE.getInternalName(), "setOrdinal", SET_ORDINAL_DESCRIPTOR, false);
                    ins.iinc(iterationIndex, 1);
                    ins.goTo(for_condition);
                    ins.mark(for_end);
                }
                //return ret
                ins.areturn(classType);
            }
        });
        return ComputeFlags.COMPUTE_FRAMES;
    }
}
