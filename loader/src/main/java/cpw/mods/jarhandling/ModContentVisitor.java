package cpw.mods.jarhandling;

import java.io.InputStream;

@FunctionalInterface
public interface ModContentVisitor {
    void visit(String relativePath,
            IOSupplier<InputStream> contentSupplier,
            IOSupplier<ModContentAttributes> attributesSupplier);
}
