/*
 * ModLauncher - for launching Java programs with in-flight transformation ability.
 * Copyright (C) 2017-2019 cpw
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package cpw.mods.modlauncher;

import static cpw.mods.modlauncher.LogMarkers.MODLAUNCHER;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TransformationServicesHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private Map<String, TransformationServiceDecorator> serviceLookup;
    private final TransformStore transformStore;

    public TransformationServicesHandler(TransformStore transformStore, Environment environment, Collection<ITransformationService> services) {
        this.transformStore = transformStore;
        setTransformationServices(services, environment);
    }

    public void initializeTransformationServices(ArgumentHandler argumentHandler, Environment environment) {
        loadTransformationServices(environment);
        validateTransformationServices();
        processArguments(argumentHandler, environment);
        initialiseTransformationServices(environment);
    }

    private void processArguments(ArgumentHandler argumentHandler, Environment environment) {
        LOGGER.debug(MODLAUNCHER, "Configuring option handling for services");

        argumentHandler.processArguments(environment, this::computeArgumentsForServices, this::offerArgumentResultsToServices);
    }

    private void computeArgumentsForServices(OptionParser parser) {
        serviceLookup.values().stream()
                .map(TransformationServiceDecorator::getService)
                .forEach(service -> service.arguments((a, b) -> parser.accepts(service.name() + "." + a, b)));
    }

    private void offerArgumentResultsToServices(OptionSet optionSet, BiFunction<String, OptionSet, ITransformationService.OptionResult> resultHandler) {
        serviceLookup.values().stream()
                .map(TransformationServiceDecorator::getService)
                .forEach(service -> service.argumentValues(resultHandler.apply(service.name(), optionSet)));
    }

    public void initialiseServiceTransformers() {
        LOGGER.debug(MODLAUNCHER, "Transformation services loading transformers");

        serviceLookup.values().forEach(s -> s.gatherTransformers(transformStore));
    }

    private void initialiseTransformationServices(Environment environment) {
        LOGGER.debug(MODLAUNCHER, "Transformation services initializing");

        serviceLookup.values().forEach(s -> s.onInitialize(environment));
    }

    private void validateTransformationServices() {
        if (serviceLookup.values().stream().filter(d -> !d.isValid()).count() > 0) {
            final List<ITransformationService> services = serviceLookup.values().stream().filter(d -> !d.isValid()).map(TransformationServiceDecorator::getService).collect(Collectors.toList());
            final String names = services.stream().map(ITransformationService::name).collect(Collectors.joining(","));
            LOGGER.error(MODLAUNCHER, "Found {} services that failed to load : [{}]", services.size(), names);
            throw new InvalidLauncherSetupException("Invalid Services found " + names);
        }
    }

    private void loadTransformationServices(Environment environment) {
        LOGGER.debug(MODLAUNCHER, "Transformation services loading");
        serviceLookup.values().forEach(s -> s.onLoad(environment, serviceLookup.keySet()));
    }

    private void setTransformationServices(Collection<ITransformationService> services, Environment environment) {
        serviceLookup = services.stream().collect(Collectors.toMap(ITransformationService::name, TransformationServiceDecorator::new));
        var modlist = serviceLookup.entrySet().stream().map(e -> Map.of(
                "name", e.getKey(),
                "type", "TRANSFORMATIONSERVICE",
                "file", ServiceLoaderUtils.fileNameFor(e.getValue().getClass()))).toList();
        environment.getProperty(IEnvironment.Keys.MODLIST.get()).ifPresent(ml -> ml.addAll(modlist));
        LOGGER.debug(MODLAUNCHER, "Found transformer services : [{}]", () -> String.join(",", serviceLookup.keySet()));
    }
}
