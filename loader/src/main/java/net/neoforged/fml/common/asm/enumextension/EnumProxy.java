package net.neoforged.fml.common.asm.enumextension;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;

public final class EnumProxy<T extends Enum<T> & IExtensibleEnum> {
    private final Class<T> enumClass;
    private final List<Object> parameters;
    private T enumValue;

    public EnumProxy(Class<T> enumClass, Object... parameters) {
        this(enumClass, Arrays.asList(parameters));
    }

    public EnumProxy(Class<T> enumClass, List<Object> parameters) {
        this.enumClass = enumClass;
        this.parameters = parameters;
    }

    @ApiStatus.Internal
    public Object getParameter(int idx) {
        return parameters.get(idx);
    }

    public T getValue() {
        if (enumValue == null) {
            try {
                var loader = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass().getClassLoader();
                Class.forName(enumClass.getName(), true, loader);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return Objects.requireNonNull(enumValue, "Enum not initialized");
    }

    @ApiStatus.Internal
    public void setValue(T value) {
        this.enumValue = value;
    }
}
