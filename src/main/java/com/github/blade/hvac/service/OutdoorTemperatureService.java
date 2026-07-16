package com.github.blade.hvac.service;

import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.simulation.ThermalModel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.Locale;

/** Biome profile sampling that never force-loads thermostat chunks. */
public final class OutdoorTemperatureService {
    private enum Profile {
        SNOWY(25, 5), COLD(45, 28), TEMPERATE(72, 52), WARM(85, 68), HOT(100, 78),
        NETHER(112, 112), END(30, 30);
        final double noon;
        final double midnight;
        Profile(double noon, double midnight) { this.noon = noon; this.midnight = midnight; }
    }

    public double temperatureFor(Thermostat thermostat) {
        BlockKey position = thermostat.position();
        World world = position.world();
        if (world == null) return thermostat.lastOutdoorF();
        if (position.isChunkLoaded()) {
            Location location = position.location();
            if (location != null) {
                Profile profile = profile(location.getBlock().getBiome());
                thermostat.setOutdoorProfile(profile.noon, profile.midnight);
            }
        }
        double value = ThermalModel.outdoorTemperature(world.getTime(),
                thermostat.outdoorNoonF(), thermostat.outdoorMidnightF());
        thermostat.setLastOutdoorF(value);
        return value;
    }

    public double temperatureAt(Location location) {
        if (location == null || location.getWorld() == null) return 70.0;
        Profile profile = profile(location.getBlock().getBiome());
        return ThermalModel.outdoorTemperature(location.getWorld().getTime(), profile.noon, profile.midnight);
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
