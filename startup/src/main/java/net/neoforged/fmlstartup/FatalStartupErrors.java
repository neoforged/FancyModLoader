package net.neoforged.fmlstartup;

import java.util.Collection;
import java.util.List;

public final class FatalStartupErrors {
    private FatalStartupErrors() {
    }

    public static FatalStartupException missingRequiredModules(Collection<String> missingModules) {
        var message = new StringBuilder("Failed to start because some required modules were missing:\n");
        for (String missingModule : missingModules) {
            message.append(" - ").append(missingModule).append("\n");
        }
        return new FatalStartupException(message.toString());
    }
}
