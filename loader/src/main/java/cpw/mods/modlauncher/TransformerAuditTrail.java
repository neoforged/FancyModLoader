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

import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.api.ITransformerAuditTrail;
import net.neoforged.neoforgespi.transformation.ClassProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TransformerAuditTrail implements ITransformerAuditTrail {
    private final Map<String, List<ITransformerActivity>> audit = new ConcurrentHashMap<>();

    @Override
    public List<ITransformerActivity> getActivityFor(final String className) {
        return Collections.unmodifiableList(getTransformerActivities(className));
    }

    private record TransformerActivity(String... context) implements ITransformerActivity {
        public String getActivityString() {
            return String.join(":", this.context);
        }
    }

    public void addClassProcessor(String clazz, ClassProcessor classProcessor) {
        getTransformerActivities(clazz).add(new TransformerActivity(classProcessor.name()));
    }

    private List<ITransformerActivity> getTransformerActivities(final String clazz) {
        return audit.computeIfAbsent(clazz, k -> new ArrayList<>());
    }

    @Override
    public String getAuditString(final String clazz) {
        return audit.getOrDefault(clazz, Collections.emptyList()).stream().map(ITransformerActivity::getActivityString).collect(Collectors.joining(","));
    }
}
