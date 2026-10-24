package com.github.blade.hvac.service;

import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.simulation.HumidityModel;
import com.github.blade.hvac.simulation.Precipitation;
import com.github.blade.hvac.simulation.Psychrometrics;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.WeatherModel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Biome profile sampling that never force-loads thermostat chunks. */
public final class OutdoorTemperatureService {
    private enum Profile {
        SNOWY(25, 5, Precipitation.SNOW), COLD(45, 28, Precipitation.RAIN),
        TEMPERATE(72, 52, Precipitation.RAIN), WARM(85, 68, Precipitation.RAIN),
        HOT(100, 78, Precipitation.NONE), NETHER(112, 112, Precipitation.NONE), END(30, 30, Precipitation.NONE);
        final double noon;
        final double midnight;
        final Precipitation precipitation;
        Profile(double noon, double midnight, Precipitation precipitation) {
            this.noon = noon;
            this.midnight = midnight;
            this.precipitation = precipitation;
        }
    }

    /**
     * The outdoors at one spot, weather included.
     *
     * @param airF      outdoor air temperature
     * @param waterF    natural water temperature (lakes, the sea, unheated pools)
     * @param dewPointF outdoor moisture
     * @param windMs    outdoor wind, for apparent temperature
     * @param weather   Clear, Rain, Thunder or Snow; empty where weather never reaches
     */
    public record Outdoor(double airF, double waterF, double dewPointF, double windMs, String weather) {
        public double humidity() { return Psychrometrics.relativeHumidity(airF, dewPointF); }
        public double feelsLikeF() { return Psychrometrics.apparentTemperatureF(airF, humidity(), windMs); }
    }

    private final ClimateSettings climate;
    /** Null outside a running server (tests): every world then reads as clear. */
    private final WeatherService weather;

    /**
     * Classification is pure string matching over a biome's name, and it ran
     * per thermostat per cycle and per thermometer per refresh. Biome is an
     * enum, so each one only ever needs classifying once.
     */
    private final Map<Biome, Profile> profileCache = new EnumMap<>(Biome.class);
    private final Map<Biome, Double> downfallCache = new EnumMap<>(Biome.class);

    public OutdoorTemperatureService() {
        this(ClimateSettings.defaults(), null);
    }

    public OutdoorTemperatureService(ClimateSettings climate, WeatherService weather) {
        this.climate = Objects.requireNonNull(climate, "climate");
        this.weather = weather;
    }

    public ClimateSettings climate() { return climate; }

    private Profile cachedProfile(Biome biome) {
        Profile cached = profileCache.get(biome);
        if (cached != null) return cached;
        Profile computed = profile(biome);
        profileCache.put(biome, computed);
        return computed;
    }

    /** Vanilla's downfall, the biome value behind rain and grass colour. Data-pack biomes all share CUSTOM, so they are not cached. */
    private double downfall(Block block, Biome biome) {
        if (biome == Biome.CUSTOM) return block.getHumidity();
        Double cached = downfallCache.get(biome);
        if (cached != null) return cached;
        double value = block.getHumidity();
        downfallCache.put(biome, value);
        return value;
    }

    private WeatherModel.Effect effect(World world, Precipitation precipitation) {
        WeatherModel.Conditions conditions = weather == null ? WeatherModel.Conditions.CLEAR : weather.conditions(world);
        return WeatherModel.effect(precipitation, conditions, climate.weather());
    }

    private String weatherWord(World world, Precipitation precipitation) {
        if (!climate.weather().enabled()) return "";
        WeatherModel.Conditions conditions = weather == null ? WeatherModel.Conditions.CLEAR : weather.conditions(world);
        return WeatherModel.word(precipitation, conditions);
    }

    /**
     * What the sky is doing at a spot, for weather sensors: Clear, Rain,
     * Thunder or Snow, from the world's own weather. Unlike the HUD's weather
     * word it doesn't depend on EZvac's weather effects being switched on: a
     * sensor reports the sky, whatever the temperatures do. Clear where it
     * never rains, such as a desert.
     */
    public String skyAt(Location location) {
        if (location == null || location.getWorld() == null) return "Clear";
        World world = location.getWorld();
        Precipitation precipitation = cachedProfile(location.getBlock().getBiome()).precipitation;
        String word = WeatherModel.word(precipitation,
                WeatherModel.Conditions.settled(world.hasStorm(), world.isThundering()));
        return word.isEmpty() ? "Clear" : word;
    }

    /** Refreshes a thermostat's cached biome profile when its chunk is loaded; never loads it. */
    private void sample(Thermostat thermostat) {
        BlockKey position = thermostat.position();
        if (!position.isChunkLoaded()) return;
        Location location = position.location();
        if (location == null) return;
        Block block = location.getBlock();
        Biome biome = block.getBiome();
        Profile profile = cachedProfile(biome);
        thermostat.setOutdoorProfile(profile.noon, profile.midnight);
        thermostat.setOutdoorHumidityProfile(downfall(block, biome), profile.precipitation);
    }

    public double temperatureFor(Thermostat thermostat) {
        return temperatureFor(thermostat, true);
    }

    private double temperatureFor(Thermostat thermostat, boolean resample) {
        World world = thermostat.position().world();
        if (world == null) return thermostat.lastOutdoorF();
        if (resample) sample(thermostat);
        WeatherModel.Effect effect = effect(world, thermostat.outdoorPrecipitation());
        double value = air(world, effect, thermostat.outdoorNoonF(), thermostat.outdoorMidnightF());
        thermostat.setLastOutdoorF(value);
        return value;
    }

    /** The full outdoor picture at a thermostat, from its cached profile when its chunk is unloaded. */
    public Outdoor climateFor(Thermostat thermostat) {
        return climateFor(thermostat, true);
    }

    /**
     * The same, from the profile the thermostat already cached, without
     * sampling its biome again: the controller has just sampled it this cycle.
     */
    public Outdoor cachedClimateFor(Thermostat thermostat) {
        return climateFor(thermostat, false);
    }

    private Outdoor climateFor(Thermostat thermostat, boolean resample) {
        World world = thermostat.position().world();
        double air = temperatureFor(thermostat, resample);
        if (world == null) {
            double dew = Psychrometrics.dewPointF(air, 50.0);
            return new Outdoor(air, ThermalModel.naturalWaterTemperature(thermostat.outdoorNoonF(),
                    thermostat.outdoorMidnightF()), dew, climate.weather().windClearMs(), "");
        }
        WeatherModel.Effect effect = effect(world, thermostat.outdoorPrecipitation());
        return outdoor(world, effect, thermostat.outdoorNoonF(), thermostat.outdoorMidnightF(),
                thermostat.outdoorDownfall(), thermostat.outdoorPrecipitation(), air);
    }

    /**
     * What a thermostat's space drifts toward when nothing conditions it: the
     * outdoor air for a room, the biome's natural water for a pool. A heated
     * pool left alone cools toward lake temperature, not toward a hot noon.
     */
    public double driftBaselineFor(Thermostat thermostat) {
        return thermostat.pool() ? naturalWaterFor(thermostat) : temperatureFor(thermostat);
    }

    /** Natural water temperature at a thermostat's biome, refreshing its cached profile like temperatureFor. */
    public double naturalWaterFor(Thermostat thermostat) {
        temperatureFor(thermostat);
        World world = thermostat.position().world();
        WeatherModel.Effect effect = world == null ? WeatherModel.Effect.calm(climate.weather())
                : effect(world, thermostat.outdoorPrecipitation());
        return water(effect, thermostat.outdoorNoonF(), thermostat.outdoorMidnightF());
    }

    /** Natural water temperature at a location: steady, with no day/night swing. */
    public double naturalWaterAt(Location location) {
        if (location == null || location.getWorld() == null) return 60.0;
        Profile profile = cachedProfile(location.getBlock().getBiome());
        return water(effect(location.getWorld(), profile.precipitation), profile.noon, profile.midnight);
    }

    public double temperatureAt(Location location) {
        if (location == null || location.getWorld() == null) return 70.0;
        Profile profile = cachedProfile(location.getBlock().getBiome());
        return air(location.getWorld(), effect(location.getWorld(), profile.precipitation), profile.noon, profile.midnight);
    }

    /** The full outdoor picture at a location. */
    public Outdoor climateAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return new Outdoor(70.0, 60.0, Psychrometrics.dewPointF(70.0, 50.0), climate.weather().windClearMs(), "");
        }
        World world = location.getWorld();
        Block block = location.getBlock();
        Biome biome = block.getBiome();
        Profile profile = cachedProfile(biome);
        WeatherModel.Effect effect = effect(world, profile.precipitation);
        double air = air(world, effect, profile.noon, profile.midnight);
        return outdoor(world, effect, profile.noon, profile.midnight, downfall(block, biome), profile.precipitation, air);
    }

    private Outdoor outdoor(World world, WeatherModel.Effect effect, double noonF, double midnightF,
                            double downfall, Precipitation precipitation, double airF) {
        double dew = HumidityModel.outdoorDewPointF(noonF, midnightF, downfall, effect, airF, climate.humidity());
        return new Outdoor(airF, water(effect, noonF, midnightF), dew, effect.windMs(), weatherWord(world, precipitation));
    }

    private static double air(World world, WeatherModel.Effect effect, double noonF, double midnightF) {
        return ThermalModel.outdoorTemperature(world.getTime(),
                effect.noonF(noonF, midnightF), effect.midnightF(noonF, midnightF));
    }

    /** Water barely reacts to weather: it follows only a small share of the air's shift. */
    private double water(WeatherModel.Effect effect, double noonF, double midnightF) {
        double value = ThermalModel.naturalWaterTemperature(noonF, midnightF)
                + effect.meanShiftF() * climate.weather().waterShare();
        return Math.max(ThermalModel.NATURAL_WATER_MINIMUM_F, Math.min(ThermalModel.NATURAL_WATER_MAXIMUM_F, value));
    }

    private static Profile profile(Biome biome) {
        String name = biome.name().toUpperCase(Locale.ROOT);
        if (name.contains("NETHER") || name.contains("BASALT") || name.contains("CRIMSON")
                || name.contains("WARPED") || name.contains("SOUL_SAND")) return Profile.NETHER;
        if (name.contains("END")) return Profile.END;
        if (name.contains("FROZEN") || name.contains("SNOWY") || name.contains("ICE")
                || name.contains("COLD_OCEAN")) return Profile.SNOWY;
        if (name.contains("DESERT") || name.contains("BADLANDS") || name.contains("SAVANNA"))
            return Profile.HOT;
        if (name.contains("JUNGLE") || name.contains("WARM_OCEAN") || name.contains("BAMBOO"))
            return Profile.WARM;
        if (name.contains("TAIGA") || name.contains("WINDSWEPT") || name.contains("GROVE")
                || name.contains("PEAKS") || name.contains("STONY_SHORE")) return Profile.COLD;
        return Profile.TEMPERATE;
    }
}
