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

import cpw.mods.modlauncher.api.TypesafeMap;

/**
 * Entry point for the ModLauncher.
 */
@Deprecated(forRemoval = true)
public class Launcher {
    public static Launcher INSTANCE;
    private final Environment environment;
    private final TypesafeMap blackboard;
    // This is only here to keep Sinytra Connector working
    private final LaunchPluginHandler launchPlugins;

    public Launcher(Environment environment, LaunchPluginHandler launchPluginHandler) {
        this.blackboard = new TypesafeMap();
        this.environment = environment;
        this.launchPlugins = launchPluginHandler;
    }

    public final TypesafeMap blackboard() {
        return blackboard;
    }

    public Environment environment() {
        return this.environment;
    }
}
