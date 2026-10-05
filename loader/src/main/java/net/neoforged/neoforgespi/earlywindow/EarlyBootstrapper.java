package net.neoforged.neoforgespi.earlywindow;

/// Defines a type which can be used to perform any bootstrap operations before
/// mod discovery and loading starts.
public interface EarlyBootstrapper {
    /// {@return the name of this bootstrapper} This is used for logging purposes.
    String name();

    /// Performs any bootstrapping that needs to be done before mod discovery
    /// and loading starts.
    ///
    /// @param arguments The arguments provided to the Java process. This is the
    ///                  entire command line, so you can process stuff from it.
    void bootstrap(String[] arguments);
}
