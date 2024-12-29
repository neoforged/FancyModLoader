package net.neoforged.fml.startup;

public class DataClient extends Entrypoint {
    private DataClient() {}

    public static void main(String[] args) {
        try (var startup = startup(args)) {
            var main = createMainMethodCallable(startup.classLoader(), "net.minecraft.client.data.Main");
            main.invokeExact(startup.programArgs());
        } catch (Throwable t) {
            FatalErrorReporting.reportFatalError(t);
            System.exit(1);
        }
    }
}
