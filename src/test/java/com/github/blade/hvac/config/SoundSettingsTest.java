package com.github.blade.hvac.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundSettingsTest {
    private static YamlConfiguration parse(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config;
    }

    @Test
    void theDefaultsAreTheSoundsEzvacAlwaysPlayedPlusTheVents() {
        SoundSettings defaults = SoundSettings.defaults();
        assertEquals(List.of(new SoundSettings.Cue("block.beacon.ambient", 1.6f, 0.55f)),
                defaults.get(SoundSettings.Event.EQUIPMENT_HUM));
        assertEquals(List.of(new SoundSettings.Cue("block.beacon.ambient", 0.64f, 0.88f)),
                defaults.get(SoundSettings.Event.DEHUMIDIFIER_HUM), "quieter and higher than a heat pump");
        assertEquals(3, defaults.get(SoundSettings.Event.FURNACE_START).size());
        assertEquals(List.of(new SoundSettings.Cue("block.conduit.ambient", 0.3f, 0.5f)),
                defaults.get(SoundSettings.Event.VENT_AIRFLOW));
        assertEquals(80, defaults.ventRefreshTicks());
        for (SoundSettings.Event event : SoundSettings.Event.values())
            assertFalse(defaults.get(event).isEmpty(), event.key());
    }

    @Test
    void linesAreReadAndBadOnesSkippedWithAWarning() throws Exception {
        List<String> warnings = new ArrayList<>();
        SoundSettings sounds = SoundSettings.load(parse("""
                audio:
                  sounds:
                    equipment-hum: ["block.portal.ambient 0.4 0.6", "nonsense", "block.beacon.ambient loud 1"]
                    vent-airflow: []
                    unit-start: "block.lever.click 0.3 1.0"
                    unit-stop: ["block.lever.click 0.3 3.0"]
                    furnace-stop: 7
                """), warnings::add);
        assertEquals(List.of(new SoundSettings.Cue("block.portal.ambient", 0.4f, 0.6f)),
                sounds.get(SoundSettings.Event.EQUIPMENT_HUM));
        assertTrue(sounds.get(SoundSettings.Event.VENT_AIRFLOW).isEmpty(), "[] turns an event off");
        assertEquals(List.of(new SoundSettings.Cue("block.lever.click", 0.3f, 1.0f)),
                sounds.get(SoundSettings.Event.UNIT_START), "a single line works too");
        assertEquals(2.0f, sounds.get(SoundSettings.Event.UNIT_STOP).get(0).pitch(), "pitch clamped to what the game plays");
        assertEquals(SoundSettings.defaults().get(SoundSettings.Event.FURNACE_STOP),
                sounds.get(SoundSettings.Event.FURNACE_STOP));
        assertEquals(SoundSettings.defaults().get(SoundSettings.Event.EQUIPMENT_START),
                sounds.get(SoundSettings.Event.EQUIPMENT_START), "anything not set keeps its default");
        assertEquals(4, warnings.size(), String.valueOf(warnings));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("audio.sounds.equipment-hum")));
    }

    @Test
    void aCueRoundTripsThroughItsLine() {
        SoundSettings.Cue cue = new SoundSettings.Cue("block.conduit.ambient", 0.3f, 0.5f);
        assertEquals("block.conduit.ambient 0.3 0.5", cue.line());
        assertEquals(cue, SoundSettings.Cue.parse(cue.line(), "test", warning -> fail(warning)));
        assertNull(SoundSettings.Cue.parse("block.conduit.ambient 0 1", "test", warning -> {}), "silent is []");
    }
}
