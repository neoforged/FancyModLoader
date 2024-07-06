package net.neoforged.fml.loading.mixin;

import com.google.common.io.Resources;
import cpw.mods.modlauncher.TransformingClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.IClassProcessor;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.transformers.MixinClassReader;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;

class FMLClassBytecodeProvider implements IClassBytecodeProvider {
    private final TransformingClassLoader classLoader;
    private final Collection<IClassProcessor> processors;

    FMLClassBytecodeProvider(TransformingClassLoader classLoader, Collection<IClassProcessor> processors) {
        this.classLoader = classLoader;
        this.processors = processors;
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException {
        return this.getClassNode(name, true, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException {
        return this.getClassNode(name, runTransformers, ClassReader.EXPAND_FRAMES);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException {
        if (!runTransformers) {
            throw new IllegalArgumentException("FML service does not currently support retrieval of untransformed bytecode");
        }

        String canonicalName = name.replace('/', '.');
        String internalName = name.replace('.', '/');

        byte[] classBytes;

        try {
            // Passing FMLMixinLaunchPlugin.NAME here prevents that plugin from recursively being applied
            classBytes = classLoader.buildTransformedClassNodeFor(canonicalName, FMLMixinLaunchPlugin.NAME);
        } catch (ClassNotFoundException ex) {
            URL url = Thread.currentThread().getContextClassLoader().getResource(internalName + ".class");
            if (url == null) {
                throw ex;
            }
            try {
                classBytes = Resources.asByteSource(url).read();
            } catch (IOException ioex) {
                throw ex;
            }
        }

        if (classBytes != null && classBytes.length != 0) {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new MixinClassReader(classBytes, canonicalName);
            classReader.accept(classNode, readerFlags);
            return classNode;
        }

        Type classType = Type.getObjectType(internalName);
        for (var processor : processors) {
            if (!processor.generatesClass(classType)) {
                continue;
            }

            ClassNode classNode = new ClassNode();
            if (processor.generateClass(classType, classNode)) {
                return classNode;
            }
        }

        throw new ClassNotFoundException(canonicalName);
    }
}
