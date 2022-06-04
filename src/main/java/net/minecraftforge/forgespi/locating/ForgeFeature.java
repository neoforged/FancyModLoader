package net.minecraftforge.forgespi.locating;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * ForgeFeature is a simple test for mods for the presence of specific features
 * such as OpenGL of a specific version or better or whatever.
 *
 * {@snippet :
 *  ForgeFeature.registerFeature("openGLVersion", VersionFeatureTest.forVersionString("3.2"));
 * }
 *
 * This will be tested during early mod loading against lists of features in the mods.toml file for mods. Those
 * that are absent or out of range will be rejected.
 */
public class ForgeFeature {
    private ForgeFeature() {}
    private static final Map<String, IFeatureTest<?>> features = new HashMap<>();

    public static <T> void registerFeature(final String featureName, final IFeatureTest<T> featureTest) {
        if (features.putIfAbsent(featureName, featureTest) != null) {
            throw new IllegalArgumentException("ForgeFeature with name "+featureName +" exists");
        }
    }

    private static final MissingFeatureTest MISSING = new MissingFeatureTest();

    public static boolean testFeature(final Bound bound) {
        return features.getOrDefault(bound.featureName(), MISSING).testWithString(bound.featureBound());
    }
    public sealed interface IFeatureTest<F> extends Predicate<F> {
        F convertFromString(final String value);
        default boolean testWithString(final String value) {
            return test(convertFromString(value));
        }
    }

    /**
     * A Bound, from a mods.toml file
     *
     * @param featureName the name of the feature
     * @param featureBound the requested bound
     */
    public record Bound(String featureName, String featureBound) {}
    /**
     * Version based feature test. Uses standard MavenVersion system. Will test the constructed version against
     * ranges requested by mods.
     * @param version The version we wish to test against
     */
    public record VersionFeatureTest(ArtifactVersion version) implements IFeatureTest<VersionRange> {
        /**
         * Convenience method for constructing the feature test for a version string
         * @param version the string
         * @return the feature test for the supplied string
         */
        public static VersionFeatureTest forVersionString(final String version) {
            return new VersionFeatureTest(new DefaultArtifactVersion(version));
        }

        @Override
        public boolean test(final VersionRange versionRange) {
            return versionRange.containsVersion(version);
        }

        @Override
        public VersionRange convertFromString(final String value) {
            try {
                return VersionRange.createFromVersionSpec(value);
            } catch (InvalidVersionSpecificationException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public record BooleanFeatureTest(boolean value) implements IFeatureTest<Boolean> {
        @Override
        public boolean test(final Boolean aBoolean) {
            return aBoolean.equals(value);
        }

        @Override
        public Boolean convertFromString(final String value) {
            return Boolean.parseBoolean(value);
        }
    }

    private record MissingFeatureTest() implements IFeatureTest<Object> {
        @Override
        public boolean test(final Object o) {
            return false;
        }

        @Override
        public Object convertFromString(final String value) {
            return null;
        }
    }
}
