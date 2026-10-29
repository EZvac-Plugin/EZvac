package com.github.blade.hvac.config;

import com.github.blade.hvac.model.DeviceKind;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What /hvac quicksettings switches while the server runs (BETA 1.0): air
 * quality, pollen, wind and the seasons, and device kinds switched off
 * server-wide. Each lives in config.yml, and a change is written back to it.
 *
 * <p>While EZvac runs, the installed switches are what the settings records'
 * {@code enabled()} answer, so every check of a feature, from the HUD to the
 * API, follows a change at once. Without them (tests, or before start-up)
 * the records answer what config.yml said.
 */
public final class ServerSwitches {
    private static volatile ServerSwitches live;

    private final FileConfiguration config;
    private final Runnable saveConfig;
    private volatile boolean airQuality;
    private volatile boolean pollen;
    private volatile boolean wind;
    private volatile boolean seasons;
    /** Replaced whole on a change, so a reader off the main thread never sees it half-made. */
    private volatile Set<DeviceKind> off;

    private ServerSwitches(FileConfiguration config, Runnable saveConfig, Set<DeviceKind> off) {
        this.config = config;
        this.saveConfig = saveConfig;
        this.airQuality = config.getBoolean("air-quality.enabled", true);
        this.pollen = config.getBoolean("air-quality.pollen", true);
        this.wind = config.getBoolean("wind.enabled", true);
        this.seasons = config.getBoolean("seasons.enabled", true);
        this.off = off;
    }

    /** Reads the switches; a name in devices.disabled that is no kind is warned about and skipped. */
    public static ServerSwitches load(FileConfiguration config, Runnable saveConfig, Consumer<String> warn) {
        Set<DeviceKind> off = EnumSet.noneOf(DeviceKind.class);
        for (String name : config.getStringList("devices.disabled")) {
            DeviceKind kind = DeviceKind.parse(name);
            if (kind == null) warn.accept("devices.disabled: '" + name + "' is not a device kind, so it was"
                    + " ignored. The kinds are " + String.join(", ", configNames()) + ".");
            else off.add(kind);
        }
        return new ServerSwitches(config, saveConfig, Set.copyOf(off));
    }

    /** Makes these the switches the settings answer from, until {@link #uninstall()}. */
    public void install() { live = this; }

    public void uninstall() {
        if (live == this) live = null;
    }

    /** What a settings record's enabled() answers: the live switch, or config.yml's value without one. */
    static boolean airQuality(boolean configured) {
        ServerSwitches switches = live;
        return switches == null ? configured : switches.airQuality;
    }

    static boolean wind(boolean configured) {
        ServerSwitches switches = live;
        return switches == null ? configured : switches.wind;
    }

    static boolean seasons(boolean configured) {
        ServerSwitches switches = live;
        return switches == null ? configured : switches.seasons;
    }

    static boolean pollenOn() {
        ServerSwitches switches = live;
        return switches == null || switches.pollen;
    }

    /** Whether this kind is switched off server-wide right now. */
    public static boolean deviceOff(DeviceKind kind) {
        ServerSwitches switches = live;
        return switches != null && switches.off.contains(kind);
    }

    public boolean airQuality() { return airQuality; }
    public boolean pollen() { return pollen; }
    public boolean wind() { return wind; }
    public boolean seasons() { return seasons; }
    public boolean isOff(DeviceKind kind) { return off.contains(kind); }

    public void setAirQuality(boolean on) {
        airQuality = on;
        write("air-quality.enabled", on);
    }

    public void setPollen(boolean on) {
        pollen = on;
        write("air-quality.pollen", on);
    }

    public void setWind(boolean on) {
        wind = on;
        write("wind.enabled", on);
    }

    public void setSeasons(boolean on) {
        seasons = on;
        write("seasons.enabled", on);
    }

    public void setOff(DeviceKind kind, boolean switchedOff) {
        Set<DeviceKind> next = EnumSet.noneOf(DeviceKind.class);
        next.addAll(off);
        if (switchedOff) next.add(kind);
        else next.remove(kind);
        off = Set.copyOf(next);
        List<String> names = new ArrayList<>();
        for (DeviceKind each : next) names.add(each.configName());
        write("devices.disabled", names);
    }

    private void write(String path, Object value) {
        config.set(path, value);
        saveConfig.run();
    }

    private static List<String> configNames() {
        List<String> names = new ArrayList<>();
        for (DeviceKind kind : DeviceKind.values()) names.add(kind.configName());
        return names;
    }
}
