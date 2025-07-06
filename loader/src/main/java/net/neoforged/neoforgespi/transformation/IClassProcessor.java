package net.neoforged.neoforgespi.transformation;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

// TODO: this and IClassProcessorProvider are both on the PLUGIN layer -- the differences from coremods (namely, the
//  greater flexibility and therefore need to be careful with what you request processing for) should be documented.
// TODO: should we add some sort of warning screen on load if more than X% of classes are being transformed? This seems doable;
//  could even count this per-plugin to detect and warn on cases of transform-everything plugins.
public interface IClassProcessor {
    class ComputeFlags {
        /**
         * This plugin did not change the class and therefor requires no rewrite of the class.
         * This is the fastest option
         */
        public static final int NO_REWRITE = 0;

        /**
         * The plugin did change the class and requires a rewrite, but does not require any additional computation
         * as frames and maxs in the class did not change of have been corrected by the plugin.
         * Should not be combined with {@link #COMPUTE_FRAMES} or {@link #COMPUTE_MAXS}
         */
        public static final int SIMPLE_REWRITE = 0x100; //leave some space for eventual new flags in ClassWriter

        /**
         * The plugin did change the class and requires a rewrite, and requires max re-computation,
         * but frames are unchanged or corrected by the plugin
         */
        public static final int COMPUTE_MAXS = ClassWriter.COMPUTE_MAXS;

        /**
         * The plugin did change the class and requires a rewrite, and requires frame re-computation.
         * This is the slowest, but also safest method if you don't know what level is required.
         * This implies {@link #COMPUTE_MAXS}, so maxs will also be recomputed.
         */
        public static final int COMPUTE_FRAMES = ClassWriter.COMPUTE_FRAMES;
    }

    String COMPUTING_FRAMES = "computing_frames";

    /**
     * {@return a unique identifier for this processor}
     */
    String name();

    /**
     * {@return processors that this processor must run before}
     */
    default Set<String> runsBefore() {
        return Set.of();
    }

    /**
     * {@return processors that this processor must run after} This should include
     * {@link IClassProcessor#COMPUTING_FRAMES} if the processor returns a result requiring frame re-computation
     */
    default Set<String> runsAfter() {
        return Set.of(COMPUTING_FRAMES);
    }

    /**
     * {@return packages that this processor generates classes for, that do not already exist on the game layer}
     */
    default Set<String> generatesPackages() {
        // TODO: implement
        return Set.of();
    }

    /**
     * {@return whether the processor wants to recieve the class}
     * 
     * @param classType the class to consider
     * @param isEmpty if the class is empty at present (indicates no backing file found) 
     */
    boolean handlesClass(Type classType, boolean isEmpty);

    /**
     * Each class that the processor has opted to recieve is passed to it for processing.
     * One of this or {@link #processClass(ClassNode, Type)} <em>must</em> be implemented.
     *
     * @param classNode the classnode to process
     * @param classType the name of the class
     * @return the {@link ComputeFlags} indicating how the class should be rewritten.
     */
    default int processClassWithFlags(ClassNode classNode, Type classType) {
        return processClass(classNode, classType) ? ComputeFlags.COMPUTE_FRAMES : ComputeFlags.NO_REWRITE;
    }

    /**
     * Each class that the processor has opted to recieve is passed to it for processing.
     * One of this or {@link #processClassWithFlags(ClassNode, Type)} <em>must</em> be implemented.
     *
     * @param classNode the classnode to process
     * @param classType the name of the class
     * @return true if the classNode needs rewriting using COMPUTE_FRAMES or false if it needs no NO_REWRITE
     */
    default boolean processClass(ClassNode classNode, Type classType) {
        throw new IllegalStateException("YOU NEED TO OVERRIDE ONE OF THE processClass methods");
    }

    /**
     * Capture a provider which can be used to view the state of any class, including those not transformed, after all
     * transformations before this one.
     * @param bytecodeProvider allows querying class bytes
     */
    default void initializeBytecodeProvider(IBytecodeProvider bytecodeProvider) {}

    interface IBytecodeProvider {
        byte[] acquireTransformedClassBefore(final String className) throws ClassNotFoundException;
    }
}
