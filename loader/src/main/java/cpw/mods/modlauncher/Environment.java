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

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.TypesafeMap;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import java.util.Optional;
import java.util.function.Function;

/**
 * Environment implementation class
 */
public final class Environment implements IEnvironment {
    private final TypesafeMap environment = new TypesafeMap(IEnvironment.class);
    private final Function<String, Optional<ILaunchPluginService>> launchPlugins;
    private final Function<String, Optional<ILaunchHandlerService>> launchService;

    public Environment(Function<String, Optional<ILaunchPluginService>> launchPlugins,
            Function<String, Optional<ILaunchHandlerService>> launchService) {
        this.launchPlugins = launchPlugins;
        this.launchService = launchService;
    }

    @Override
    public <T> Optional<T> getProperty(TypesafeMap.Key<T> key) {
        return environment.get(key);
    }

    @Override
    public Optional<ILaunchPluginService> findLaunchPlugin(final String name) {
        return launchPlugins.apply(name);
    }

    @Override
    public Optional<ILaunchHandlerService> findLaunchHandler(final String name) {
        return launchService.apply(name);
    }

    @Override
    public <T> T computePropertyIfAbsent(final TypesafeMap.Key<T> key, final Function<? super TypesafeMap.Key<T>, ? extends T> valueFunction) {
        return environment.computeIfAbsent(key, valueFunction);
    }
}