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

package cpw.mods.modlauncher.log;

import cpw.mods.modlauncher.TransformingClassLoader;
import java.util.function.Function;
import org.apache.logging.log4j.core.pattern.TextRenderer;

public class ExtraDataTextRenderer implements TextRenderer {
    private final TextRenderer wrapped;
    private final Function<String, String> auditData;
    private final ThreadLocal<TransformerContext> currentClass = new ThreadLocal<>();

    ExtraDataTextRenderer(TextRenderer wrapped) {
        this.wrapped = wrapped;
        if (Thread.currentThread().getContextClassLoader() instanceof TransformingClassLoader transformingClassLoader) {
            auditData = transformingClassLoader::getAuditString;
        } else {
            auditData = ignored -> "";
        }
    }

    @Override
    public void render(final String input, final StringBuilder output, final String styleName) {
        if ("StackTraceElement.ClassName".equals(styleName)) {
            currentClass.set(new TransformerContext());
            currentClass.get().setClassName(input);
        } else if ("StackTraceElement.MethodName".equals(styleName)) {
            final TransformerContext transformerContext = currentClass.get();
            if (transformerContext != null) {
                transformerContext.setMethodName(input);
            }
        } else if ("Suffix".equals(styleName)) {
            final TransformerContext classContext = currentClass.get();
            currentClass.remove();
            if (classContext != null) {
                var auditLine = auditData.apply(classContext.getClassName());
                wrapped.render(" {" + auditLine + "}", output, "StackTraceElement.Transformers");
            }
            return;
        }
        wrapped.render(input, output, styleName);
    }

    @Override
    public void render(final StringBuilder input, final StringBuilder output) {
        wrapped.render(input, output);
    }

    private static class TransformerContext {
        private String className;
        private String methodName;

        public void setClassName(final String className) {
            this.className = className;
        }

        public String getClassName() {
            return className;
        }

        public void setMethodName(final String methodName) {
            this.methodName = methodName;
        }

        public String getMethodName() {
            return methodName;
        }

        @Override
        public String toString() {
            return getClassName() + "." + getMethodName();
        }
    }
}
