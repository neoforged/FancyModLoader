package cpw.mods.util;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Lazy<T>
{

    public static <T> Lazy<T> of() {
        return new Lazy<>((Supplier<T>) null);
    }

    public static <T> Lazy<T> of(final T value)
    {
        return new Lazy<T>(value);
    }

    public static <T> Lazy<T> of(final Supplier<T> provider)
    {
        return new Lazy<T>(provider);
    }

    private final Object lock = new Object();
    private T value;
    private Boolean initialized;
    private final Supplier<T> provider;

    private Lazy(final T value)
    {
        this.value = value;
        this.initialized = true;
        this.provider = () -> value;
    }

    private Lazy(final Supplier<T> provider)
    {
        this.value = null;
        this.initialized = false;
        this.provider = provider;
    }

    public T get()
    {
        synchronized (lock) {
            if (!initialized && provider != null) {
                initialized = true;
                this.value = provider.get();
            }

            return value;
        }
    }

    public void ifPresent(final Consumer<T> consumer) {
        synchronized (lock) {
            if (!initialized)
                return;

            consumer.accept(this.value);
        }
    }

    public <R> Lazy<R> map(Function<T, R> mapper) {
        synchronized (lock) {
            return of(() -> mapper.apply(get()));
        }
    }

    public T orElse(T elseValue) {
        synchronized (lock) {
            if (!initialized)
                return elseValue;

            return value;
        }
    }
}
