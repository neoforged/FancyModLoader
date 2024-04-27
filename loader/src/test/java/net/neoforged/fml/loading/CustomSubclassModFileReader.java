package net.neoforged.fml.loading;

import static org.mockito.Mockito.mock;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;

public class CustomSubclassModFileReader implements IModFileReader {
    public static final IdentifiableContent TRIGGER = new IdentifiableContent("CUSTOM_MODFILE_SUBCLASS_TRIGGER", "custom_modfile_subclass_trigger");

    @Override
    public @Nullable IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
        if (jar.findFile(TRIGGER.relativePath()).isPresent()) {
            return mock(IModFile.class);
        }
        return null;
    }
}
