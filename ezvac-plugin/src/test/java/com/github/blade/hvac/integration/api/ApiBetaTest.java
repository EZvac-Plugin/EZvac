package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.EzvacApi;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.SeasonView;
import com.github.blade.hvac.api.ServerSettingsView;
import com.github.blade.hvac.api.SystemSize;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.WeatherStationView;
import com.github.blade.hvac.api.Wind;
import com.github.blade.hvac.model.Attention;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.OperatingProfile;
import com.github.blade.hvac.model.Thermostat;
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

/** API 1.6 to 1.9: what EZvac BETA 1.0 and its pilots added, for addons. */
class ApiBetaTest {
    private static final UUID WORLD = UUID.randomUUID();

    @Test
    void theApiIsVersion19() {
        assertEquals("1.10", EzvacApi.API_VERSION);
    }

    @Test
    void aSeasonSaysHowFarThroughTheYearItIs() {
        // API 1.9 (EZvac BETA 1.0 Pilot 4), for EZclimates' colour stages.
        assertEquals(0.5 / 120, new SeasonView(SeasonView.Season.SPRING, 1, 30, false).progress(), 1e-12);
        assertEquals(0.5, new SeasonView(SeasonView.Season.AUTUMN, 1, 30, false).progress(), 0.01);
        assertEquals(119.5 / 120, new SeasonView(SeasonView.Season.WINTER, 30, 30, false).progress(), 1e-12);
        assertEquals(new SeasonView(SeasonView.Season.SUMMER, 8, 15, false).progress(),
                new SeasonView(SeasonView.Season.SUMMER, 16, 30, false).progress(), 0.02,
                "about the same point of the year, whatever the length");
    }

    @Test
    void anOlderAddonsApiSaysNothingFalls() {
        // API 1.9: what an implementation without it answers.
        EzvacApi old = (EzvacApi) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{EzvacApi.class}, (proxy, method, args) -> method.isDefault()
                        ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        assertEquals(com.github.blade.hvac.api.Precipitation.NONE, old.precipitationAt(null, 0, 64, 0));
        assertTrue(Double.isNaN(old.snowLineAt(null, 0, 0)));
        assertTrue(Double.isNaN(old.freezingWaterF(null, 0, 64, 0)));
    }

    @Test
    void theWindHasADirection() {
        // API 1.8 (EZvac BETA 1.0 Pilot 3).
        Wind wind = new Wind(10.0, Wind.Level.WINDY, 300.0);
        assertEquals("NW", wind.compass());
        assertEquals(120.0, wind.towardDegrees(), 1e-9, "an arrow points where it blows to");
        assertTrue(wind.hasDirection());
        Wind old = new Wind(10.0, Wind.Level.WINDY);
        assertFalse(old.hasDirection(), "the 1.5 constructor has none");
        assertEquals("", old.compass());
        assertTrue(Double.isNaN(old.towardDegrees()));
        assertEquals(10.0, new Wind(1, Wind.Level.CALM, 370.0).fromDegrees(), 1e-9);
        assertEquals("SW", new Wind.Day(5, Wind.Level.GALE, 18.0, 225.0).compass());
        assertEquals("", new Wind.Day(5, Wind.Level.GALE, 18.0).compass());
        assertEquals(Wind.CALM, ApiViews.wind(0.0, 270.0, com.github.blade.hvac.config.WindSettings.defaults()));
    }

    @Test
    void aWeatherStationSaysItsWindAndAir() {
        // API 1.8 (EZvac BETA 1.0 Pilot 3): MapPeek draws the arrow and the AQI.
        WeatherStation station = new WeatherStation(new BlockKey(WORLD, 10, 64, 20), "Harbour");
        Forecast.Day today = new Forecast.Day(0, 74.0, 55.0, 0.3, 0.1, Forecast.Sky.CLEAR,
                new WindModel.Day(812, WindModel.Level.WINDY, 9.0).from(225.0), 60.0, "").withAqi(42);
        var air = ApiViews.airQuality(new com.github.blade.hvac.simulation.AirQualityModel.Air(10.0, 20.0, 50.0),
                com.github.blade.hvac.config.AirQualitySettings.defaults());
        WeatherStationView view = ApiViews.weatherStation(station, 70.4, List.of(today), 100, 20,
                new Wind(9.5, Wind.Level.WINDY, 230.0), air);
        assertEquals("SW", view.wind().compass());
        assertTrue(view.airQuality().isPresent());
        WeatherStationView.ForecastDay day = view.forecast().get(0);
        assertEquals(42, day.aqi());
        assertEquals(225.0, day.wind().orElseThrow().fromDegrees(), 1e-9);
        WeatherStationView older = new WeatherStationView("Old", new Position(WORLD, 0, 64, 0), true, true, 0,
                70.0, List.of(), 100, 20);
        assertEquals(Wind.CALM, older.wind(), "the 1.7 constructor has no wind...");
        assertEquals(Optional.empty(), older.airQuality(), "...and no air");
        assertEquals(-1, new WeatherStationView.ForecastDay(0, 70, 50, WeatherStationView.Sky.CLEAR, 0, 0,
                Optional.empty(), Double.NaN, "").aqi(), "a 1.6 day has no AQI");
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
        assertEquals(Optional.of(new Wind.Day(812, Wind.Level.BREEZY, 5.0, WindModel.WESTERLIES_FROM)), day.wind(),
                "since 1.8 with where it blows from");
        assertEquals("Rain from 15:00", day.note());
        assertFalse(ApiViews.weatherStation(station, Double.NaN, List.of()).working(), "not forecasting");
        Forecast.Day still = new Forecast.Day(1, 70, 50, 0, 0, Forecast.Sky.CLEAR, null, Double.NaN, "");
        assertEquals(Optional.empty(), ApiViews.weatherStation(station, 70, List.of(still)).forecast().get(0).wind(),
                "no wind with wind switched off");
    }

    @Test
    void aWeatherStationSaysHowFarItMeasuresForAMapToDraw() {
        // API 1.7: MapPeek draws this as the station's coverage.
        WeatherStation station = new WeatherStation(new BlockKey(WORLD, 10, 64, 20), "Harbour");
        WeatherStationView view = ApiViews.weatherStation(station, 70.4, List.of(), 150, 30);
        assertEquals(150, view.areaRadius());
        assertEquals(30, view.areaHeight());
        WeatherStationView old = new WeatherStationView("Old", new Position(WORLD, 0, 64, 0), true, true, 0,
                70.0, List.of());
        assertEquals(100, old.areaRadius(), "the 1.6 constructor gives the default area");
    }

    @Test
    void aThermostatSaysItsProfileWhetherItIsMiniAndWhatNeedsAttention() {
        // API 1.7 (EZvac BETA 1.0 Pilot 2).
        Thermostat thermostat = new Thermostat(new BlockKey(WORLD, 0, 64, 0), "Hall", new SystemId(WORLD, "#3"),
                72, 70, com.github.blade.hvac.config.HvacSettings.defaults(), false);
        thermostat.setMini(true);
        thermostat.setAttention(java.util.EnumSet.of(Attention.NO_VENTS, Attention.UNDERSIZED));
        ThermostatView view = ApiViews.thermostat(thermostat, null, null, OperatingProfile.TURBO);
        assertEquals(ThermostatView.Profile.TURBO, view.profile());
        assertTrue(view.mini());
        assertEquals(List.of(ThermostatView.Attention.NO_VENTS, ThermostatView.Attention.UNDERSIZED), view.attention());
        assertTrue(view.blocked(), "no vent keeps it from starting");
        ThermostatView old = new ThermostatView("Old", "#1", new Position(WORLD, 0, 64, 0), 70, 72,
                ThermostatView.Mode.IDLE, true, false, Double.NaN, 0, 0, Optional.empty(), false);
        assertEquals(ThermostatView.Profile.NORMAL, old.profile());
        assertEquals(List.of(), old.attention());
        assertFalse(old.mini() || old.blocked(), "the 1.6 constructor: nothing new set");
        for (Attention each : Attention.values()) ThermostatView.Attention.valueOf(each.name());
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
