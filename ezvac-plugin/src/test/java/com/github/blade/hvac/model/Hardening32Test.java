package com.github.blade.hvac.model;

import com.github.blade.hvac.config.AirQualitySettings;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.WindSettings;
import com.github.blade.hvac.simulation.AirQualityModel.Air;
import com.github.blade.hvac.simulation.EtaEstimator;
import com.github.blade.hvac.simulation.WindModel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 3.2 against hand-edited saves and config: nothing throws, and every bad value reads as something sane. */
class Hardening32Test {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private final HvacSettings settings = HvacSettings.defaults();

    @Test
    void aHandEditedPurifierLoadsSafely() {
        AirPurifier purifier = new AirPurifier(new BlockKey(WORLD, 1, 65, 0), new BlockKey(WORLD, 0, 65, 0),
                new BlockKey(WORLD, 0, 64, 0), "air", AirPurifier.Mode.HIGH);
        Map<String, Object> map = purifier.toMap();
        map.put("mode", "TURBO");
        map.put("filterLife", "full");
        map.put("zoneFine", "lots");
        map.put("zoneCoarse", -40.0);
        map.put("zonePollen", 1e12);
        AirPurifier loaded = AirPurifier.fromMap(map);
        assertEquals(AirPurifier.Mode.AUTO, loaded.mode(), "an unknown mode reads as Auto");
        assertEquals(1.0, loaded.filter(), "an unreadable filter reads as fresh");
        assertFalse(loaded.zoneAir().known(), "a broken zone air starts again at the outdoor air");
        map.put("zoneFine", 5.0);
        Air air = AirPurifier.fromMap(map).zoneAir();
        assertEquals(new Air(5.0, 0.0, 5_000.0), air, "negative and huge values are clamped");
        map.put("filterLife", -3.0);
        assertEquals(ClimateUnit.State.FILTER_SPENT, AirPurifier.fromMap(map).state());
        map.put("filterLife", 7.0);
        assertEquals(1.0, AirPurifier.fromMap(map).filter());
        map.put("filterLife", Double.NaN);
        assertEquals(1.0, AirPurifier.fromMap(map).filter());
    }

    @Test
    void aHandEditedThermostatLoadsSafely() {
        Thermostat thermostat = new Thermostat(new BlockKey(WORLD, 10, 64, -20), "Main", "main", 72, 70, settings);
        Map<String, Object> map = thermostat.toMap();
        for (Object wind : new Object[]{"gusty", -1.0, 99.0, Double.POSITIVE_INFINITY}) {
            map.put("outdoorWind", wind);
            assertEquals(1.0, Thermostat.fromMap(map, settings).outdoorClimate().windExposure(),
                    "a bad exposure (" + wind + ") reads as open ground until it is sampled");
        }
        map.put("outdoorWind", 0.5);
        Thermostat sheltered = Thermostat.fromMap(map, settings);
        assertEquals(0.5, sheltered.outdoorClimate().windExposure());
        assertEquals(0.5, sheltered.toMap().get("outdoorWind"), "it saves back as it was");
        map.put("roomFine", 1e9);
        map.put("roomCoarse", 3.0);
        map.put("roomPollen", 4.0);
        assertEquals(5_000.0, Thermostat.fromMap(map, settings).roomAir().fine());
    }

    @Test
    void aWindSensorRoundTripsAndAnUnknownKindIsRefused() {
        Sensor sensor = new Sensor(new BlockKey(WORLD, 3, 70, 3), "Roof", Sensor.Kind.WIND);
        assertEquals(sensor, Sensor.fromMap(sensor.toMap()));
        Map<String, Object> map = sensor.toMap();
        map.put("reads", "TIDE");
        assertThrows(IllegalArgumentException.class, () -> Sensor.fromMap(map),
                "the registry keeps such a record as it is, as 3.1 does with a wind sensor");
    }

    @Test
    void badConfigValuesFallBackWithWarnings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("wind", "breezy");                         // a whole section replaced by a word
        config.set("air-quality.settling", 3);
        config.set("air-quality.purifier.speeds.low", "slow");
        config.set("air-quality.purifier.auto.high-above-aqi", 10);  // below medium
        List<String> warnings = new ArrayList<>();
        ClimateSettings climate = ClimateSettings.load(config, warnings::add);
        assertEquals(WindSettings.defaults(), climate.wind());
        assertEquals(AirQualitySettings.defaults().settling(), climate.airQuality().settling());
        assertEquals(AirQualitySettings.defaults().purifier(), climate.airQuality().purifier());
        assertFalse(warnings.isEmpty());

        config = new YamlConfiguration();
        config.set("wind.speeds-ms.gale", "fast");
        config.set("wind.units", 5);
        config.set("wind.maximum-infiltration", 0.5);
        config.set("wind.biomes.minecraft:plains", -2);
        warnings.clear();
        WindSettings wind = WindSettings.load(config, warnings::add);
        assertEquals(WindSettings.defaults().speeds(), wind.speeds());
        assertEquals(WindSettings.Units.AUTO, wind.units());
        assertEquals(2.0, wind.maximumInfiltration());
        assertEquals(1.15, wind.exposure("minecraft:plains"));
        assertEquals(4, warnings.size(), warnings.toString());
    }

    @Test
    void theEtaKnowsAWindyDayMakesHeatingSlower() {
        List<EtaEstimator.UnitSnapshot> furnace = List.of(new EtaEstimator.UnitSnapshot(EquipmentType.FURNACE, 0.0,
                Tier.TWO_STAGE));
        OptionalLong calm = EtaEstimator.estimateTicks(60, 70, 20, OperatingMode.HEATING, furnace,
                OperatingProfile.NORMAL, settings);
        OptionalLong gale = EtaEstimator.estimateTicks(60, 70, 20, OperatingMode.HEATING, furnace,
                OperatingProfile.NORMAL, settings, 2.0);
        assertTrue(calm.isPresent() && gale.isPresent());
        assertTrue(gale.getAsLong() > calm.getAsLong(), calm + " calm, " + gale + " in a gale");
        assertEquals(calm, EtaEstimator.estimateTicks(60, 70, 20, OperatingMode.HEATING, furnace,
                OperatingProfile.NORMAL, settings, 1.0));
    }

    @Test
    void theWindNeverGoesWildFromBadInputs() {
        WindSettings s = WindSettings.defaults();
        WindModel.Day day = new WindModel.Day(1, WindModel.Level.GALE, 18.0);
        for (long time : new long[]{Long.MIN_VALUE / 2, -1, 0, Long.MAX_VALUE / 2})
            assertTrue(Double.isFinite(WindModel.speedMs(1L, time, day, null, s)), "at " + time);
        assertEquals(WindModel.Level.CALM, WindModel.Level.of(Double.NaN, s.speeds()));
        assertEquals(1.0, WindModel.exposure(Double.NaN, 70, 63, Double.NaN), 0.1);
        assertEquals(1.0, s.infiltrationFactor(Double.NaN));
    }
}
