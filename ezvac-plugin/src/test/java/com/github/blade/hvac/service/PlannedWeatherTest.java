package com.github.blade.hvac.service;

import com.github.blade.hvac.api.WeatherEvent;
import com.github.blade.hvac.api.WeatherPlan;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** An addon's weather plan, as EZvac's services read it (BETA 1.0 Pilot 4, API 1.9). */
class PlannedWeatherTest {
    private static <T> T stub(Class<T> type, String name) {
        return type.cast(Proxy.newProxyInstance(PlannedWeatherTest.class.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> name;
                    default -> null;
                }));
    }

    private static final World WORLD = stub(World.class, "world");

    private static WeatherPlan plan(double shift, WeatherEvent event) {
        return new WeatherPlan() {
            @Override
            public List<Outlook> outlook(World world, int days) {
                return List.of(new Outlook(0, 1.0, 0.2, shift, Optional.ofNullable(event), "Snowstorm from 14:00"));
            }

            @Override
            public double shiftNowF(World world) { return shift; }

            @Override
            public Optional<WeatherEvent> eventNow(World world) { return Optional.ofNullable(event); }
        };
    }

    @Test
    void withoutAPlanNothingChanges() {
        PlannedWeather planned = PlannedWeather.none();
        assertFalse(planned.active());
        assertEquals(0.0, planned.shiftNowF(WORLD));
        assertEquals(Optional.empty(), planned.eventNow(WORLD));
        assertEquals(List.of(), planned.outlook(WORLD, 7));
    }

    @Test
    void aPlanIsReadAndGoesWithItsPlugin() {
        PlannedWeather planned = PlannedWeather.none();
        Plugin climates = stub(Plugin.class, "EZclimates"), other = stub(Plugin.class, "Other");
        planned.set(climates, plan(-12.0, WeatherEvent.COLD_SNAP));
        assertTrue(planned.active());
        assertEquals(-12.0, planned.shiftNowF(WORLD));
        assertEquals(Optional.of(WeatherEvent.COLD_SNAP), planned.eventNow(WORLD));
        assertEquals("Snowstorm from 14:00", planned.outlook(WORLD, 7).get(0).note());
        planned.dropFrom(other);
        assertTrue(planned.active(), "another plugin disabling leaves it");
        planned.set(other, null);
        assertTrue(planned.active(), "and can't remove it");
        planned.dropFrom(climates);
        assertFalse(planned.active(), "its own plugin disabling drops it");
    }

    @Test
    void aFailingPlanIsNoPlanAndIsSaidOnce() {
        List<String> warnings = new ArrayList<>();
        PlannedWeather planned = new PlannedWeather(warnings::add);
        planned.set(stub(Plugin.class, "Broken"), new WeatherPlan() {
            @Override
            public List<Outlook> outlook(World world, int days) { throw new IllegalStateException("boom"); }

            @Override
            public double shiftNowF(World world) { throw new IllegalStateException("boom"); }

            @Override
            public Optional<WeatherEvent> eventNow(World world) { return null; }
        });
        assertEquals(0.0, planned.shiftNowF(WORLD));
        assertEquals(List.of(), planned.outlook(WORLD, 7));
        assertEquals(Optional.empty(), planned.eventNow(WORLD), "a null event is none");
        assertEquals(1, warnings.size(), String.valueOf(warnings));
        assertTrue(warnings.get(0).startsWith("Broken's weather plan failed"), warnings.get(0));
    }

    @Test
    void anOutlookKeepsItsNumbersSensible() {
        WeatherPlan.Outlook day = new WeatherPlan.Outlook(2, 1.4, 0.9, Double.NaN, null, null);
        assertEquals(1.0, day.rainChance());
        assertEquals(0.9, day.thunderChance());
        assertEquals(0.3, new WeatherPlan.Outlook(2, 0.3, 0.9, 0, Optional.empty(), "").thunderChance(),
                "no thunder without rain");
        assertEquals(0.0, day.shiftF());
        assertEquals(Optional.empty(), day.event());
        assertEquals("", day.note());
        assertEquals("Heat wave", WeatherEvent.HEAT_WAVE.displayName());
        assertEquals(100.0 - 100.0, PlannedWeather.none().shiftNowF(null));
    }
}
