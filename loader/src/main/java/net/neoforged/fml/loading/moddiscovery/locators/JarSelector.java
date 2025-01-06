/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.moddiscovery.locators;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.neoforged.jarjar.metadata.ContainedJarIdentifier;
import net.neoforged.jarjar.metadata.ContainedJarMetadata;
import net.neoforged.jarjar.metadata.ContainedVersion;
import net.neoforged.jarjar.metadata.Metadata;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import net.neoforged.jarjar.selection.util.Constants;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JarSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger(JarSelector.class);

    private JarSelector() {
        throw new IllegalStateException("Can not instantiate an instance of: JarSelector. This is a utility class");
    }

    public static <T, E extends Throwable> List<T> detectAndSelect(
            final List<T> source,
            final BiFunction<T, String, Optional<InputStream>> resourceReader,
            final BiFunction<T, String, Optional<T>> sourceProducer,
            final Function<T, String> identificationProducer,
            final Function<Collection<ResolutionFailureInformation<T>>, E> failureExceptionProducer) throws E {
        final Set<DetectionResult<T>> detectedMetadata = detect(source, resourceReader, sourceProducer, identificationProducer);
        final Multimap<ContainedJarMetadata, T> detectedJarsBySource = detectedMetadata.stream().collect(Multimaps.toMultimap(DetectionResult::metadata, DetectionResult::source, HashMultimap::create));
        final Multimap<ContainedJarMetadata, T> detectedJarsByRootSource = detectedMetadata.stream().collect(Multimaps.toMultimap(DetectionResult::metadata, DetectionResult::rootSource, HashMultimap::create));
        final Multimap<ContainedJarIdentifier, ContainedJarMetadata> metadataByIdentifier = Multimaps.index(detectedJarsByRootSource.keySet(), ContainedJarMetadata::identifier);

        final Set<SelectionResult> select = select(detectedJarsBySource.keySet());

        if (select.stream().anyMatch(result -> !result.selected().isPresent())) {
            //We have entered into failure territory. Let's collect all of those that failed
            final Set<SelectionResult> failed = select.stream().filter(result -> !result.selected().isPresent()).collect(Collectors.toSet());

            final List<ResolutionFailureInformation<T>> resolutionFailures = new ArrayList<>();
            for (final SelectionResult failedResult : failed) {
                final ContainedJarIdentifier failedIdentifier = failedResult.identifier();
                final Collection<ContainedJarMetadata> metadata = metadataByIdentifier.get(failedIdentifier);
                final Set<SourceWithRequestedVersionRange<T>> sources = metadata.stream().map(containedJarMetadata -> {
                    final Collection<T> rootSources = detectedJarsBySource.get(containedJarMetadata);
                    return new SourceWithRequestedVersionRange<T>(rootSources, containedJarMetadata.version().range(), containedJarMetadata.version().artifactVersion());
                })
                        .collect(Collectors.toSet());

                final ResolutionFailureInformation<T> resolutionFailure = new ResolutionFailureInformation<>(getFailureReason(failedResult), failedIdentifier, sources);

                resolutionFailures.add(resolutionFailure);
            }

            final E exception = failureExceptionProducer.apply(resolutionFailures);
            LOGGER.error("Failed to select jars for {}", resolutionFailures);
            throw exception;
        }

        final List<T> selectedJars = select.stream()
                .map(SelectionResult::selected)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(detectedJarsBySource::containsKey)
                .map(selectedJarMetadata -> {
                    final Collection<T> sourceOfJar = detectedJarsBySource.get(selectedJarMetadata);
                    return sourceProducer.apply(sourceOfJar.iterator().next(), selectedJarMetadata.path());
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        final Map<String, T> selectedJarsByIdentification = selectedJars.stream()
                .collect(Collectors.toMap(identificationProducer, Function.identity(), (t, t2) -> {
                    LOGGER.warn("Attempted to select two dependency jars from JarJar which have the same identification: {} and {}. Using {}", t, t2, t);
                    return t;
                }));

        final Map<String, T> sourceJarsByIdentification = source.stream()
                .collect(Collectors.toMap(identificationProducer, Function.identity(), (t, t2) -> {
                    LOGGER.warn("Attempted to select two source jars for JarJar which have the same identification: {} and {}. Using {}", t, t2, t);
                    return t;
                }));

        //Strip out jars which are already included by source. We can't do any resolution on this anyway so we force the use of those by not returning them.
        final Set<String> operatingKeySet = new HashSet<>(selectedJarsByIdentification.keySet()); //PREVENT CME's.
        operatingKeySet.stream().filter(sourceJarsByIdentification::containsKey)
                .peek(identification -> LOGGER.warn("Attempted to select a dependency jar for JarJar which was passed in as source: {}. Using {}", identification, sourceJarsByIdentification.get(identification)))
                .forEach(selectedJarsByIdentification::remove);
        return new ArrayList<>(selectedJarsByIdentification.values());
    }

    private static <T> Set<DetectionResult<T>> detect(
            final List<T> source,
            final BiFunction<T, String, Optional<InputStream>> resourceReader,
            final BiFunction<T, String, Optional<T>> sourceProducer,
            final Function<T, String> identificationProducer) {
        final Map<T, Optional<InputStream>> metadataInputStreamsBySource = source.stream().collect(
                Collectors.toMap(
                        Function.identity(),
                        t -> resourceReader.apply(t, Constants.CONTAINED_JARS_METADATA_PATH)));

        final Map<T, Metadata> rootMetadataBySource = metadataInputStreamsBySource.entrySet().stream()
                .filter(kvp -> kvp.getValue().isPresent())
                .map(kvp -> new SourceWithOptionalMetadata<>(kvp.getKey(), MetadataIOHandler.fromStream(kvp.getValue().get())))
                .filter(sourceWithOptionalMetadata -> sourceWithOptionalMetadata.metadata().isPresent())
                .collect(
                        Collectors.toMap(
                                SourceWithOptionalMetadata::source,
                                sourceWithOptionalMetadata -> sourceWithOptionalMetadata.metadata().get()));

        return recursivelyDetectContainedJars(
                rootMetadataBySource,
                resourceReader,
                sourceProducer,
                identificationProducer);
    }

    private static <T> Set<DetectionResult<T>> recursivelyDetectContainedJars(
            final Map<T, Metadata> rootMetadataBySource,
            final BiFunction<T, String, Optional<InputStream>> resourceReader,
            final BiFunction<T, String, Optional<T>> sourceProducer,
            final Function<T, String> identificationProducer) {
        final Set<DetectionResult<T>> results = Sets.newHashSet();
        final Map<T, T> rootSourcesBySource = Maps.newHashMap();

        final Queue<T> sourcesToProcess = new LinkedList<>();
        for (final Map.Entry<T, Metadata> entry : rootMetadataBySource.entrySet()) {
            entry.getValue().jars().stream().map(containedJarMetadata -> new DetectionResult<>(containedJarMetadata, entry.getKey(), entry.getKey()))
                    .forEach(results::add);

            for (final ContainedJarMetadata jar : entry.getValue().jars()) {
                final Optional<T> source = sourceProducer.apply(entry.getKey(), jar.path());
                if (source.isPresent()) {
                    sourcesToProcess.add(source.get());
                    rootSourcesBySource.put(source.get(), entry.getKey());
                } else {
                    LOGGER.warn("The source jar: " + identificationProducer.apply(entry.getKey()) + " is supposed to contain a jar: " + jar.path() + " but it does not exist.");
                }
            }
        }

        while (!sourcesToProcess.isEmpty()) {
            final T source = sourcesToProcess.remove();
            final T rootSource = rootSourcesBySource.get(source);
            final Optional<InputStream> metadataInputStream = resourceReader.apply(source, Constants.CONTAINED_JARS_METADATA_PATH);
            if (metadataInputStream.isPresent()) {
                final Optional<Metadata> metadata = MetadataIOHandler.fromStream(metadataInputStream.get());
                if (metadata.isPresent()) {
                    metadata.get().jars().stream().map(containedJarMetadata -> new DetectionResult<>(containedJarMetadata, source, rootSource))
                            .forEach(results::add);

                    for (final ContainedJarMetadata jar : metadata.get().jars()) {
                        final Optional<T> sourceJar = sourceProducer.apply(source, jar.path());
                        if (sourceJar.isPresent()) {
                            sourcesToProcess.add(sourceJar.get());
                            rootSourcesBySource.put(sourceJar.get(), rootSource);
                        } else {
                            LOGGER.warn("The source jar: " + identificationProducer.apply(source) + " is supposed to contain a jar: " + jar.path() + " but it does not exist.");
                        }
                    }
                }
            }
        }

        return results;
    }

    private static Set<SelectionResult> select(final Set<ContainedJarMetadata> containedJarMetadata) {
        final Multimap<ContainedJarIdentifier, ContainedJarMetadata> jarsByIdentifier = containedJarMetadata.stream()
                .collect(
                        Multimaps.toMultimap(
                                ContainedJarMetadata::identifier,
                                Function.identity(),
                                HashMultimap::create));

        return jarsByIdentifier.keySet().stream()
                .map(identifier -> {
                    final Collection<ContainedJarMetadata> jars = jarsByIdentifier.get(identifier);

                    if (jars.size() <= 1) {
                        //Quick return:
                        return new SelectionResult(identifier, jars, Optional.of(jars.iterator().next()), false);
                    }

                    //Find the most agreeable version:
                    final VersionRange range = jars.stream()
                            .map(ContainedJarMetadata::version)
                            .map(ContainedVersion::range)
                            .reduce(null, JarSelector::restrictRanges);

                    if (range == null || !isValid(range)) {
                        return new SelectionResult(identifier, jars, Optional.empty(), true);
                    }

                    if (range.getRecommendedVersion() != null) {
                        final Optional<ContainedJarMetadata> selected = jars.stream().filter(jar -> jar.version().artifactVersion().equals(range.getRecommendedVersion())).findFirst();
                        return new SelectionResult(identifier, jars, selected, false);
                    }

                    final Optional<ContainedJarMetadata> selected = jars.stream().filter(jar -> range.containsVersion(jar.version().artifactVersion())).findFirst();
                    return new SelectionResult(identifier, jars, selected, false);
                })
                .collect(Collectors.toSet());
    }

    private static VersionRange restrictRanges(final VersionRange versionRange, final VersionRange versionRange2) {
        if (versionRange == null) {
            return versionRange2;
        }

        if (versionRange2 == null) {
            return versionRange;
        }

        return versionRange.restrict(versionRange2);
    }

    private static boolean isValid(final VersionRange range) {
        return range.getRecommendedVersion() == null && range.hasRestrictions();
    }

    private static FailureReason getFailureReason(SelectionResult selectionResult) {
        if (selectionResult.selected().isPresent())
            throw new IllegalArgumentException("Resolution succeeded, not failure possible");

        if (selectionResult.noValidRangeFound())
            return FailureReason.VERSION_RESOLUTION_FAILED;

        return FailureReason.NO_MATCHING_JAR;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final class SourceWithOptionalMetadata<Z> {
        private final Z source;
        private final Optional<Metadata> metadata;

        SourceWithOptionalMetadata(Z source, Optional<Metadata> metadata) {
            this.source = source;
            this.metadata = metadata;
        }

        public Z source() {
            return source;
        }

        public Optional<Metadata> metadata() {
            return metadata;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final SourceWithOptionalMetadata that = (SourceWithOptionalMetadata) obj;
            return Objects.equals(this.source, that.source) &&
                    Objects.equals(this.metadata, that.metadata);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, metadata);
        }

        @Override
        public String toString() {
            return "SourceWithOptionalMetadata[" +
                    "source=" + source + ", " +
                    "metadata=" + metadata + ']';
        }
    }

    private static final class DetectionResult<Z> {
        private final ContainedJarMetadata metadata;
        private final Z source;
        private final Z rootSource;

        private DetectionResult(ContainedJarMetadata metadata, Z source, Z rootSource) {
            this.metadata = metadata;
            this.source = source;
            this.rootSource = rootSource;
        }

        public ContainedJarMetadata metadata() {
            return metadata;
        }

        public Z source() {
            return source;
        }

        public Z rootSource() {
            return rootSource;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final DetectionResult that = (DetectionResult) obj;
            return Objects.equals(this.metadata, that.metadata) &&
                    Objects.equals(this.source, that.source) &&
                    Objects.equals(this.rootSource, that.rootSource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metadata, source, rootSource);
        }

        @Override
        public String toString() {
            return "DetectionResult[" +
                    "metadata=" + metadata + ", " +
                    "source=" + source + ", " +
                    "rootSource=" + rootSource + ']';
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final class SelectionResult {
        private final ContainedJarIdentifier identifier;
        private final Collection<ContainedJarMetadata> candidates;
        private final Optional<ContainedJarMetadata> selected;
        private final boolean noValidRangeFound;

        private SelectionResult(ContainedJarIdentifier identifier, Collection<ContainedJarMetadata> candidates, Optional<ContainedJarMetadata> selected, final boolean noValidRangeFound) {
            this.identifier = identifier;
            this.candidates = candidates;
            this.selected = selected;
            this.noValidRangeFound = noValidRangeFound;
        }

        public ContainedJarIdentifier identifier() {
            return identifier;
        }

        public Collection<ContainedJarMetadata> candidates() {
            return candidates;
        }

        public Optional<ContainedJarMetadata> selected() {
            return selected;
        }

        public boolean noValidRangeFound() {
            return noValidRangeFound;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            final SelectionResult that = (SelectionResult) obj;
            return Objects.equals(this.identifier, that.identifier) &&
                    Objects.equals(this.candidates, that.candidates) &&
                    Objects.equals(this.selected, that.selected);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, candidates, selected);
        }

        @Override
        public String toString() {
            return "SelectionResult[" +
                    "identifier=" + identifier + ", " +
                    "candidates=" + candidates + ", " +
                    "selected=" + selected + ']';
        }
    }

    public enum FailureReason {
        VERSION_RESOLUTION_FAILED,
        NO_MATCHING_JAR,
    }

    public static final class SourceWithRequestedVersionRange<Z> {
        private final Collection<Z> sources;
        private final VersionRange requestedVersionRange;
        private final ArtifactVersion includedVersion;

        public SourceWithRequestedVersionRange(Collection<Z> sources, VersionRange requestedVersionRange, ArtifactVersion includedVersion) {
            this.sources = sources;
            this.requestedVersionRange = requestedVersionRange;
            this.includedVersion = includedVersion;
        }

        public Collection<Z> sources() {
            return sources;
        }

        public VersionRange requestedVersionRange() {
            return requestedVersionRange;
        }

        public ArtifactVersion includedVersion() {
            return includedVersion;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceWithRequestedVersionRange)) return false;

            final SourceWithRequestedVersionRange<?> that = (SourceWithRequestedVersionRange<?>) o;

            if (!sources.equals(that.sources)) return false;
            if (!requestedVersionRange.equals(that.requestedVersionRange)) return false;
            return includedVersion.equals(that.includedVersion);
        }

        @Override
        public int hashCode() {
            int result = sources.hashCode();
            result = 31 * result + requestedVersionRange.hashCode();
            result = 31 * result + includedVersion.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "SourceWithRequestedVersionRange{" +
                    "source=" + sources +
                    ", requestedVersionRange=" + requestedVersionRange +
                    ", includedVersion=" + includedVersion +
                    '}';
        }
    }

    public static final class ResolutionFailureInformation<Z> {
        private final FailureReason failureReason;
        private final ContainedJarIdentifier identifier;
        private final Collection<SourceWithRequestedVersionRange<Z>> sources;

        public ResolutionFailureInformation(final FailureReason failureReason, final ContainedJarIdentifier identifier, final Collection<SourceWithRequestedVersionRange<Z>> sources) {
            this.failureReason = failureReason;
            this.identifier = identifier;
            this.sources = sources;
        }

        public FailureReason failureReason() {
            return failureReason;
        }

        public ContainedJarIdentifier identifier() {
            return identifier;
        }

        public Collection<SourceWithRequestedVersionRange<Z>> sources() {
            return sources;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof ResolutionFailureInformation)) return false;

            final ResolutionFailureInformation<?> that = (ResolutionFailureInformation<?>) o;

            if (failureReason != that.failureReason) return false;
            if (!identifier.equals(that.identifier)) return false;
            return sources.equals(that.sources);
        }

        @Override
        public int hashCode() {
            int result = failureReason.hashCode();
            result = 31 * result + identifier.hashCode();
            result = 31 * result + sources.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "ResolutionFailureInformation{" +
                    "failureReason=" + failureReason +
                    ", identifier=" + identifier +
                    ", sources=" + sources +
                    '}';
        }
    }
}
