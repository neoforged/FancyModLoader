open module net.neoforged.fancymodloader.language.minecraft {
    requires net.neoforged.bus;
    requires net.neoforged.neoforgespi;
    requires net.neoforged.fancymodloader.core;
    requires org.apache.logging.log4j;
    requires static org.jetbrains.annotations;

    exports net.neoforged.fml.mclanguageprovider;

    provides net.neoforged.neoforgespi.language.IModLanguageProvider with
        net.neoforged.fml.mclanguageprovider.MinecraftModLanguageProvider;
}