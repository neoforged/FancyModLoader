/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.core;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.IModLoadingState;
import net.neoforged.fml.IModStateProvider;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModLoadingPhase;
import net.neoforged.fml.ModLoadingStage;
import net.neoforged.fml.ModLoadingState;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.util.List;

/**
 * Provider for the core FML mod loading states.
 */
public class ModStateProvider implements IModStateProvider {
    /**
     * The special mod loading state for exceptional situations and error handling.
     *
     * @see ModLoadingPhase#ERROR
     */
    final ModLoadingState ERROR = ModLoadingState.empty("ERROR", "",
            ModLoadingPhase.ERROR);

    /**
     * First {@linkplain ModLoadingPhase#GATHER gathering state}, for the validation of the mod list.
     * TODO: figure out where this is used and why this exists instead of CONSTRUCT being the first state
     */
    private final ModLoadingState VALIDATE = ModLoadingState.empty("VALIDATE", "",
            ModLoadingPhase.GATHER);

    /**
     * {@linkplain ModLoadingPhase#GATHER Gathering state} after {@linkplain #VALIDATE validation}, for the construction
     * of mod containers and their backing mod instances.
     *
     * @see FMLConstructModEvent
     * @see ModLoadingStage#CONSTRUCT
     */
    final ModLoadingState CONSTRUCT = ModLoadingState.withTransition("CONSTRUCT", "VALIDATE",
            ml -> "Constructing %d mods".formatted(ml.size()),
            ModLoadingPhase.GATHER,
            new ParallelTransition(ModLoadingStage.CONSTRUCT, FMLConstructModEvent.class));

    /**
     * First {@linkplain ModLoadingPhase#LOAD loading state}, for loading of the common and (if applicable)
     * {@linkplain Dist#CLIENT client-side} mod configurations.
     */
    private final ModLoadingState CONFIG_LOAD = ModLoadingState.withInline("CONFIG_LOAD", "",
            ModLoadingPhase.LOAD,
            ml -> {
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.CLIENT, FMLPaths.CONFIGDIR.get());
                }
                ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
            });

    /**
     * {@linkplain ModLoadingPhase#LOAD Loading state} after {@linkplain #CONFIG_LOAD configuration loading}, for
     * common (non-side-specific) setup and initialization.
     *
     * @see FMLCommonSetupEvent
     * @see ModLoadingStage#COMMON_SETUP
     */
    private final ModLoadingState COMMON_SETUP = ModLoadingState.withTransition("COMMON_SETUP", "CONFIG_LOAD",
            ModLoadingPhase.LOAD,
            new ParallelTransition(ModLoadingStage.COMMON_SETUP, FMLCommonSetupEvent.class));

    /**
     * {@linkplain ModLoadingPhase#LOAD Loading state} after {@linkplain #COMMON_SETUP common setup}, for side-specific
     * setup and initialization.
     *
     * @see FMLClientSetupEvent
     * @see FMLDedicatedServerSetupEvent
     * @see ModLoadingStage#SIDED_SETUP
     */
    private final ModLoadingState SIDED_SETUP = ModLoadingState.withTransition("SIDED_SETUP", "COMMON_SETUP",
            ModLoadingPhase.LOAD,
            new ParallelTransition(ModLoadingStage.SIDED_SETUP,
                    FMLEnvironment.dist == Dist.CLIENT ? FMLClientSetupEvent.class : FMLDedicatedServerSetupEvent.class));

    /**
     * First {@linkplain ModLoadingPhase#COMPLETE completion state}, for enqueuing {@link InterModComms}
     * messages.
     *
     * @see InterModEnqueueEvent
     * @see ModLoadingStage#ENQUEUE_IMC
     */
    private final ModLoadingState ENQUEUE_IMC = ModLoadingState.withTransition("ENQUEUE_IMC", "",
            ModLoadingPhase.COMPLETE,
            new ParallelTransition(ModLoadingStage.ENQUEUE_IMC, InterModEnqueueEvent.class));

    /**
     * {@linkplain ModLoadingPhase#COMPLETE Completion state} after {@linkplain #ENQUEUE_IMC}, for processing of messages
     * received through {@link InterModComms}.
     *
     * @see InterModProcessEvent
     * @see ModLoadingStage#PROCESS_IMC
     */
    private final ModLoadingState PROCESS_IMC = ModLoadingState.withTransition("PROCESS_IMC", "ENQUEUE_IMC",
            ModLoadingPhase.COMPLETE,
            new ParallelTransition(ModLoadingStage.PROCESS_IMC, InterModProcessEvent.class));

    /**
     * {@linkplain ModLoadingPhase#COMPLETE Completion state} after {@linkplain #PROCESS_IMC}, marking the completion
     * of the basic mod loading process; however, additional completion states may be present after this.
     *
     * @see FMLLoadCompleteEvent
     * @see ModLoadingStage#COMPLETE
     */
    private final ModLoadingState COMPLETE = ModLoadingState.withTransition("COMPLETE", "PROCESS_IMC",
            ml -> "completing load of %d mods".formatted(ml.size()),
            ModLoadingPhase.COMPLETE,
            new ParallelTransition(ModLoadingStage.COMPLETE, FMLLoadCompleteEvent.class));

    /**
     * The marker state for the completion of the full mod loading process.
     *
     * @see ModLoadingStage#DONE
     */
    private final ModLoadingState DONE = ModLoadingState.empty("DONE", "",
            ModLoadingPhase.DONE);

    @Override
    public List<IModLoadingState> getAllStates() {
        return List.of(ERROR, VALIDATE, CONSTRUCT, CONFIG_LOAD, COMMON_SETUP, SIDED_SETUP, ENQUEUE_IMC, PROCESS_IMC, COMPLETE, DONE);
    }
}
