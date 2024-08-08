package fmlbuild;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * Apply this plugin to get access to a local production client installation.
 */
public abstract class NeoForgeInstallationsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var installations = project.getObjects().polymorphicDomainObjectContainer(NeoForgeInstallation.class);
        installations.registerFactory(fmlbuild.NeoForgeClientInstallation.class, name -> {
            return project.getObjects().newInstance(NeoForgeClientInstallation.class, name);
        });
        installations.registerFactory(fmlbuild.NeoForgeServerInstallation.class, name -> {
            return project.getObjects().newInstance(NeoForgeServerInstallation.class, name);
        });

        // Shared configurations
        var dependencyFactory = project.getDependencyFactory();
        var nfrtCliConfig = project.getConfigurations().create("nfrtCli", config -> {
            config.setDescription("This configuration pulls the NFRT CLI tool");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.setTransitive(false);
            config.withDependencies(dependencies -> {
                dependencies.add(dependencyFactory.create("net.neoforged:neoform-runtime:0.1.64:all"));
            });
        });

        // When a new install is declared, immediately create the associated Gradle objects
        project.getExtensions().add("neoForgeInstallations", installations);
        installations.all(installation -> {
            if (installation instanceof NeoForgeClientInstallation clientInstallation) {
                addClientInstallation(project, nfrtCliConfig, clientInstallation);
            } else if (installation instanceof NeoForgeServerInstallation serverInstallation) {
                addServerInstallation(project, serverInstallation);
            }
        });
        installations.whenObjectRemoved(installation -> {
            throw new GradleException("Cannot remove installations once they have been registered");
        });
    }

    private void addClientInstallation(Project project, Configuration nfrtCliConfig, NeoForgeClientInstallation installation) {
        var depFactory = project.getDependencyFactory();

        var capitalizedName = installation.getName();
        capitalizedName = capitalizedName.substring(0, 1).toUpperCase() + capitalizedName.substring(1);

        var installerConfig = project.getConfigurations().create("neoForgeInstaller" + capitalizedName, config -> {
            config.setDescription("This configuration pulls the NeoForge installer fat-jar for the requested version of NeoForge");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.setTransitive(false);
            config.withDependencies(dependencies -> {
                dependencies.addLater(installation
                        .getVersion()
                        .map(v -> depFactory
                                .create("net.neoforged:neoforge:" + v).capabilities(caps -> {
                                    caps.requireCapability("net.neoforged:neoforge-installer");
                                })));
            });
        });

        project.getTasks().register("installNeoForge" + capitalizedName, InstallProductionClientTask.class, task -> {
            task.setGroup("fml/installations");
            task.getInstaller().from(installerConfig);
            task.getNfrt().from(nfrtCliConfig);
            task.getMinecraftVersion().set(installation.getMinecraftVersion());
            task.getNeoForgeVersion().set(installation.getVersion());
            task.getInstallDir().set(installation.getDirectory());
            task.getAssetsDir().set(task.getInstallDir().dir("assets"));
            task.getLibrariesDir().set(task.getInstallDir().dir("libraries"));

            // Write the JVM args to files
            task.getVanillaJvmArgFile().set(installation.getVanillaJvmArgFile());
            task.getVanillaMainClassArgFile().set(installation.getVanillaMainClassArgFile());
            task.getVanillaProgramArgFile().set(installation.getVanillaProgramArgFile());
            task.getNeoForgeJvmArgFile().set(installation.getNeoForgeJvmArgFile());
            task.getNeoForgeMainClassArgFile().set(installation.getNeoForgeMainClassArgFile());
            task.getNeoForgeProgramArgFile().set(installation.getNeoForgeProgramArgFile());

            // Path to the obfuscated Minecraft jar
            var obfuscatedJar = task.getInstallDir().file(installation.getMinecraftVersion().map(minecraftVersion -> "versions/%s/%s.jar".formatted(minecraftVersion, minecraftVersion)));
            task.getObfuscatedClientJar().set(obfuscatedJar);
        });
    }

    private void addServerInstallation(Project project, NeoForgeServerInstallation installation) {
        var depFactory = project.getDependencyFactory();

        var capitalizedName = installation.getName();
        capitalizedName = capitalizedName.substring(0, 1).toUpperCase() + capitalizedName.substring(1);

        var installerConfig = project.getConfigurations().create("neoForgeInstaller" + capitalizedName, config -> {
            config.setDescription("This configuration pulls the NeoForge installer fat-jar for the requested version of NeoForge");
            config.setCanBeResolved(true);
            config.setCanBeConsumed(false);
            config.setTransitive(false);
            config.withDependencies(dependencies -> {
                dependencies.addLater(installation
                        .getVersion()
                        .map(v -> depFactory
                                .create("net.neoforged:neoforge:" + v).capabilities(caps -> {
                                    caps.requireCapability("net.neoforged:neoforge-installer");
                                })));
            });
        });

        project.getTasks().register("installNeoForge" + capitalizedName, InstallProductionServerTask.class, task -> {
            task.setGroup("fml/installations");
            task.getInstaller().from(installerConfig);
            task.getInstallDir().set(installation.getDirectory());
            task.getNeoForgeVersion().set(installation.getVersion());

            // Write the JVM args to files
            task.getNeoForgeJvmArgFile().set(installation.getNeoForgeJvmArgFile());
            task.getNeoForgeMainClassArgFile().set(installation.getNeoForgeMainClassArgFile());
            task.getNeoForgeProgramArgFile().set(installation.getNeoForgeProgramArgFile());
        });
    }
}
