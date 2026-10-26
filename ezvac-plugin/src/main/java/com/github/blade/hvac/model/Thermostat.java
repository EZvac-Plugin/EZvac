package com.github.blade.hvac.model;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.simulation.BiomeBlend;
import com.github.blade.hvac.simulation.BiomeClimate;
import com.github.blade.hvac.simulation.Precipitation;
import com.github.blade.hvac.simulation.ThermalModel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Persistent state for one sign-based thermostat/controller. */
public final class Thermostat {
    private final BlockKey position;
    private String id;
    private SystemId system;
    private double targetF;
    private double roomF;
    private boolean enabled;
    private OperatingMode mode;
    private long ticksInMode;
    private double outdoorNoonF;
    private double outdoorMidnightF;
    private double lastOutdoorF;
    /** The biome's vanilla downfall (0 desert .. 1 jungle), cached like the noon/midnight profile. */
    private double outdoorDownfall = DEFAULT_DOWNFALL;
    private Precipitation outdoorPrecipitation = Precipitation.RAIN;
    /**
     * Shares of its surroundings where rain and snow fall (2.1). Whole for a
     * thermostat inside one biome; in between on a biome border.
     */
    private double outdoorRainShare = 1.0;
    private double outdoorSnowShare = 0.0;
    /**
     * How far the seasons move its outdoor climate (2.1). NaN in a
     * save from before then, until it samples its surroundings again.
     */
    private double outdoorSeasonalF = Double.NaN;
    /** World game time of the last biome sample; never saved, so a restart samples at once. */
    private long outdoorSampledAt = Long.MIN_VALUE;
    /** The air's moisture as a dew point; NaN until first simulated (2.0). */
    private double roomDewPointF = Double.NaN;
    /**
     * Pool thermostats clamp to the water range. Since 3.0 a thermostat
     * follows what is linked to it: water equipment or grates make it one.
     */
    private boolean pool;

    /** A temperate biome's downfall, used until the thermostat's chunk is first sampled. */
    public static final double DEFAULT_DOWNFALL = 0.4;
    /** The targets a new room or pool thermostat starts at. */
    public static final double ROOM_DEFAULT_F = 72.0;
    public static final double POOL_DEFAULT_F = 82.0;

    public Thermostat(BlockKey position, String id, String systemKey,
                      double targetF, double roomF, HvacSettings settings) {
        this(position, id, systemKey, targetF, roomF, settings, false);
    }

    public Thermostat(BlockKey position, String id, String systemKey,
                      double targetF, double roomF, HvacSettings settings, boolean pool) {
        this(position, id, SystemId.of(position, systemKey), targetF, roomF, settings, pool);
    }

    public Thermostat(BlockKey position, String id, SystemId system,
                      double targetF, double roomF, HvacSettings settings, boolean pool) {
        this(position, id, system, settings.clampTarget(targetF, pool),
                ThermalModel.clampTemperature(roomF), true, OperatingMode.IDLE,
                settings.minimumOffTicks(), 72.0, 52.0, 62.0, pool);
    }

    private Thermostat(BlockKey position, String id, SystemId system, double targetF,
                       double roomF, boolean enabled, OperatingMode mode, long ticksInMode,
                       double outdoorNoonF, double outdoorMidnightF, double lastOutdoorF,
                       boolean pool) {
        this.pool = pool;
        this.position = Objects.requireNonNull(position, "position");
        this.id = validateId(id);
        this.system = Objects.requireNonNull(system, "system");
        if (!position.worldId().equals(system.worldId()))
            throw new IllegalArgumentException("thermostat and system worlds differ");
        this.targetF = targetF;
        this.roomF = roomF;
        this.enabled = enabled;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.ticksInMode = Math.max(0L, ticksInMode);
        this.outdoorNoonF = ThermalModel.clampTemperature(outdoorNoonF);
        this.outdoorMidnightF = ThermalModel.clampTemperature(outdoorMidnightF);
        this.lastOutdoorF = ThermalModel.clampTemperature(lastOutdoorF);
    }

    public BlockKey position() { return position; }
    public String id() { return id; }
    public SystemId system() { return system; }
    public double targetF() { return targetF; }
    public double roomF() { return roomF; }
    public boolean enabled() { return enabled; }
    public OperatingMode mode() { return mode; }
    public long ticksInMode() { return ticksInMode; }
    public double outdoorNoonF() { return outdoorNoonF; }
    public double outdoorMidnightF() { return outdoorMidnightF; }
    public double lastOutdoorF() { return lastOutdoorF; }
    public boolean pool() { return pool; }
    public double outdoorDownfall() { return outdoorDownfall; }
    public Precipitation outdoorPrecipitation() { return outdoorPrecipitation; }
    /**
     * The outdoor climate it last sampled, transitions between biomes and its
     * seasons included; a thermostat that hasn't sampled since 2.1
     * expects the seasons its climate usually has.
     */
    public BiomeBlend.Blended outdoorClimate() {
        double seasonal = Double.isFinite(outdoorSeasonalF) ? outdoorSeasonalF
                : BiomeClimate.estimateSeasonalF((outdoorNoonF + outdoorMidnightF) / 2.0);
        return new BiomeBlend.Blended(outdoorNoonF, outdoorMidnightF, outdoorDownfall, outdoorRainShare,
                outdoorSnowShare, seasonal);
    }
    public double roomDewPointF() { return roomDewPointF; }
    public boolean hasRoomDewPoint() { return Double.isFinite(roomDewPointF); }

    public void setId(String id) { this.id = validateId(id); }
    /** Only HvacRegistry may move a registered thermostat: its indexes follow the system. */
    public void setSystem(SystemId system) {
        Objects.requireNonNull(system, "system");
        if (!position.worldId().equals(system.worldId()))
            throw new IllegalArgumentException("thermostat and system worlds differ");
        this.system = system;
    }
    public void setTargetF(double targetF, HvacSettings settings) {
        this.targetF = settings.clampTarget(targetF, pool);
    }

    /**
     * Switches between the room and the pool range. A target still at the
     * old range's default moves to the new one's; any other is clamped.
     *
     * @return true when it changed
     */
    public boolean setPool(boolean pool, HvacSettings settings) {
        if (this.pool == pool) return false;
        double previousDefault = this.pool ? POOL_DEFAULT_F : ROOM_DEFAULT_F;
        this.pool = pool;
        targetF = settings.clampTarget(Math.abs(targetF - previousDefault) < 1e-9
                ? (pool ? POOL_DEFAULT_F : ROOM_DEFAULT_F) : targetF, pool);
        return true;
    }
    public void setRoomF(double roomF) { this.roomF = ThermalModel.clampTemperature(roomF); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setControlState(OperatingMode mode, long ticksInMode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.ticksInMode = Math.max(0L, ticksInMode);
    }
    public void setOutdoorProfile(double noonF, double midnightF) {
        this.outdoorNoonF = ThermalModel.clampTemperature(noonF);
        this.outdoorMidnightF = ThermalModel.clampTemperature(midnightF);
    }
    public void setLastOutdoorF(double outdoorF) { this.lastOutdoorF = ThermalModel.clampTemperature(outdoorF); }
    public void setOutdoorHumidityProfile(double downfall, Precipitation precipitation) {
        this.outdoorDownfall = Double.isFinite(downfall) ? Math.max(0.0, Math.min(1.0, downfall)) : DEFAULT_DOWNFALL;
        this.outdoorPrecipitation = Objects.requireNonNull(precipitation, "precipitation");
        BiomeBlend.Blended pure = BiomeBlend.Blended.of(0.0, 0.0, 0.0, precipitation);
        this.outdoorRainShare = pure.rainShare();
        this.outdoorSnowShare = pure.snowShare();
    }
    /** True when the biome around it was last sampled at least this many ticks ago. */
    public boolean outdoorSampleDue(long gameTime, long intervalTicks) {
        return outdoorSampledAt == Long.MIN_VALUE || gameTime - outdoorSampledAt >= intervalTicks
                || gameTime < outdoorSampledAt;
    }
    public void markOutdoorSampled(long gameTime) { this.outdoorSampledAt = gameTime; }
    /** Caches a sampled outdoor climate: temperatures, moisture and weather shares together. */
    public void setOutdoorClimate(BiomeBlend.Blended climate) {
        setOutdoorProfile(climate.noonF(), climate.midnightF());
        setOutdoorHumidityProfile(climate.downfall(), climate.dominant());
        setOutdoorWeatherShares(climate.rainShare(), climate.snowShare());
        if (Double.isFinite(climate.seasonalF())) this.outdoorSeasonalF = Math.max(0.0, climate.seasonalF());
    }
    private void setOutdoorWeatherShares(double rain, double snow) {
        if (!Double.isFinite(rain) || !Double.isFinite(snow)) return;
        this.outdoorRainShare = Math.max(0.0, Math.min(1.0, rain));
        this.outdoorSnowShare = Math.max(0.0, Math.min(1.0 - outdoorRainShare, snow));
    }
    /** The dew point never exceeds the air temperature: that would be more than 100% RH. */
    public void setRoomDewPointF(double dewPointF) {
        if (!Double.isFinite(dewPointF)) { this.roomDewPointF = Double.NaN; return; }
        this.roomDewPointF = Math.min(roomF, ThermalModel.clampTemperature(dewPointF));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("system", system.key());
        position.write(map, "");
        map.put("targetF", targetF);
        map.put("roomF", roomF);
        map.put("enabled", enabled);
        map.put("mode", mode.name());
        map.put("ticksInMode", ticksInMode);
        map.put("outdoorNoonF", outdoorNoonF);
        map.put("outdoorMidnightF", outdoorMidnightF);
        map.put("lastOutdoorF", lastOutdoorF);
        map.put("pool", pool);
        map.put("outdoorDownfall", outdoorDownfall);
        map.put("outdoorPrecipitation", outdoorPrecipitation.name());
        // Only a thermostat on a biome border has shares worth saving; inside a biome they follow from the above.
        if (!outdoorClimate().pure()) {
            map.put("outdoorRainShare", outdoorRainShare);
            map.put("outdoorSnowShare", outdoorSnowShare);
        }
        // Saved once it has sampled since 2.1; an older save loads and saves back unchanged until then.
        if (Double.isFinite(outdoorSeasonalF)) map.put("outdoorSeasonalF", outdoorSeasonalF);
        if (hasRoomDewPoint()) map.put("roomDewPointF", roomDewPointF);
        return map;
    }

    public static Thermostat fromMap(Map<String, Object> map, HvacSettings settings) {
        BlockKey position = BlockKey.read(map, "");
        String id = string(map, "id", string(map, "label", null));
        // Format 7 (3.0) saves the system; before then it was the group's name, which becomes its key.
        String systemKey = string(map, "system", string(map, "group", string(map, "hvacGroupLabel", null)));
        double target = number(map, "targetF", number(map, "targetTempF", 72.0));
        double room = number(map, "roomF", number(map, "roomTempF", 72.0));
        long ticks = whole(map, "ticksInMode", -1L);
        if (ticks < 0L) ticks = Math.max(0L, Math.round(number(map,
                "modeElapsedMinecraftHours", settings.minimumOffTicks() / 1_000.0) * 1_000.0));
        boolean pool = bool(map, "pool", false);
        Thermostat thermostat = new Thermostat(position, id, SystemId.of(position, systemKey),
                settings.clampTarget(target, pool), ThermalModel.clampTemperature(room),
                bool(map, "enabled", true), OperatingMode.parse(map.get("mode")), ticks,
                number(map, "outdoorNoonF", 72.0), number(map, "outdoorMidnightF", 52.0),
                number(map, "lastOutdoorF", number(map, "lastOutdoorTempF", 62.0)), pool);
        // Saves from before 2.0 have no humidity: the defaults stand until the
        // biome is sampled, and the room's moisture starts at the outdoor level.
        thermostat.setOutdoorHumidityProfile(number(map, "outdoorDownfall", DEFAULT_DOWNFALL),
                Precipitation.parse(map.get("outdoorPrecipitation")));
        if (map.containsKey("outdoorRainShare"))
            thermostat.setOutdoorWeatherShares(number(map, "outdoorRainShare", 1.0), number(map, "outdoorSnowShare", 0.0));
        double seasonal = number(map, "outdoorSeasonalF", Double.NaN);
        if (Double.isFinite(seasonal)) thermostat.outdoorSeasonalF = Math.max(0.0, seasonal);
        thermostat.setRoomDewPointF(number(map, "roomDewPointF", Double.NaN));
        return thermostat;
    }

    public static String validateId(String value) {
        if (value == null) throw new IllegalArgumentException("a label is required");
        String id = value.trim();
        if (id.isEmpty() || id.length() > 32 || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("labels must be 1-32 letters, numbers, '_' or '-'");
        return id;
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : fallback;
    }

    private static long whole(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
