# Fancy Mod Loader

The mod loader used by [NeoForge](https://github.com/neoforged/NeoForge).

## Testing

The `tests` subproject provides several tasks to test FML in various usage scenarios without having to include
it in a NeoForge working directory.

The Gradle property `test_neoforge_version` controls, which NeoForge version is used for these tests.

### Production Client

Run the `:tests:runProductionClient` task to start FML in an environment resembling a client launched through the
Vanilla launcher.

### Production Server

Run the `:tests:runProductionServer` task to start FML in an environment resembling a server launched through one of
the NeoForge provided startup scripts.

### Mod-Development

Run the `:tests:runClient` or `:tests:runServer` tasks to start FML in an environment resembling a mod development
environment.

## Extension Points

### Mod File Candidate Locators

Responsible for locating potential mod files. Filesystem locations, virtual jars or even full mod-files can be reported to the discovery pipeline for inclusion in the mod loading process.
The pipeline also offers a way for locators to add issues (warnings & errors) that will later be shown to the user when mod loading concludes.

Interface: `net.neoforged.neoforgespi.locating.IModFileCandidateLocator`

Resolved via Java ServiceLoader.

You can construct a basic locator to scan a folder for mods by using `IModFileCandidateLocator.forFolder`. This can be
useful if your locator generates a folder on-disk and wants to delegate to default behavior for it (For example used
by [ServerPackLocator](https://github.com/marchermans/serverpacklocator/)).

### Mod File Readers

Responsible for creating a `IModFile` for mod file candidates.

The default implementation will resolve the type of the mod file by inspecting the Jar manifest or the mod metadata
file (`neoforge.mods.toml`) and return an `IModFile` instance if this succeeds.

Interface: `net.neoforged.neoforgespi.locating.IModFileReader`

Resolved via Java ServiceLoader.

Mod file instances can be created using the static methods on `IModFile`.
