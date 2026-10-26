package com.github.blade.hvac.service;

import com.github.blade.hvac.model.Thermostat;

import java.io.File;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Offline registry host: a plain folder, a logger that remembers what it was
 * told, and a disabled plugin, so every save runs synchronously on the caller.
 */
final class TestHost implements HvacRegistry.Host {
    private final File folder;
    private final Logger logger;
    private final List<LogRecord> records = new ArrayList<>();

    TestHost(File folder) {
        this.folder = folder;
        this.logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
    }

    /** Messages logged at WARNING or above. */
    List<String> warnings() {
        return records.stream().filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                .map(LogRecord::getMessage).toList();
    }

    /** The thermostat with this label (ignoring case) at the lowest position, in any world; or null. */
    static Thermostat labelled(HvacRegistry registry, String label) {
        return registry.thermostats().stream().filter(thermostat -> thermostat.id().equalsIgnoreCase(label))
                .min(Comparator.comparing(Thermostat::position)).orElse(null);
    }

    @Override public File dataFolder() { return folder; }
    @Override public Logger logger() { return logger; }
    @Override public boolean isEnabled() { return false; }
    @Override public void runAsync(Runnable task) { task.run(); }
    @Override public void runSync(Runnable task) { task.run(); }
}
