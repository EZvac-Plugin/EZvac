package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.TemperatureUnit;
import com.github.blade.hvac.api.Temperatures;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.GroupId;
import com.github.blade.hvac.model.Humidifier;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.AirflowService;
import com.github.blade.hvac.service.HvacController;
import com.github.blade.hvac.model.OperatingProfile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The public API's views of EZvac's own objects (2.1). */
class ApiViewsTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private final HvacSettings settings = HvacSettings.defaults();

    private Thermostat thermostat() {
        Thermostat thermostat = new Thermostat(new BlockKey(WORLD, 4, 64, -2), "main", "house", 70.0, 74.5, settings);
        thermostat.setControlState(OperatingMode.COOLING, 100L);
        thermostat.setRoomDewPointF(55.0);
        return thermostat;
    }

    @Test
    void aThermostatViewCarriesItsStateAndItsSystemsUnits() {
        Thermostat thermostat = thermostat();
        HvacController.GroupStatus status = new HvacController.GroupStatus(thermostat.group(), OperatingMode.COOLING,
                74.5, 70.0, 3, 2, 1.0, OperatingProfile.NORMAL, true);
        ThermostatView view = ApiViews.thermostat(thermostat, status);
        assertEquals("main", view.id());
        assertEquals("house", view.system());
        assertEquals(new Position(WORLD, 4, 64, -2), view.position());
        assertEquals(74.5, view.roomF());
        assertEquals(70.0, view.targetF());
        assertEquals(ThermostatView.Mode.COOLING, view.mode());
        assertTrue(view.enabled());
        assertFalse(view.pool());
        assertTrue(view.humidity() > 0 && view.humidity() < 100, "the room's humidity from its dew point");
        assertEquals(2, view.runningUnits());
        assertEquals(3, view.totalUnits());
        assertEquals(Temperatures.celsius(70.0), view.targetC(), 1e-12);
        assertEquals(0, ApiViews.thermostat(thermostat, null).totalUnits(), "no status: no units known");
    }

    @Test
    void aPoolHasNoAirHumidity() {
        Thermostat pool = new Thermostat(new BlockKey(WORLD, 0, 64, 0), "spa", "pool", 82.0, 80.0, settings, true);
        assertTrue(Double.isNaN(ApiViews.thermostat(pool, null).humidity()));
        assertTrue(ApiViews.thermostat(pool, null).pool());
    }

    @Test
    void aHumidifierView() {
        Humidifier humidifier = new Humidifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 66, 0), "nursery", 40);
        ClimateUnitView view = ApiViews.climateUnit(humidifier);
        assertEquals("nursery", view.label());
        assertEquals(ClimateUnitView.Kind.HUMIDIFIER, view.kind());
        assertEquals(new Position(WORLD, 1, 65, 0), view.position());
        assertEquals(40, view.setpoint());
        assertEquals(0.0, view.tank(), "plumbed: no tank");
    }

    @Test
    void aReadingNamesTheSystemOnlyWhereOneConditionsTheSpot() {
        Thermostat thermostat = thermostat();
        Position at = new Position(WORLD, 5, 64, 0);
        AirflowService.TemperatureReading inside = new AirflowService.TemperatureReading(71.0, 90.0, 0.8, 6,
                GroupId.of(thermostat.position(), "house"), thermostat, false, 45.0, 70.4, "Clear");
        ClimateReading reading = ApiViews.reading(at, inside, Optional.of(SeasonView.Season.SUMMER));
        assertEquals(71.0, reading.temperatureF());
        assertEquals(70.4, reading.feelsLikeF());
        assertEquals(90.0, reading.outdoorF());
        assertEquals(Optional.of("main"), reading.thermostat());
        assertEquals(Optional.of("house"), reading.system());
        assertEquals(0.8, reading.influence());
        assertTrue(reading.conditioned());
        assertEquals(Optional.of(SeasonView.Season.SUMMER), reading.season());

        AirflowService.TemperatureReading outside = new AirflowService.TemperatureReading(90.0, 90.0, 0.0, -1,
                null, null, false, 30.0, 92.0, "Clear");
        ClimateReading open = ApiViews.reading(at, outside, Optional.empty());
        assertFalse(open.conditioned());
        assertEquals(Optional.empty(), open.thermostat());
        assertEquals(0.0, open.influence());
    }

    @Test
    void recordsNeverHoldNulls() {
        ClimateReading reading = new ClimateReading(new Position(WORLD, 0, 0, 0), 70, 70, 50, 70, false, null, null,
                null, null, 0);
        assertEquals("", reading.weather());
        assertEquals(Optional.empty(), reading.season());
        assertEquals(Optional.empty(), reading.thermostat());
        assertThrows(NullPointerException.class, () -> new Position(null, 0, 0, 0));
    }

    @Test
    void temperaturesConvert() {
        assertEquals(22.0, Temperatures.celsius(71.6), 1e-9);
        assertEquals(71.6, Temperatures.fahrenheit(22.0), 1e-9);
        assertEquals(22.0, TemperatureUnit.C.fromFahrenheit(71.6), 1e-9);
        assertEquals(71.6, TemperatureUnit.F.fromFahrenheit(71.6));
    }

    @Test
    void eventsAreNotBuiltWithoutListeners() {
        // No server here: firing would fail, so these must return before building anything.
        Thermostat thermostat = thermostat();
        assertDoesNotThrow(() -> ApiEvents.modeChanged(thermostat, OperatingMode.IDLE, null));
        assertDoesNotThrow(() -> ApiEvents.targetChanged(thermostat, 65.0,
                com.github.blade.hvac.api.event.ThermostatTargetChangeEvent.Cause.MENU, null));
    }
}
