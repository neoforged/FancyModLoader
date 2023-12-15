import net.neoforged.fml.junit.JUnitService;
import org.junit.platform.launcher.LauncherSessionListener;

module net.neoforged.fancymodlauncher.junit {
    requires org.junit.platform.launcher;
    requires cpw.mods.bootstraplauncher;
    provides LauncherSessionListener with JUnitService;
}