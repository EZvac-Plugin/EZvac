package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.ServerSettingsView;
import com.github.blade.hvac.api.SystemSize;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.WeatherStationView;
import com.github.blade.hvac.api.Wind;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.SystemId;
import com.github.blade.hvac.model.WeatherStation;
import com.github.blade.hvac.simulation.Forecast;
import com.github.blade.hvac.simulation.SizingModel;
import com.github.blade.hvac.simulation.WindModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** API 1.6: what EZvac BETA 1.0 added, for addons. */
class ApiBetaTest {
    private static final UUID WORLD = UUID.randomUUID();

    @Test
    void theApiIsVersion16() {
        assertEquals("1.6", EzvacApi.API_VERSION);
    }

    @Test
    void aWeatherStationShowsItsForecast() {
        WeatherStation station = new WeatherStation(new BlockKey(WORLD, 10, 64, 20), "Harbour");
        station.struck();
        Forecast.Day today = new Forecast.Day(0, 74.0, 55.0, 0.3, 0.1, Forecast.Sky.CLEAR,
                new WindModel.Day(812, WindModel.Level.BREEZY, 5.0), 60.0, "Rain from 15:00");
        WeatherStationView view = ApiViews.weatherStation(station, 70.4, List.of(today));
        assertEquals("Harbour", view.label());
        assertEquals(new Position(WORLD, 10, 64, 20), view.position());
        assertEquals(1, view.lightningStrikes());
        assertTrue(view.working());
        WeatherStationView.ForecastDay day = view.forecast().get(0);
        assertEquals(WeatherStationView.Sky.CLEAR, day.sky());
        assertEquals(74.0, day.highF());
        assertEquals(Optional.of(new Wind.Day(812, Wind.Level.BREEZY, 5.0)), day.wind());
        assertEquals("Rain from 15:00", day.note());
        assertFalse(ApiViews.weatherStation(station, Double.NaN, List.of()).working(), "not forecasting");
        Forecast.Day still = new Forecast.Day(1, 70, 50, 0, 0, Forecast.Sky.CLEAR, null, Double.NaN, "");
        assertEquals(Optional.empty(), ApiViews.weatherStation(station, 70, List.of(still)).forecast().get(0).wind(),
                "no wind with wind switched off");
    }

    @Test
    void aSystemsSizeSaysWhatItNeeds() {
        SystemId home = new SystemId(WORLD, "#14"), hall = new SystemId(WORLD, "#15");
        SizingModel.Assessment cooling = new SizingModel.Assessment(5580, false, 2, 3.0, 1.0, 1 / 3.0, 1.4);
        SizingModel.Assessment heating = new SizingModel.Assessment(5580, false, 2, 3.0, 3.0, 1.0, 1.4);
        SystemSize size = ApiViews.systemSize(home, cooling, heating, Map.of(hall, 12));
        assertEquals("#14", size.system());
        assertEquals(5580, size.size());
        assertEquals(1.4, size.leakFactor());
        assertTrue(size.undersized(ThermostatView.Mode.COOLING));
        assertFalse(size.undersized(ThermostatView.Mode.HEATING));
        assertEquals(Map.of("#15", 12), size.touching());
    }

    @Test
    void readingsGiveTheAirflowStrengthTheHudShows() {
        assertEquals(100, reading(1.0, true).airflowStrength());
        assertEquals(99, reading(0.996, true).airflowStrength(), "just short of the core never reads full");
        assertEquals(42, reading(0.42, true).airflowStrength());
        assertEquals(0, reading(0.0, false).airflowStrength());
    }

    @Test
    void oldViewsStillBuildAsBefore() {
        ThermostatView old = new ThermostatView("Home", "#1", new Position(WORLD, 0, 64, 0), 70, 72,
                ThermostatView.Mode.HEATING, true, false, 45, 1, 1);
        assertFalse(old.unitsOff(), "a 1.0-style view has no units switched off");
        ServerSettingsView settings = new ServerSettingsView(Set.of("weather-station"), true, false, true, true, true);
        assertTrue(settings.deviceOff("weather-station"));
        assertFalse(settings.deviceOff("heat-pump"));
    }

    private static ClimateReading reading(double influence, boolean conditioned) {
        return new ClimateReading(new Position(WORLD, 0, 64, 0), 70, 70, 45, 60, false, "Clear", Optional.empty(),
                conditioned ? Optional.of("Home") : Optional.empty(), conditioned ? Optional.of("#1") : Optional.empty(),
                influence, Optional.empty(), Wind.CALM);
    }
}
