package com.github.blade.hvac.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Every sound EZvac plays, from config.yml's {@code audio.sounds} (2.0
 * Pilot 5). Each sound is one line, {@code "sound volume pitch"}; an event
 * plays all of its lines together, and an empty list silences it. Sounds are
 * named by key, so a resource pack's own sounds work too.
 */
public record SoundSettings(Map<SoundSettings.Event, List<SoundSettings.Cue>> sounds, long ventRefreshTicks) {
    /** The game plays nothing outside this pitch range: it clamps to it. */
    public static final float MINIMUM_PITCH = 0.5f;
    public static final float MAXIMUM_PITCH = 2.0f;

    public enum Event {
        EQUIPMENT_HUM, FURNACE_START, FURNACE_STOP, EQUIPMENT_START, EQUIPMENT_STOP,
        UNIT_START, UNIT_STOP, DEHUMIDIFIER_START, DEHUMIDIFIER_HUM, DEHUMIDIFIER_EMPTYING,
        HUMIDIFIER_BUBBLES, VENT_AIRFLOW;

        /** Its key under {@code audio.sounds}, e.g. {@code equipment-hum}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** One sound: a namespaced key such as {@code block.beacon.ambient}, its volume and pitch. */
    public record Cue(String sound, float volume, float pitch) {
        public Cue {
            if (sound == null || sound.isBlank()) throw new IllegalArgumentException("sound key is blank");
            if (!Float.isFinite(volume) || volume <= 0f) throw new IllegalArgumentException("volume must be above 0");
            if (!Float.isFinite(pitch)) throw new IllegalArgumentException("pitch must be a number");
        }

        /**
         * Reads {@code "sound volume pitch"}. A pitch the game can't play is
         * clamped to what it can, with a warning.
         *
         * @return the cue, or null (with a warning) when the line can't be read
         */
        public static Cue parse(String line, String where, Consumer<String> warn) {
            String[] parts = line == null ? new String[0] : line.trim().split("\\s+");
            if (parts.length != 3) {
                warn.accept(where + ": \"" + line + "\" should be \"sound volume pitch\"; it was skipped.");
                return null;
            }
            try {
                float volume = Float.parseFloat(parts[1]);
                float pitch = Float.parseFloat(parts[2]);
                if (pitch < MINIMUM_PITCH || pitch > MAXIMUM_PITCH) {
                    float clamped = Math.max(MINIMUM_PITCH, Math.min(MAXIMUM_PITCH, pitch));
                    warn.accept(where + ": pitch " + parts[2] + " is outside what the game plays ("
                            + MINIMUM_PITCH + "-" + MAXIMUM_PITCH + "), so " + clamped + " is used.");
                    pitch = clamped;
                }
                return new Cue(parts[0].toLowerCase(Locale.ROOT), volume, pitch);
            } catch (IllegalArgumentException invalid) {
                warn.accept(where + ": \"" + line + "\" could not be read (" + invalid.getMessage()
                        + "); it was skipped.");
                return null;
            }
        }

        /** The line config.yml holds for it. */
        public String line() {
            return sound + " " + volume + " " + pitch;
        }
    }

    public SoundSettings {
        EnumMap<Event, List<Cue>> copy = new EnumMap<>(Event.class);
        for (Event event : Event.values()) copy.put(event, List.copyOf(sounds.getOrDefault(event, List.of())));
        sounds = copy;
        if (ventRefreshTicks < 1) throw new IllegalArgumentException("audio.vent-airflow-refresh-ticks must be positive");
    }

    public List<Cue> get(Event event) {
        return sounds.get(event);
    }

    /** What EZvac played before sounds were configurable, plus the vent airflow sound. */
    public static SoundSettings defaults() {
        EnumMap<Event, List<Cue>> sounds = new EnumMap<>(Event.class);
        sounds.put(Event.EQUIPMENT_HUM, List.of(new Cue("block.beacon.ambient", 1.6f, 0.55f)));
        sounds.put(Event.FURNACE_START, List.of(new Cue("block.wooden_button.click_on", 0.5f, 1.8f),
                new Cue("block.dispenser.fail", 0.4f, 1.5f), new Cue("item.firecharge.use", 0.6f, 0.5f)));
        sounds.put(Event.FURNACE_STOP, List.of(new Cue("block.furnace.fire_crackle", 0.25f, 0.6f)));
        sounds.put(Event.EQUIPMENT_START, List.of(new Cue("block.wooden_button.click_on", 0.5f, 1.8f),
                new Cue("block.piston.extend", 0.4f, 0.5f), new Cue("block.beacon.activate", 0.3f, 0.5f)));
        sounds.put(Event.EQUIPMENT_STOP, List.of(new Cue("block.wooden_button.click_off", 0.5f, 1.5f),
                new Cue("block.beacon.deactivate", 0.3f, 0.5f)));
        sounds.put(Event.UNIT_START, List.of(new Cue("block.wooden_button.click_on", 0.4f, 1.6f)));
        sounds.put(Event.UNIT_STOP, List.of(new Cue("block.wooden_button.click_off", 0.4f, 1.4f)));
        sounds.put(Event.DEHUMIDIFIER_START, List.of(new Cue("block.beacon.activate", 0.2f, 1.2f)));
        sounds.put(Event.DEHUMIDIFIER_HUM, List.of(dehumidifierHum(1.6f, 0.55f)));
        sounds.put(Event.DEHUMIDIFIER_EMPTYING, List.of(new Cue("block.bubble_column.upwards_ambient", 0.8f, 0.8f)));
        sounds.put(Event.HUMIDIFIER_BUBBLES, List.of(new Cue("block.bubble_column.upwards_ambient", 0.35f, 1.0f)));
        sounds.put(Event.VENT_AIRFLOW, List.of(new Cue("block.conduit.ambient", 0.3f, 0.5f)));
        return new SoundSettings(sounds, 80);
    }

    /** A dehumidifier hums like a heat pump, only smaller: quieter and higher. */
    private static Cue dehumidifierHum(float humVolume, float humPitch) {
        return new Cue("block.beacon.ambient", round(humVolume * 0.4f),
                Math.min(MAXIMUM_PITCH, round(humPitch * 1.6f)));
    }

    /**
     * Reads {@code audio.sounds}. An event that isn't there keeps its default;
     * a line that can't be read is skipped with a warning.
     */
    public static SoundSettings load(ConfigurationSection config, Consumer<String> warn) {
        SoundSettings defaults = defaults();
        EnumMap<Event, List<Cue>> sounds = new EnumMap<>(Event.class);
        for (Event event : Event.values()) {
            String path = "audio.sounds." + event.key();
            if (!config.contains(path)) {
                sounds.put(event, defaults.get(event));
                continue;
            }
            List<Cue> cues = new ArrayList<>();
            if (config.isList(path)) {
                for (String line : config.getStringList(path)) {
                    Cue cue = Cue.parse(line, path, warn);
                    if (cue != null) cues.add(cue);
                }
            } else if (config.isString(path)) {
                Cue cue = Cue.parse(config.getString(path), path, warn);
                if (cue != null) cues.add(cue);
            } else {
                warn.accept(path + " should be a list of \"sound volume pitch\" lines; the default was used.");
                cues.addAll(defaults.get(event));
            }
            sounds.put(event, cues);
        }
        long refresh = config.getLong("audio.vent-airflow-refresh-ticks", defaults.ventRefreshTicks());
        if (refresh < 1) {
            warn.accept("audio.vent-airflow-refresh-ticks must be positive; " + defaults.ventRefreshTicks() + " was used.");
            refresh = defaults.ventRefreshTicks();
        }
        return new SoundSettings(sounds, refresh);
    }

    /**
     * Before Pilot 5 the hum was tuned with {@code audio.hum-volume} and
     * {@code audio.hum-pitch}. When the updater has just added the new hum
     * settings to such a config, they take that tuning, so a server keeps
     * the hum it had. Nothing the admin wrote is changed.
     *
     * @param added the paths the updater just added
     */
    public static void carryOverHum(YamlConfiguration config, List<String> added) {
        String equipmentHum = "audio.sounds." + Event.EQUIPMENT_HUM.key();
        String dehumidifierHum = "audio.sounds." + Event.DEHUMIDIFIER_HUM.key();
        if (!added.contains(equipmentHum)) return;
        for (String old : List.of("audio.hum-volume", "audio.hum-pitch"))
            if (config.contains(old))
                config.setComments(old, List.of("No longer read: replaced by audio.sounds in 2.0 Pilot 5."));
        if (!config.contains("audio.hum-volume") && !config.contains("audio.hum-pitch")) return;
        float volume = (float) config.getDouble("audio.hum-volume", 1.6);
        float pitch = (float) config.getDouble("audio.hum-pitch", 0.55);
        if (!(volume > 0f) || !Float.isFinite(volume) || !Float.isFinite(pitch)) return;
        pitch = Math.max(MINIMUM_PITCH, Math.min(MAXIMUM_PITCH, pitch));
        config.set(equipmentHum, List.of(new Cue("block.beacon.ambient", volume, pitch).line()));
        if (added.contains(dehumidifierHum))
            config.set(dehumidifierHum, List.of(dehumidifierHum(volume, pitch).line()));
    }

    private static float round(float value) {
        return Math.round(value * 1000f) / 1000f;
    }
}
