# BootstrapLauncher

> Allows bootstrapping a modularized environment from a classpath one.

BootstrapLauncher (BSL for short) uses the following information:

- The **(legacy) classpath information.** This is retrieved from the following, in descending order of priority:
    - The `legacyClassPath.file` system property, containing a _path list_ (paths separated by `;` on Windows and `:` on
      UNIX, as defined by [`File.pathSeparatorChar`][path_separator])
    - The `legacyClassPath` system property, containing a path list
    - The `java.class.path` system property.

  If none of the above is present, then an exception is thrown.

- The **ignore list**, specified by the `ignoreList` system property, as a comma-separated list of values. For any path
  within the classpath (as retrieved above) whose filename begins with any value in the ignore list, the path is ignored
  by BSL and not included in the bootstrap module layer created by BSL.

  By default, the ignore list is set to ignore filenames that start with `asm` or `securejarhandler` (the dependencies
  of BSL).

- The optional **module merge information**, specified by the `mergeModules` system property. This is used to combine
  multiple JAR files into a single logical module in the eyes of the module system. This property is a list of groups of
  comma-separated paths, where each group is separated by semicolons and denotes one module.

  For example: `a.jar,b.jar;b.jar,c.jar` means `a.jar` and `b.jar` are combined into one module, and `b.jar` and `c.jar`
  are combined into another module.

- The **bootstrap service**, which is a `Consumer<String[]>` service provided by a module in the bootstrap module layer.
  At least one such bootstrap service must exist, otherwise an exception is thrown. [ModLauncher][modlauncher] provides
  one such service: `BootstrapLaunchConsumer`.

Each JAR (unless included in the above module merge information) maps to one module in the bootstrap module layer.
Because all modules share the same classloader, no module may share a package with another module. Therefore, packages
are tracked and the first JAR which contains the module effectively 'owns' that package, and later JARs will not be
searched for the same package.

BSL creates a new module layer which has the following properties:

- The name of the module layer is `MC-BOOTSTRAP`.
- Its parent layer and configuration is the boot configuration (from [`ModuleLayer#boot()`][bootmodule]).
- It contains all the modules as provided in the classpath information (excluded from which the JARs who match the
  ignore list) and mapped according to the optional module merge information.

For easier debugging, additional debugging information is printed to `System.out` if the `bsl.debug` system property is
defined (regardless of its actual value).

[path_separator]: https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/io/File.html#pathSeparatorChar
[modlauncher]: https://github.com/McModLauncher/modlauncher
[bootmodule]: https://docs.oracle.com/en/java/javase/16/docs/api/java.base/java/lang/ModuleLayer.html#boot()