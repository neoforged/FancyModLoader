/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.mixin;

import com.google.common.io.Resources;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.io.IOException;
import java.net.URL;
import net.neoforged.fml.ModLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.transformers.MixinClassReader;

class FMLClassBytecodeProvider implements IClassBytecodeProvider {
    private final ILaunchPluginService.ITransformerLoader transformerLoader;
    private final FMLMixinLaunchPlugin launchPlugin;

    FMLClassBytecodeProvider(ILaunchPluginService.ITransformerLoader transformerLoader, FMLMixinLaunchPlugin launchPlugin) {
        this.transformerLoader = transformerLoader;
        this.launchPlugin = launchPlugin;
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
            classBytes = transformerLoader.buildTransformedClassNodeFor(canonicalName);
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

            ModLoader.incrementMixinParsedClasses();

            return classNode;
        }

        Type classType = Type.getObjectType(internalName);
        if (launchPlugin.generatesClass(classType)) {
            ClassNode classNode = new ClassNode();
            if (launchPlugin.generateClass(classType, classNode)) {
                return classNode;
            }
        }

        throw new ClassNotFoundException(canonicalName);
    }
}
