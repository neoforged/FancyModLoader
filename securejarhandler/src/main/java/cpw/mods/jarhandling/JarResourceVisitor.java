package cpw.mods.jarhandling;

@FunctionalInterface
public interface JarResourceVisitor {
    /**
     * @param relativePath
     * @param resource     A resource in the Jar file. Please note that this object will be reused for the next
     *                     object when this method is called again for the same jar file, so do not hold onto
     *                     a reference to this object.
     */
    void visit(String relativePath, JarResource resource);
}
