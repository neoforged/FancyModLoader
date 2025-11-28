package net.neoforged.fml.testlib;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_RECORD;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V21;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;

public class SimulatedGameAndInstallationServiceProvider {
    private static IdentifiableContent createInstallerClass(
            InstallerInstance installerInstance) {
        return createInstallerClass(
                installerInstance.className,
                installerInstance.packageName,
                installerInstance.relativePath);
    }

    private static IdentifiableContent createInstallerClass(
            String className,
            String packageName,
            String relativePath) {
        ClassWriter classWriter = new ClassWriter(0);
        FieldVisitor fieldVisitor;
        RecordComponentVisitor recordComponentVisitor;
        MethodVisitor methodVisitor;
        AnnotationVisitor annotationVisitor0;

        String packagePath = packageName.replace(".", "/");

        classWriter.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_RECORD, "%s/%s".formatted(packagePath, className), null, "java/lang/Record", new String[] { "net/neoforged/neoforgespi/installation/GameDiscoveryOrInstallationService" });

        classWriter.visitSource("%s.java".formatted(className), null);

        classWriter.visitInnerClass("net/neoforged/neoforgespi/installation/GameDiscoveryOrInstallationService$Result", "net/neoforged/neoforgespi/installation/GameDiscoveryOrInstallationService", "Result", ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

        classWriter.visitInnerClass("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(10, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false);
            methodVisitor.visitInsn(RETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "L%s/%s;".formatted(packagePath, className), null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "name", "()Ljava/lang/String;", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(13, label0);
            methodVisitor.visitLdcInsn("simulatedInstallation");
            methodVisitor.visitInsn(ARETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "L%s/%s;".formatted(packagePath, className), null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "discoverOrInstall", "()Lnet/neoforged/neoforgespi/installation/GameDiscoveryOrInstallationService$Result;", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(18, label0);
            methodVisitor.visitLdcInsn("libraryDirectory");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false);
            methodVisitor.visitVarInsn(ASTORE, 1);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLineNumber(19, label1);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInsn(ICONST_0);
            methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/String");
            methodVisitor.visitMethodInsn(INVOKESTATIC, "java/nio/file/Path", "of", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", true);
            methodVisitor.visitVarInsn(ASTORE, 2);
            Label label2 = new Label();
            methodVisitor.visitLabel(label2);
            methodVisitor.visitLineNumber(20, label2);
            methodVisitor.visitTypeInsn(NEW, "net/neoforged/neoforgespi/installation/GameDiscoveryOrInstallationService$Result");
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitLdcInsn(relativePath);
            Label label3 = new Label();
            methodVisitor.visitLabel(label3);
            methodVisitor.visitLineNumber(21, label3);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, "java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;", true);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, "net/neoforged/neoforgespi/installation/GameDiscoveryOrInstallationService$Result", "<init>", "(Ljava/nio/file/Path;)V", false);
            Label label4 = new Label();
            methodVisitor.visitLabel(label4);
            methodVisitor.visitLineNumber(20, label4);
            methodVisitor.visitInsn(ARETURN);
            Label label5 = new Label();
            methodVisitor.visitLabel(label5);
            methodVisitor.visitLocalVariable("this", "L%s/%s;".formatted(packagePath, className), null, label0, label5, 0);
            methodVisitor.visitLocalVariable("librariesDirectory", "Ljava/lang/String;", null, label1, label5, 1);
            methodVisitor.visitLocalVariable("librariesRoot", "Ljava/nio/file/Path;", null, label2, label5, 2);
            methodVisitor.visitMaxs(4, 3);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "toString", "()Ljava/lang/String;", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(10, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInvokeDynamicInsn("toString", "(L%s/%s;)Ljava/lang/String;".formatted(packagePath, className), new Handle(Opcodes.H_INVOKESTATIC, "java/lang/runtime/ObjectMethods", "bootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;", false), new Object[] { Type.getType("L%s/%s;".formatted(packagePath, className)), "" });
            methodVisitor.visitInsn(ARETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "L%s/%s;".formatted(packagePath, className), null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "hashCode", "()I", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(10, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitInvokeDynamicInsn("hashCode", "(L%s/%s;)I".formatted(packagePath, className), new Handle(Opcodes.H_INVOKESTATIC, "java/lang/runtime/ObjectMethods", "bootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;", false), new Object[] { Type.getType("L%s/%s;".formatted(packagePath, className)), "" });
            methodVisitor.visitInsn(IRETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "L%s/%s;".formatted(packagePath, className), null, label0, label1, 0);
            methodVisitor.visitMaxs(1, 1);
            methodVisitor.visitEnd();
        }
        {
            methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "equals", "(Ljava/lang/Object;)Z", null, null);
            methodVisitor.visitCode();
            Label label0 = new Label();
            methodVisitor.visitLabel(label0);
            methodVisitor.visitLineNumber(10, label0);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitInvokeDynamicInsn("equals", "(L%s/%s;Ljava/lang/Object;)Z".formatted(packagePath, className), new Handle(Opcodes.H_INVOKESTATIC, "java/lang/runtime/ObjectMethods", "bootstrap", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;", false), new Object[] { Type.getType("L%s/%s;".formatted(packagePath, className)), "" });
            methodVisitor.visitInsn(IRETURN);
            Label label1 = new Label();
            methodVisitor.visitLabel(label1);
            methodVisitor.visitLocalVariable("this", "L%s/%s;".formatted(packagePath, className), null, label0, label1, 0);
            methodVisitor.visitLocalVariable("o", "Ljava/lang/Object;", null, label0, label1, 1);
            methodVisitor.visitMaxs(2, 2);
            methodVisitor.visitEnd();
        }
        classWriter.visitEnd();

        return new IdentifiableContent("%s/%s.class".formatted(packageName, className), "%s/%s.class".formatted(packagePath, className), classWriter.toByteArray());
    }

    public record InstallerInstance(
            String className,
            String packageName,
            String relativePath) {}

    public static IdentifiableContent[] create(
            InstallerInstance... installers) {
        var installerClasses = Arrays.stream(installers)
                .map(SimulatedGameAndInstallationServiceProvider::createInstallerClass)
                .toArray(IdentifiableContent[]::new);

        var serviceFileContent = Arrays.stream(installers)
                .map(installer -> "%s.%s".formatted(installer.packageName, installer.className))
                .collect(Collectors.joining("\n"));

        var serviceFile = new IdentifiableContent(
                "servicefile:net.neoforged.neoforgespi.installation.GameDiscoveryOrInstallationService",
                "META-INF/services/net.neoforged.neoforgespi.installation.GameDiscoveryOrInstallationService",
                serviceFileContent.getBytes());

        return ArrayUtils.addAll(installerClasses, serviceFile);
    }
}
