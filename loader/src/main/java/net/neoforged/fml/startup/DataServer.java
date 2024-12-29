package net.neoforged.fml.startup;

public class DataServer extends Entrypoint {
    private DataServer() {}

    public static void main(String[] args) {
        try (var startup = startup(args)) {
            var main = createMainMethodCallable(startup.classLoader(), "net.minecraft.data.Main");
            main.invokeExact(startup.programArgs());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }
}
