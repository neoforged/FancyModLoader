/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforgespi.language;

import net.neoforged.fml.loading.modscan.ModAnnotation;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public class ModFileScanData {
    private final Set<AnnotationData> annotations;
    private final Set<ClassData> classes;
    public ModFileScanData(Set<AnnotationData> annotations, Set<ClassData> classes) {
        this.annotations = annotations;
        this.classes = classes;
    }
    public ModFileScanData() {
        this(new LinkedHashSet<>(), new LinkedHashSet<>());
    }
    private final List<IModFileInfo> modFiles = new ArrayList<>();

    public Set<ClassData> getClasses() {
        return classes;
    }

    public Set<AnnotationData> getAnnotations() {
        return annotations;
    }

    public Stream<AnnotationData> getAnnotatedBy(Class<? extends Annotation> type, ElementType elementType) {
        final var anType = Type.getType(type);
        return getAnnotations().stream()
                .filter(ad -> ad.targetType == elementType && ad.annotationType.equals(anType));
    }

    public void addModFileInfo(IModFileInfo info) {
        this.modFiles.add(info);
    }

    public List<IModFileInfo> getIModInfoData() {
        return this.modFiles;
    }

    public record ClassData(Type clazz, Type parent, Set<Type> interfaces) {}

    public record AnnotationData(Type annotationType, ElementType targetType, Type clazz, String memberName, Map<String, Object> annotationData) {}

    public void write(ObjectOutputStream stream) throws IOException {
        stream.writeInt(1);
        writeCollection(stream, classes.size(), classes, (st, cls) -> {
            st.writeUTF(cls.clazz.getInternalName());
            st.writeUTF(cls.parent.getInternalName());
            writeCollection(st, cls.interfaces.size(), cls.interfaces, (st1, i) -> st1.writeUTF(i.getInternalName()));
        });
        writeCollection(stream, annotations.size(), annotations, (st, ann) -> {
            st.writeUTF(ann.annotationType().getInternalName());
            st.writeByte((byte)ann.targetType().ordinal());
            st.writeUTF(ann.clazz().getInternalName());
            st.writeUTF(ann.memberName());
            writeCollection(st, ann.annotationData.size(), ann.annotationData.entrySet(), (st1, value) -> {
                st1.writeUTF(value.getKey());
                encodeNested(st1, value.getValue());
            });
        });
    }

    @Nullable
    public static ModFileScanData read(ObjectInputStream stream) throws IOException {
        int spec = stream.readInt();
        if (spec != 1) return null;

        var classesCount = stream.readInt();
        var classes = new LinkedHashSet<ClassData>(classesCount);
        while (classesCount > 0) {
            var type = Type.getObjectType(stream.readUTF());
            var parent = Type.getObjectType(stream.readUTF());
            var interfaces = readCollection(stream, size -> new HashSet<Type>(size), (s, set) -> set.add(Type.getObjectType(s.readUTF())));
            classes.add(new ClassData(type, parent, interfaces));
            classesCount--;
        }

        var annotationCount = stream.readInt();
        var annotations = new LinkedHashSet<AnnotationData>(annotationCount);
        while (annotationCount > 0) {
            annotations.add(new AnnotationData(
                    Type.getObjectType(stream.readUTF()),
                    ElementType.values()[stream.readByte()],
                    Type.getObjectType(stream.readUTF()),
                    stream.readUTF(),
                    readCollection(
                            stream,
                            HashMap::new,
                            (st, map) -> map.put(st.readUTF(), decodeNested(st))
                    )
            ));
            annotationCount--;
        }

        return new ModFileScanData(annotations, classes);
    }

    private static <C> C readCollection(ObjectInputStream stream, IntFunction<C> factory, IOExceptionConsumer<ObjectInputStream, C> reader) throws IOException {
        var size = stream.readInt();
        var col = factory.apply(size);
        while (size > 0) {
            reader.consume(stream, col);
            size--;
        }
        return col;
    }

    private static <T> void writeCollection(ObjectOutputStream stream, int size, Iterable<T> iterable, IOExceptionConsumer<ObjectOutputStream, T> writer) throws IOException {
        stream.writeInt(size);
        for (T t : iterable) {
            writer.consume(stream, t);
        }
    }

    private static Object decodeNested(ObjectInputStream stream) throws IOException {
        return switch (stream.readByte()) {
            case 0 -> stream.readUTF();
            case 1 -> stream.readByte();
            case 2 -> stream.readBoolean();
            case 3 -> stream.readShort();
            case 4 -> stream.readChar();
            case 5 -> stream.readInt();
            case 6 -> stream.readLong();
            case 7 -> stream.readFloat();
            case 8 -> stream.readDouble();
            case 9 -> Type.getType(stream.readUTF());
            case 10 -> new ModAnnotation.EnumHolder(stream.readUTF(), stream.readUTF());
            case 11 -> readCollection(stream, ArrayList::new, (st, l) -> l.add(decodeNested(st)));
            case 12 -> readCollection(stream, HashMap::new, (st, m) -> m.put(st.readUTF(), decodeNested(st)));
            case 13 -> {
                var size = stream.readInt();
                var ar = new byte[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readByte();
                yield ar;
            }
            case 14 -> {
                var size = stream.readInt();
                var ar = new boolean[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readBoolean();
                yield ar;
            }
            case 15 -> {
                var size = stream.readInt();
                var ar = new short[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readShort();
                yield ar;
            }
            case 16 -> {
                var size = stream.readInt();
                var ar = new char[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readChar();
                yield ar;
            }
            case 17 -> {
                var size = stream.readInt();
                var ar = new int[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readInt();
                yield ar;
            }
            case 18 -> {
                var size = stream.readInt();
                var ar = new long[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readLong();
                yield ar;
            }
            case 19 -> {
                var size = stream.readInt();
                var ar = new float[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readFloat();
                yield ar;
            }
            case 20 -> {
                var size = stream.readInt();
                var ar = new double[size];
                for (int i = 0; i < size; i++) ar[i] = stream.readDouble();
                yield ar;
            }
            default -> throw new IllegalArgumentException();
        };
    }

    @SuppressWarnings("ForLoopReplaceableByForEach") // an indexed for loop is a bit faster and more memory efficient
    private static void encodeNested(ObjectOutputStream stream, Object object) throws IOException {
        switch (object) {
            case String st -> {
                stream.writeByte(0);
                stream.writeUTF(st);
            }
            case Byte i -> {
                stream.writeByte(1);
                stream.writeByte(i);
            }
            case Boolean i -> {
                stream.writeByte(2);
                stream.writeBoolean(i);
            }
            case Short i -> {
                stream.writeByte(3);
                stream.writeShort(i);
            }
            case Character i -> {
                stream.writeByte(4);
                stream.writeChar(i);
            }
            case Integer i -> {
                stream.writeByte(5);
                stream.writeInt(i);
            }
            case Long i -> {
                stream.writeByte(6);
                stream.writeLong(i);
            }
            case Float i -> {
                stream.writeByte(7);
                stream.writeFloat(i);
            }
            case Double i -> {
                stream.writeByte(8);
                stream.writeDouble(i);
            }
            case Type t -> {
                stream.writeByte(9);
                stream.writeUTF(t.getDescriptor());
            }
            case ModAnnotation.EnumHolder(var desc, var val) -> {
                stream.writeByte(10);
                stream.writeUTF(desc);
                stream.writeUTF(val);
            }
            case List<?> list -> {
                stream.writeByte(11);
                writeCollection(stream, list.size(), list, ModFileScanData::encodeNested);
            }
            case Map<?, ?> map -> {
                stream.writeByte(12);
                writeCollection(stream, map.size(), map.entrySet(), (st1, in) -> {
                    st1.writeUTF((String)in.getKey());
                    encodeNested(st1, in.getValue());
                });
            }
            case byte[] b -> {
                stream.writeInt(13);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeByte(b[i]);
                }
            }
            case boolean[] b -> {
                stream.writeInt(14);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeBoolean(b[i]);
                }
            }
            case short[] b -> {
                stream.writeInt(15);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeShort(b[i]);
                }
            }
            case char[] b -> {
                stream.writeInt(16);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeChar(b[i]);
                }
            }
            case int[] b -> {
                stream.writeInt(17);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeInt(b[i]);
                }
            }
            case long[] b -> {
                stream.writeInt(18);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeLong(b[i]);
                }
            }
            case double[] b -> {
                stream.writeInt(19);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeDouble(b[i]);
                }
            }
            case float[] b -> {
                stream.writeInt(20);
                stream.writeInt(b.length);
                for (int i = 0; i < b.length; i++) {
                    stream.writeFloat(b[i]);
                }
            }
            default -> throw new IllegalArgumentException();
        }
    }

    @FunctionalInterface
    private interface IOExceptionConsumer<F, I> {
        void consume(F stream, I input) throws IOException;
    }
}
