package net.neoforged.neoforgespi.language;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModInfoTest {
    /**
     * Regression test to ensure that unbounded ranges are actually unbounded.
     * See <a href="https://github.com/neoforged/FancyModLoader/issues/34">issue</a>.
     */
    @Test
    public void testUnboundedRange() {
        assertTrue(IModInfo.UNBOUNDED.containsVersion(new DefaultArtifactVersion("0.0.1")));
        assertTrue(IModInfo.UNBOUNDED.containsVersion(new DefaultArtifactVersion("1.0.0")));
        assertTrue(IModInfo.UNBOUNDED.containsVersion(new DefaultArtifactVersion("10000.0.0")));
    }
}
