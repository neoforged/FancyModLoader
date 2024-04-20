# Fancy Mod Loader

The mod loader used by [NeoForge](https://github.com/neoforged/NeoForge).

## Extension Points

### Mod File Candidate Locators

Responsible for locating potential mod files. Represents candidates as `JarContents`
from [SJH](https://github.com/McModLauncher/securejarhandler). This allows locators to also return joined directories (i.e. mod classes and resources in userdev).

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

### Mod File Provider

Responsible for supplying `IModFile` that are not based on candidates resolved through locators.

Interface: `net.neoforged.neoforgespi.locating.IModFileProvider`

Resolved via Java ServiceLoader.

Mod file instances can be created using the static methods on `IModFile`.
