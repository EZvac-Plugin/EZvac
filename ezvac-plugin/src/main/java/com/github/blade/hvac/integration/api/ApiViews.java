package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.event.EquipmentStateChangeEvent;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.HvacController;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.Seasons;

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
    public static ThermostatView thermostat(Thermostat thermostat, HvacController.GroupStatus status) {
        double humidity = !thermostat.pool() && thermostat.hasRoomDewPoint()
                ? Psychrometrics.relativeHumidity(thermostat.roomF(), thermostat.roomDewPointF()) : Double.NaN;
        return new ThermostatView(thermostat.id(), thermostat.group().label(), position(thermostat.position()),
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
        boolean conditioned = reading.group() != null && reading.thermostat() != null;
        return new ClimateReading(at, reading.localF(), reading.feelsLikeF(), reading.humidity(), reading.outdoorF(),
                reading.water(), reading.weather(), season,
                conditioned ? Optional.of(reading.thermostat().id()) : Optional.empty(),
                conditioned ? Optional.of(reading.group().label()) : Optional.empty(),
                conditioned ? reading.influence() : 0.0);
    }
}
