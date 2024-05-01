/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading.modscan;

import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.ImmediateWindowHandler;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.slf4j.Logger;

public class BackgroundScanHandler {
    private enum ScanStatus {
        NOT_STARTED,
        RUNNING,
        COMPLETE,
        TIMED_OUT,
        INTERRUPTED,
        ERRORED
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ExecutorService modContentScanner;
    private final List<ModFile> pendingFiles;
    private final List<ModFile> scannedFiles;
    private final List<ModFile> allFiles;
    private ScanStatus status;
    private LoadingModList loadingModList;

    public BackgroundScanHandler() {
        int maxThreads = FMLConfig.getIntConfigValue(FMLConfig.ConfigValue.MAX_THREADS);
        // Leave 1 thread for Minecraft's own bootstrap
        int poolSize = Math.max(1, maxThreads - 1);
        AtomicInteger threadCount = new AtomicInteger();
        modContentScanner = Executors.newFixedThreadPool(poolSize, r -> {
            final Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setDaemon(true);
            thread.setName("background-scan-handler-" + threadCount.getAndIncrement());
            return thread;
        });
        scannedFiles = new ArrayList<>();
        pendingFiles = new ArrayList<>();
        allFiles = new ArrayList<>();
        status = ScanStatus.NOT_STARTED;
    }

    public void submitForScanning(final ModFile file) {
        if (modContentScanner.isShutdown()) {
            status = ScanStatus.ERRORED;
            throw new IllegalStateException("Scanner has shutdown");
        }
        status = ScanStatus.RUNNING;
        ImmediateWindowHandler.updateProgress("Scanning mod candidates");
        allFiles.add(file);
        pendingFiles.add(file);
        final CompletableFuture<ModFileScanData> future = CompletableFuture.supplyAsync(file::compileContent, modContentScanner)
                .whenComplete(file::setScanResult)
                .whenComplete((r, t) -> this.addCompletedFile(file, r, t));
        file.setFutureScanResult(future);
    }

    private synchronized void addCompletedFile(final ModFile file, final ModFileScanData modFileScanData, final Throwable throwable) {
        if (throwable != null) {
            status = ScanStatus.ERRORED;
            LOGGER.error(LogMarkers.SCAN, "An error occurred scanning file {}", file, throwable);
        }
        pendingFiles.remove(file);
        scannedFiles.add(file);
    }

    public void setLoadingModList(LoadingModList loadingModList) {
        this.loadingModList = loadingModList;
    }

    public LoadingModList getLoadingModList() {
        return loadingModList;
    }

    public void waitForScanToComplete(final Runnable ticker) {
        boolean timeoutActive = System.getProperty("fml.disableScanTimeout") == null;
        Instant deadline = Instant.now().plus(Duration.ofMinutes(10));
        modContentScanner.shutdown();
        do {
            ticker.run();
            try {
                status = modContentScanner.awaitTermination(50, TimeUnit.MILLISECONDS) ? ScanStatus.COMPLETE : ScanStatus.RUNNING;
            } catch (InterruptedException e) {
                status = ScanStatus.INTERRUPTED;
            }
            if (timeoutActive && Instant.now().isAfter(deadline)) status = ScanStatus.TIMED_OUT;
        } while (status == ScanStatus.RUNNING);
        if (status == ScanStatus.INTERRUPTED) Thread.currentThread().interrupt();
        if (status != ScanStatus.COMPLETE) throw new IllegalStateException("Failed to complete mod scan");
    }
}
