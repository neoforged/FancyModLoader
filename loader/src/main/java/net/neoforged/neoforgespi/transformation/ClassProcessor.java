package net.neoforged.neoforgespi.transformation;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.Set;

// TODO: this and ClassProcessorProvider are both on the PLUGIN layer -- the differences from coremods (namely, the
//  greater flexibility and therefore need to be careful with what you request processing for) should be documented.
// TODO: should we add some sort of warning screen on load if more than X% of classes are being transformed? This seems doable;
//  could even count this per-plugin to detect and warn on cases of transform-everything plugins.
public interface ClassProcessor {
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

    ProcessorName COMPUTING_FRAMES = new ProcessorName("neoforge", "computing_frames");

    /**
     * {@return a unique identifier for this processor}
     */
    ProcessorName name();

    /**
     * {@return processors that this processor must run before}
     */
    default Set<ProcessorName> runsBefore() {
        return Set.of();
    }

    /**
     * {@return processors that this processor must run after} This should include
     * {@link ClassProcessor#COMPUTING_FRAMES} if the processor returns a result requiring frame re-computation
     */
    default Set<ProcessorName> runsAfter() {
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
     * Context available when determining whether a processor wants to handle a class
     * @param type the class to consider
     * @param empty if the class is empty at present (indicates no backing file found)
     */
    record SelectionContext(Type type, boolean empty) {
        @ApiStatus.Internal
        public SelectionContext {}
    }

    /**
     * Context available when processing a class
     * @param type the class to process
     * @param node the current structure of the class
     * @param empty if the class is empty at present (indicates no backing file found and no previous processor has created it)
     */
    record TransformationContext(Type type, ClassNode node, boolean empty) {
        @ApiStatus.Internal
        public TransformationContext {}
    }

    /**
     * {@return whether the processor wants to recieve the class}
     * @param context the context of the class to consider
     */
    boolean handlesClass(SelectionContext context);

    /**
     * Each class that the processor has opted to recieve is passed to it for processing.
     * One of this or {@link #processClass(TransformationContext)} <em>must</em> be implemented.
     *
     * @param context the context of the class to process
     * @return the {@link ComputeFlags} indicating how the class should be rewritten.
     */
    default int processClassWithFlags(TransformationContext context) {
        return processClass(context) ? ComputeFlags.COMPUTE_FRAMES : ComputeFlags.NO_REWRITE;
    }

    /**
     * Each class that the processor has opted to recieve is passed to it for processing.
     * One of this or {@link #processClassWithFlags(TransformationContext)} <em>must</em> be implemented.
     *
     * @param context the context of the class to process
     * @return true if the classNode needs rewriting using COMPUTE_FRAMES or false if it needs no NO_REWRITE
     */
    default boolean processClass(TransformationContext context) {
        throw new IllegalStateException("You need to override one of the processClass methods");
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
