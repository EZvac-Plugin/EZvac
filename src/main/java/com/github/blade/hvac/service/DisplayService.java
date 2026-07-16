package com.github.blade.hvac.service;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.EtaEstimator;
import com.github.blade.hvac.simulation.ThermalModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.util.*;
import java.util.logging.Level;

/** Renders all sign devices from authoritative state with write de-duplication. */
public final class DisplayService {
    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final AirflowService airflow;
    private final OutdoorTemperatureService outdoor;
    private final HvacSettings settings;
    private final Map<BlockKey, String> fingerprints = new HashMap<>();

    public DisplayService(HvacPlugin plugin, HvacRegistry registry, AirflowService airflow,
                          OutdoorTemperatureService outdoor, HvacSettings settings) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.airflow = Objects.requireNonNull(airflow, "airflow");
        this.outdoor = Objects.requireNonNull(outdoor, "outdoor");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void refreshAll(boolean force) {
        for (Thermostat thermostat : registry.thermostats()) refreshThermostat(thermostat, force);
        for (Thermometer thermometer : registry.thermometers()) refreshThermometer(thermometer, force);
        for (RpmMonitor monitor : registry.rpmMonitors()) refreshRpmMonitor(monitor, force);
        for (SettingsPanel panel : registry.settingsPanels()) refreshSettingsPanel(panel, force);
    }

    public void invalidate(BlockKey position) { fingerprints.remove(position); }

    public void refreshThermostat(Thermostat thermostat, boolean force) {
        String mode = switch (thermostat.mode()) {
            case COOLING -> "COOL";
            case HEATING -> "HEAT";
            case IDLE -> thermostat.enabled() ? "IDLE" : "OFF";
        };
        List<EquipmentUnit> running = registry.equipmentFor(thermostat.group()).stream()
                .filter(unit -> unit.running() && unit.operatingMode() == thermostat.mode()).toList();
        double fixedEquivalent = running.stream().filter(unit -> unit.type() == EquipmentType.FURNACE)
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        double variableEquivalent = running.stream().filter(unit -> unit.type() != EquipmentType.FURNACE)
                .mapToDouble(unit -> unit.operatingCapacity(settings)).sum();
        double capacity = ThermalModel.aggregateIndependentCapacity(fixedEquivalent,
                variableEquivalent, settings.maximumGroupCapacity());
        int percent = (int) Math.round(capacity * 100.0);
        List<EtaEstimator.UnitSnapshot> snapshots = running.stream()
                .map(unit -> new EtaEstimator.UnitSnapshot(unit.type(), unit.motorRpm())).toList();
        OperatingProfile profile = registry.profileFor(thermostat.group());
        OptionalLong eta = EtaEstimator.estimateTicks(thermostat.roomF(), thermostat.targetF(),
                outdoor.temperatureFor(thermostat), thermostat.mode(), snapshots, profile, settings);
        String footer = !thermostat.enabled() ? "System off"
                : thermostat.mode() == OperatingMode.IDLE ? "Ready"
                : eta.isPresent() ? "ETA " + formatEta(eta) : "ETA --";
        String fingerprint = String.format(Locale.US, "%.1f|%.1f|%s|%d|%s|%s|%s",
                thermostat.roomF(), thermostat.targetF(), mode, percent, footer,
                thermostat.group().label(), thermostat.id());
        render(thermostat.position(), fingerprint, force, side -> {
            side.line(0, Component.text(shorten("EZvac | " + thermostat.id()), NamedTextColor.AQUA));
            NamedTextColor color = thermostat.mode() == OperatingMode.COOLING ? NamedTextColor.AQUA
                    : thermostat.mode() == OperatingMode.HEATING ? NamedTextColor.GOLD : NamedTextColor.GRAY;
            side.line(1, Component.text(shorten(String.format(Locale.US, "%.1f F | %s",
                    thermostat.roomF(), mode)), color));
            String setpoint = String.format(Locale.US, "Set %.1f F", thermostat.targetF());
            if (thermostat.mode() != OperatingMode.IDLE) setpoint += " | " + percent + "%";
            side.line(2, Component.text(shorten(setpoint), NamedTextColor.WHITE));
            side.line(3, Component.text(shorten(footer + " | " + thermostat.group().label()),
                    eta.isPresent() && thermostat.mode() != OperatingMode.IDLE
                            ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        });
    }

    private void refreshThermometer(Thermometer thermometer, boolean force) {
        var location = thermometer.position().location();
        if (location == null) return;
        AirflowService.TemperatureReading reading = airflow.temperatureAt(location);
        String fingerprint = String.format(Locale.US, "%.1f|%.1f|%s", reading.localF(),
                reading.outdoorF(), thermometer.label());
        render(thermometer.position(), fingerprint, force, side -> {
            side.line(0, Component.text(shorten(thermometer.label()), NamedTextColor.AQUA));
            side.line(1, Component.text("Thermometer", NamedTextColor.WHITE));
            side.line(2, Component.text(String.format(Locale.US, "Inside: %.1f F", reading.localF()),
                    NamedTextColor.YELLOW));
            side.line(3, Component.text(String.format(Locale.US, "Outside: %.1f F", reading.outdoorF()),
                    NamedTextColor.GRAY));
        });
    }

    private void refreshRpmMonitor(RpmMonitor monitor, boolean force) {
        List<EquipmentUnit> matching = registry.equipmentFor(monitor.group()).stream()
                .filter(unit -> unit.type() == monitor.equipmentType()).toList();
        List<EquipmentUnit> running = matching.stream().filter(EquipmentUnit::running).toList();
        double rpm = running.stream().mapToDouble(EquipmentUnit::motorRpm).average().orElse(0.0);
        double capacity = running.stream().mapToDouble(unit -> unit.operatingCapacity(settings))
                .average().orElse(0.0);
        int percent = (int) Math.round(capacity * 100.0);
        String fingerprint = String.format(Locale.US, "%.0f|%d|%d|%d|%s", rpm, percent,
                running.size(), matching.size(), monitor.label());
        render(monitor.position(), fingerprint, force, side -> {
            side.line(0, Component.text(shorten(monitor.label()), NamedTextColor.AQUA));
            side.line(1, Component.text(shorten(monitor.equipmentType().displayName()), NamedTextColor.WHITE));
            if (matching.isEmpty()) {
                side.line(2, Component.text("No equipment", NamedTextColor.RED));
                side.line(3, Component.text(shorten(monitor.group().label()), NamedTextColor.GRAY));
            } else {
                side.line(2, Component.text(String.format(Locale.US, "RPM: %,.0f", rpm), NamedTextColor.YELLOW));
                String state = monitor.equipmentType() == EquipmentType.FURNACE
                        ? running.size() + "/" + matching.size() + " running"
                        : percent + "% | " + running.size() + "/" + matching.size();
                side.line(3, Component.text(shorten(state), running.isEmpty()
                        ? NamedTextColor.GRAY : NamedTextColor.GREEN));
            }
        });
    }

    public void refreshSettingsPanel(SettingsPanel panel, boolean force) {
        String fingerprint = panel.label() + "|" + panel.group().label() + "|" + panel.profile();
        NamedTextColor profileColor = switch (panel.profile()) {
            case ECO -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.AQUA;
            case TURBO -> NamedTextColor.GOLD;
        };
        render(panel.position(), fingerprint, force, side -> {
            side.line(0, Component.text(shorten("EZvac | " + panel.label()), NamedTextColor.AQUA));
            side.line(1, Component.text(shorten("System: " + panel.group().label()), NamedTextColor.WHITE));
            side.line(2, Component.text("[ " + panel.profile().name() + " ]", profileColor));
            side.line(3, Component.text("Click > | Sneak <", NamedTextColor.GRAY));
        });
    }

    private void render(BlockKey position, String fingerprint, boolean force,
                        java.util.function.Consumer<SignSide> writer) {
        if (!position.isChunkLoaded() || (!force && fingerprint.equals(fingerprints.get(position)))) return;
        var location = position.location();
        if (location == null || !(location.getBlock().getState() instanceof Sign sign)) return;
        try {
            writer.accept(sign.getSide(Side.FRONT));
            sign.update(false, false);
            fingerprints.put(position, fingerprint);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not refresh HVAC display at " + position, exception);
        }
    }

    private static String shorten(String value) {
        return value.length() <= 24 ? value : value.substring(0, 24);
    }

    private static String formatEta(OptionalLong eta) {
        if (eta.isEmpty()) return "--";
        long seconds = Math.max(0L, (eta.getAsLong() + 19L) / 20L);
        if (seconds == 0L) return "now";
        long minutes = seconds / 60L;
        if (minutes >= 100L) return "99m+";
        return String.format(Locale.US, "%d:%02d", minutes, seconds % 60L);
    }
}
