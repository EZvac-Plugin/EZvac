package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.HvacDevice;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.control.ControlResult;
import com.github.blade.hvac.api.control.ControlSource;
import com.github.blade.hvac.api.event.DeviceControlEvent;
import com.github.blade.hvac.api.event.ThermostatTargetChangeEvent;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.AirPurifier;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.Freezer;
import com.github.blade.hvac.model.FridgeFreezer;
import com.github.blade.hvac.model.OperatingProfile;
import com.github.blade.hvac.model.Refrigerator;
import com.github.blade.hvac.model.SettingsPanel;
import com.github.blade.hvac.model.SystemId;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every change made to a device through the API or by a button on its screen
 * (BETA 1.0 Pilot 7, API 1.12), in one place: it finds the device, lets
 * plugins cancel with {@link DeviceControlEvent}, makes the change within the
 * device's range, saves, redraws the device's screen, and notes who changed
 * it for {@code /hvac info}. It checks no permission: that's the caller's
 * job (a screen button checks {@code ezvac.use}; a plugin its own users).
 * Main thread only.
 */
public final class DeviceControls {
    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final DisplayService displays;
    private final HvacSettings settings;
    private final ClimateSettings climate;
    private final Function<BlockKey, Optional<HvacDevice>> views;
    /** Who last changed each device this way, and to what: shown by /hvac info; never saved. */
    private final Map<BlockKey, String> lastChange = new HashMap<>();

    public DeviceControls(HvacPlugin plugin, HvacRegistry registry, DisplayService displays, HvacSettings settings,
                          ClimateSettings climate, Function<BlockKey, Optional<HvacDevice>> views) {
        this.plugin = plugin;
        this.registry = registry;
        this.displays = displays;
        this.settings = settings;
        this.climate = climate;
        this.views = views;
    }

    /** Who last changed a device through the API or its screen, e.g. "WebPanel for Steve: target 70.0 F". */
    public Optional<String> lastChange(BlockKey position) {
        return Optional.ofNullable(lastChange.get(position));
    }

    // ---- The changes ----

    public ControlResult setTarget(BlockKey key, double targetF, ControlSource source) {
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat == null) return missing("thermostat");
        if (!Double.isFinite(targetF)) return new ControlResult(ControlResult.Status.NOT_SUPPORTED,
                "That isn't a temperature.", view(key));
        return change(key, DeviceControlEvent.Setting.TARGET, targetF, source, () -> {
            double target = settings.clampTarget(targetF, thermostat.pool());
            double previous = thermostat.targetF();
            if (Math.abs(target - previous) < 1e-9) return ControlResult.Status.UNCHANGED;
            thermostat.setTargetF(target, settings);
            ApiEvents.targetChanged(thermostat, previous, source.origin() == ControlSource.Origin.SCREEN
                    ? ThermostatTargetChangeEvent.Cause.SCREEN : ThermostatTargetChangeEvent.Cause.API, plugin.controller());
            displays.refreshThermostat(thermostat, true);
            return Math.abs(target - targetF) < 1e-9 ? ControlResult.Status.DONE : ControlResult.Status.ADJUSTED;
        }, String.format(java.util.Locale.US, "target %.1f F", targetF));
    }

    public ControlResult setEnabled(BlockKey key, boolean enabled, ControlSource source) {
        Thermostat thermostat = registry.thermostatAt(key);
        if (thermostat == null) return missing("thermostat");
        return change(key, DeviceControlEvent.Setting.ENABLED, enabled, source, () -> {
            if (thermostat.enabled() == enabled) return ControlResult.Status.UNCHANGED;
            thermostat.setEnabled(enabled);
            displays.refreshThermostat(thermostat, true);
            return ControlResult.Status.DONE;
        }, enabled ? "switched on" : "switched off");
    }

    public ControlResult setProfile(BlockKey key, ThermostatView.Profile profile, ControlSource source) {
        Thermostat thermostat = registry.thermostatAt(key);
        SettingsPanel panel = thermostat == null ? registry.settingsPanelAt(key) : null;
        SystemId system = thermostat != null ? thermostat.system() : panel != null ? panel.system() : null;
        if (system == null) return missing("thermostat or linked settings panel");
        Thermostat head = registry.thermostatFor(system);
        BlockKey shown = head != null ? head.position() : key;
        OperatingProfile wanted = OperatingProfile.valueOf(profile.name());
        return change(shown, DeviceControlEvent.Setting.PROFILE, profile, source, () -> {
            if (registry.profileFor(system) == wanted) return ControlResult.Status.UNCHANGED;
            registry.setProfile(system, wanted);
            SettingsPanel linked = registry.settingsPanelFor(system);
            if (linked != null) displays.refreshSettingsPanel(linked, true);
            if (head != null) displays.refreshThermostat(head, true);
            return ControlResult.Status.DONE;
        }, "profile " + profile.name().toLowerCase(java.util.Locale.ROOT));
    }

    public ControlResult setPowered(BlockKey key, boolean powered, ControlSource source) {
        ClimateUnit unit = registry.climateUnitAt(key);
        if (unit == null) return missing("climate unit");
        return change(unit.position(), DeviceControlEvent.Setting.POWER, powered, source, () -> {
            if (unit.powered() == powered) return ControlResult.Status.UNCHANGED;
            unit.setPowered(powered);
            displays.refreshClimateUnit(unit, true);
            return ControlResult.Status.DONE;
        }, powered ? "switched on" : "switched off");
    }

    public ControlResult setSetpoint(BlockKey key, int setpoint, ControlSource source) {
        ClimateUnit unit = registry.climateUnitAt(key);
        if (unit == null) return missing("climate unit");
        if (unit instanceof AirPurifier) return unsupported(unit, "An air purifier has fan speeds, not a setpoint.");
        return change(unit.position(), DeviceControlEvent.Setting.SETPOINT, setpoint, source, () -> {
            int wanted = setpoint;
            if (!(unit instanceof Refrigerator)) wanted = nearest(setpoint, unit instanceof Dehumidifier
                    ? climate.dehumidifier().presets() : climate.humidifier().presets());
            int before = unit.setpoint();
            unit.setSetpoint(wanted);
            if (unit.setpoint() == before) return ControlResult.Status.UNCHANGED;
            displays.refreshClimateUnit(unit, true);
            return unit.setpoint() == setpoint ? ControlResult.Status.DONE : ControlResult.Status.ADJUSTED;
        }, "setpoint " + setpoint);
    }

    public ControlResult setFridgeSetpoint(BlockKey key, int setpointF, ControlSource source) {
        if (!(registry.climateUnitAt(key) instanceof FridgeFreezer both)) return missing("refrigerator");
        return change(both.position(), DeviceControlEvent.Setting.FRIDGE_SETPOINT, setpointF, source, () -> {
            int before = both.fridgeSetpoint();
            both.setFridgeSetpoint(setpointF);
            if (both.fridgeSetpoint() == before) return ControlResult.Status.UNCHANGED;
            displays.refreshClimateUnit(both, true);
            return both.fridgeSetpoint() == setpointF ? ControlResult.Status.DONE : ControlResult.Status.ADJUSTED;
        }, "fridge setpoint " + setpointF + " F");
    }

    public ControlResult setFan(BlockKey key, ClimateUnitView.Fan fan, ControlSource source) {
        ClimateUnit unit = registry.climateUnitAt(key);
        if (unit == null) return missing("air purifier");
        if (!(unit instanceof AirPurifier purifier)) return unsupported(unit, "Only an air purifier has a fan to set.");
        AirPurifier.Mode mode = AirPurifier.Mode.valueOf(fan.name());
        return change(unit.position(), DeviceControlEvent.Setting.FAN, fan, source, () -> {
            if (purifier.mode() == mode) return ControlResult.Status.UNCHANGED;
            purifier.setMode(mode);
            displays.refreshClimateUnit(purifier, true);
            return ControlResult.Status.DONE;
        }, "fan " + fan.name().toLowerCase(java.util.Locale.ROOT));
    }

    public ControlResult startDefrost(BlockKey key, ControlSource source) {
        ClimateUnit unit = registry.climateUnitAt(key);
        if (unit == null) return missing("freezer");
        if (!(unit instanceof Freezer freezer)) return unsupported(unit, "Only a freezer frosts up.");
        return change(unit.position(), DeviceControlEvent.Setting.DEFROST, Boolean.TRUE, source, () -> {
            if (freezer.defrosting()) return ControlResult.Status.UNCHANGED;
            freezer.startDefrost(climate.refrigeration());
            displays.refreshClimateUnit(freezer, true);
            return ControlResult.Status.DONE;
        }, "defrost");
    }

    public ControlResult setAutoDefrost(BlockKey key, boolean on, ControlSource source) {
        ClimateUnit unit = registry.climateUnitAt(key);
        if (unit == null) return missing("refrigerator");
        if (!(unit instanceof Freezer freezer) || !freezer.hasAutoDefrost())
            return unsupported(unit, "Only a refrigerator's freezer defrosts itself.");
        return change(unit.position(), DeviceControlEvent.Setting.AUTO_DEFROST, on, source, () -> {
            if (freezer.autoDefrost() == on) return ControlResult.Status.UNCHANGED;
            freezer.setAutoDefrost(on);
            displays.refreshClimateUnit(freezer, true);
            return ControlResult.Status.DONE;
        }, on ? "auto defrost on" : "auto defrost off");
    }

    public ControlResult setEquipmentEnabled(BlockKey key, boolean enabled, ControlSource source) {
        EquipmentUnit unit = registry.equipmentAt(key);
        if (unit == null) return missing("piece of equipment");
        return change(key, DeviceControlEvent.Setting.EQUIPMENT_ENABLED, enabled, source, () -> {
            if (unit.enabled() == enabled) return ControlResult.Status.UNCHANGED;
            unit.setEnabled(enabled);
            displays.refreshAll(false);
            return ControlResult.Status.DONE;
        }, enabled ? "switched on" : "switched off");
    }

    // ---- How every change goes ----

    private ControlResult change(BlockKey key, DeviceControlEvent.Setting setting, Object value, ControlSource source,
                                 Supplier<ControlResult.Status> apply, String what) {
        Optional<HvacDevice> before = view(key);
        if (before.isPresent()) {
            DeviceControlEvent event = new DeviceControlEvent(before.get(), setting, value, source);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled())
                return new ControlResult(ControlResult.Status.CANCELLED, "Another plugin stopped that change.", before);
        }
        ControlResult.Status status = apply.get();
        if (status == ControlResult.Status.DONE || status == ControlResult.Status.ADJUSTED) {
            registry.markDirty();
            plugin.requestImmediateSync();
            lastChange.put(key, describe(source) + ": " + what);
        }
        return new ControlResult(status, message(status), view(key));
    }

    private Optional<HvacDevice> view(BlockKey key) {
        try {
            return views.apply(key);
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private static String message(ControlResult.Status status) {
        return switch (status) {
            case DONE -> "Done.";
            case ADJUSTED -> "Done, kept within its range.";
            case UNCHANGED -> "It already was.";
            case NOT_FOUND -> "There's no such device there.";
            case NOT_SUPPORTED -> "That device can't do that.";
            case CANCELLED -> "Another plugin stopped that change.";
        };
    }

    private static ControlResult missing(String what) {
        return new ControlResult(ControlResult.Status.NOT_FOUND, "There's no " + what + " there.", Optional.empty());
    }

    private ControlResult unsupported(ClimateUnit unit, String message) {
        return new ControlResult(ControlResult.Status.NOT_SUPPORTED, message, view(unit.position()));
    }

    /** "WebPanel for Steve", "WebPanel", or "Steve at its screen". */
    private static String describe(ControlSource source) {
        String player = source.player().map(id -> {
            var known = Bukkit.getOfflinePlayer(id).getName();
            return known == null ? id.toString() : known;
        }).orElse("");
        if (source.origin() == ControlSource.Origin.SCREEN) return (player.isEmpty() ? "Someone" : player) + " at its screen";
        String by = source.plugin().orElse("A plugin");
        return player.isEmpty() ? by : by + " for " + player;
    }

    /** The preset nearest a setpoint; the lower on a tie. */
    static int nearest(int setpoint, List<Integer> presets) {
        int best = presets.get(0);
        for (int preset : presets) if (Math.abs(preset - setpoint) < Math.abs(best - setpoint)) best = preset;
        return best;
    }
}
