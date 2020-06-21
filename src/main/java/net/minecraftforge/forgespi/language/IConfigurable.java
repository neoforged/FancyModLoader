package net.minecraftforge.forgespi.language;

import java.util.List;
import java.util.Optional;


/**
 * This is an interface for querying configuration elements
 */
public interface IConfigurable {
    <T> Optional<T> getConfigElement(final String... key);
    public List<? extends IConfigurable> getConfigList(final String... key);
}
