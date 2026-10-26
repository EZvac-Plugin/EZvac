package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.EquipmentView;
import com.github.blade.hvac.api.InstrumentView;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.VentView;
import com.github.blade.hvac.api.event.EquipmentStateChangeEvent;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.ClimateVent;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.EquipmentUnit;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.RpmMonitor;
import com.github.blade.hvac.model.Sensor;
import com.github.blade.hvac.model.SensorHub;
import com.github.blade.hvac.model.SettingsPanel;
import com.github.blade.hvac.model.SystemId;
import com.github.blade.hvac.model.Thermometer;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.HvacController;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.Seasons;

import java.util.List;
import java.util.Optional;

/**
 * Turns EZvac's own objects into the public API's views (2.1). The
 * API never sees an internal type, so the internals can keep changing.
 */
public final class ApiViews {
    private ApiViews() {}

    public static Position position(BlockKey key) {
        return new Position(key.worldId(), key.x(), key.y(), key.z());
    }

    public static ThermostatView.Mode mode(OperatingMode mode) {
        return switch (mode) {
            case COOLING -> ThermostatView.Mode.COOLING;
            case HEATING -> ThermostatView.Mode.HEATING;
            case IDLE -> ThermostatView.Mode.IDLE;
        };
    }

    /** A thermostat as the API shows it; its unit counts come from the controller. */
    public static ThermostatView thermostat(Thermostat thermostat, HvacController.SystemStatus status) {
        double humidity = !thermostat.pool() && thermostat.hasRoomDewPoint()
                ? Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF()) : Double.NaN;
        return new ThermostatView(thermostat.id(), thermostat.system().key(), position(thermostat.position()),
                thermostat.roomF(), thermostat.targetF(), mode(thermostat.mode()), thermostat.enabled(),
                thermostat.pool(), humidity, status == null ? 0 : status.running(),
                status == null ? 0 : status.equipment());
    }

    public static ClimateUnitView climateUnit(ClimateUnit unit) {
        ClimateUnitView.Kind kind = unit.kind() == ClimateUnit.Kind.DEHUMIDIFIER
                ? ClimateUnitView.Kind.DEHUMIDIFIER : ClimateUnitView.Kind.HUMIDIFIER;
        ClimateUnitView.State state = switch (unit.state()) {
            case OFF -> ClimateUnitView.State.OFF;
            case IDLE -> ClimateUnitView.State.IDLE;
            case RUNNING -> ClimateUnitView.State.RUNNING;
            case EMPTYING -> ClimateUnitView.State.EMPTYING;
        };
        return new ClimateUnitView(unit.label(), kind, position(unit.position()), unit.lastHumidity(),
                unit.setpoint(), unit.powered(), state, unit instanceof Dehumidifier dry ? dry.tank() : 0.0);
    }

    public static SeasonView.Season season(Seasons.Season season) {
        return switch (season) {
            case SPRING -> SeasonView.Season.SPRING;
            case SUMMER -> SeasonView.Season.SUMMER;
            case AUTUMN -> SeasonView.Season.AUTUMN;
            case WINTER -> SeasonView.Season.WINTER;
        };
    }

    public static EquipmentStateChangeEvent.Kind equipment(EquipmentType type) {
        return switch (type) {
            case HEAT_PUMP -> EquipmentStateChangeEvent.Kind.HEAT_PUMP;
            case AIR_CONDITIONER -> EquipmentStateChangeEvent.Kind.AIR_CONDITIONER;
            case FURNACE -> EquipmentStateChangeEvent.Kind.FURNACE;
            case BOILER -> EquipmentStateChangeEvent.Kind.BOILER;
            case POOL_HEAT_PUMP -> EquipmentStateChangeEvent.Kind.POOL_HEAT_PUMP;
        };
    }

    /** A reading as the API shows it: the same numbers as the HUD and /hvac temperature. */
    public static ClimateReading reading(Position at, AirflowService.TemperatureReading reading,
                                         Optional<SeasonView.Season> season) {
        boolean conditioned = reading.system() != null && reading.thermostat() != null;
        return new ClimateReading(at, reading.localF(), reading.feelsLikeF(), reading.humidity(), reading.outdoorF(),
                reading.water(), reading.weather(), season,
                conditioned ? Optional.of(reading.thermostat().id()) : Optional.empty(),
                conditioned ? Optional.of(reading.system().key()) : Optional.empty(),
                conditioned ? reading.influence() : 0.0);
    }

    /** A piece of equipment as the API shows it (API 1.2). */
    public static EquipmentView equipment(EquipmentUnit unit) {
        ThermostatView.Mode mode = unit.running() && unit.operatingMode() != null
                ? mode(unit.operatingMode()) : ThermostatView.Mode.IDLE;
        return new EquipmentView(equipment(unit.type()), unit.system().key(), position(unit.position()),
                unit.enabled(), unit.running(), mode, unit.running() ? unit.motorRpm() : 0.0);
    }

    /** A vent; {@code water} when its system is a pool system. */
    public static VentView vent(ClimateVent vent, boolean water) {
        return new VentView(vent.system().key(), position(vent.position()),
                water ? VentView.Medium.WATER : VentView.Medium.AIR);
    }

    public static InstrumentView thermometer(Thermometer thermometer) {
        InstrumentView.Kind kind = switch (thermometer.reads()) {
            case TEMPERATURE -> InstrumentView.Kind.THERMOMETER;
            case HUMIDITY -> InstrumentView.Kind.HYGROMETER;
            case BOTH -> InstrumentView.Kind.THERMO_HYGROMETER;
        };
        return new InstrumentView(kind, thermometer.label(), position(thermometer.position()), Optional.empty(),
                List.of());
    }

    public static InstrumentView sensor(Sensor sensor) {
        InstrumentView.Kind kind = switch (sensor.reads()) {
            case TEMPERATURE -> InstrumentView.Kind.TEMPERATURE_SENSOR;
            case HUMIDITY -> InstrumentView.Kind.HUMIDITY_SENSOR;
            case BOTH -> InstrumentView.Kind.TEMPERATURE_HUMIDITY_SENSOR;
            case WEATHER -> InstrumentView.Kind.WEATHER_SENSOR;
        };
        return new InstrumentView(kind, sensor.label(), position(sensor.position()), Optional.empty(), List.of());
    }

    public static InstrumentView sensorHub(SensorHub hub) {
        return new InstrumentView(InstrumentView.Kind.SENSOR_HUB, hub.label(), position(hub.position()),
                Optional.empty(), hub.sensors().stream().map(ApiViews::position).toList());
    }

    /** A monitor links the equipment of its system (1.3); an unlinked one has no system. */
    public static InstrumentView rpmMonitor(RpmMonitor monitor, List<EquipmentUnit> shown) {
        return new InstrumentView(InstrumentView.Kind.RPM_MONITOR, monitor.label(), position(monitor.position()),
                key(monitor.system()), shown.stream().map(unit -> position(unit.position())).toList());
    }

    public static InstrumentView settingsPanel(SettingsPanel panel) {
        return new InstrumentView(InstrumentView.Kind.SETTINGS_PANEL, panel.label(), position(panel.position()),
                key(panel.system()), List.of());
    }

    private static Optional<String> key(SystemId system) {
        return system == null ? Optional.empty() : Optional.of(system.key());
    }
}
