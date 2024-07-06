package net.neoforged.fmlstartup;

import net.neoforged.fmlstartup.api.StartupArgs;

import java.lang.instrument.Instrumentation;

@FunctionalInterface
public interface StartupEntrypoint {
    void start(Instrumentation instrumentation, StartupArgs args);
}
