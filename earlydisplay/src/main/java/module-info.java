open module net.neoforged.fancymodloader.earlydisplay {
    requires java.desktop;
    requires java.management;
    requires jdk.management;
    requires jopt.simple;
    requires net.neoforged.fancymodloader.loader;
    requires org.lwjgl.glfw;
    requires org.lwjgl.opengl;
    requires org.lwjgl.stb;
    requires org.lwjgl.tinyfd;
    requires org.slf4j;
    requires static org.jetbrains.annotations;

    exports net.neoforged.fml.earlydisplay;

    provides net.neoforged.fml.loading.ImmediateWindowProvider with 
        net.neoforged.fml.earlydisplay.DisplayWindow;
}