/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.startup;

import com.sun.tools.attach.VirtualMachine;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Used out-of-process to attach our agent to the current VM.
 * Please red JEP to understand that this will likely be disabled in a future LTS of Java:
 * https://openjdk.org/jeps/451
 * <p>
 * We expect the ecosystem to allow for easier attachment of agents via the intended way (command-line) by then.
 */
public class SelfAttach {
    public static void main(String[] args) throws Exception {
        var parentProcess = ProcessHandle.current().parent().orElse(null);
        if (parentProcess == null) {
            System.err.println("No parent process to attach to");
            System.exit(1);
        }

        var ourExecutable = parentProcess.info().command().orElse(null);
        var parentExecutable = parentProcess.info().command().orElse(null);
        if (!Objects.equals(ourExecutable, parentExecutable)) {
            System.err.println("Can only self-attach if the processes match: " + ourExecutable + " != " + parentExecutable);
            System.exit(1);
        }

        // Write a jar-file to a temp-file, which only contains the manifest pointing to the desired class
        // This class already has to be on the system classloader
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Agent-Class", DevAgent.class.getName());

        Path tempFile = null;
        VirtualMachine vm = null;
        try {
            tempFile = Files.createTempFile("agentattach", ".jar");
            new JarOutputStream(Files.newOutputStream(tempFile), manifest).close();

            vm = VirtualMachine.attach(String.valueOf(parentProcess.pid()));
            vm.loadAgent(tempFile.toAbsolutePath().toString());
        } finally {
            if (vm != null) {
                vm.detach();
            }
            if (tempFile != null) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    /**
     * Gets a Java class-path item that allows the JVM to load this class.
     */
    public static String getClassPathItem() throws Exception {
        var relativeClassPath = "/" + SelfAttach.class.getName().replace('.', '/') + ".class";
        var locationUrl = SelfAttach.class.getResource(relativeClassPath);
        if (locationUrl == null) {
            throw new IllegalStateException("Couldn't find SelfAttach class on class-path at " + relativeClassPath);
        }
        var location = locationUrl.toURI();
        if ("file".equals(location.getScheme())) {
            var classpathDir = Paths.get(location);

            var expectedPath = classpathDir.resolve(SelfAttach.class.getName().replace('.', '/') + ".class");
            if (!Files.isRegularFile(expectedPath)) {
                throw new IllegalStateException("Expected SelfAttach at " + expectedPath + " but couldn't find it.");
            }

            return classpathDir.toAbsolutePath().toString();
        } else if (location.getScheme().equals("jar") && location.getRawSchemeSpecificPart().contains("!/")) {
            int lastExcl = location.getRawSchemeSpecificPart().lastIndexOf("!/");
            return Paths.get(new URI(location.getRawSchemeSpecificPart().substring(0, lastExcl))).toAbsolutePath().toString();
        } else {
            throw new IllegalStateException("Class path resource for SelfAttach uses unknown scheme: " + location.getScheme());
        }
    }
}
