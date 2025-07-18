package cpw.mods.jarhandling;

@FunctionalInterface
public interface JarResourceVisitor {
    /**
     * @param relativePath The path of the file, relative to the root of the jar.
     * @param resource     A resource in the Jar file. Please note that this object will be reused for the next
     *                     object when this method is called again for the same jar file, so if you need to hold
     *                     onto this object outside your visitor, use {@link JarResource#retain()}.
     */
    void visit(String relativePath, JarResource resource);
}
