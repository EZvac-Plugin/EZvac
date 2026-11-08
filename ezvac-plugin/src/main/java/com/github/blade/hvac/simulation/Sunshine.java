package com.github.blade.hvac.simulation;

/**
 * The sunshine a person takes in (BETA 1.0 Pilot 4), for how warm it feels in
 * the sun: Steadman's radiation term, in W/m² of body surface. Only the
 * feels-like uses it; the air temperature is a shade reading, as a weather
 * station's is.
 */
public final class Sunshine {
    /** A clear noon with the sun high, standing in the open: about 7 C warmer in a light breeze. */
    public static final double CLEAR_NOON_WM2 = 110.0;
    /** In shade the sky still lights a person: this share of the open sun at full sky light. */
    static final double SHADE_SHARE = 0.25;

    private Sunshine() {}

    /**
     * @param dayTime      the world's time of day: 0 is sunrise, 6000 noon, 12000 sunset
     * @param seasonFactor the season, -1 mid-winter to 1 mid-summer: a lower winter sun is weaker
     * @param wetness      0 clear to 1 raining: clouds
     * @param storm        0 to 1 thundering: darker clouds
     * @param direct       nothing above the spot: the sun reaches it
     * @param skyLight     0 to 15, how much sky reaches it, for the shade
     * @param strength     the config's sun-strength
     */
    public static double absorbedWm2(long dayTime, double seasonFactor, double wetness, double storm,
                                     boolean direct, int skyLight, double strength) {
        if (!(strength > 0.0)) return 0.0;
        long time = Math.floorMod(dayTime, 24_000L);
        if (time >= 12_000L) return 0.0;
        double elevation = Math.sin(Math.PI * time / 12_000.0);
        double season = 0.8 + 0.2 * clamp(seasonFactor, -1.0, 1.0);
        double clouds = clamp(1.0 - 0.7 * clamp(wetness, 0.0, 1.0) - 0.2 * clamp(storm, 0.0, 1.0), 0.05, 1.0);
        double exposure = direct ? 1.0 : SHADE_SHARE * clamp(skyLight / 15.0, 0.0, 1.0);
        return CLEAR_NOON_WM2 * elevation * season * clouds * exposure * strength;
    }

    private static double clamp(double value, double low, double high) {
        return Double.isFinite(value) ? Math.max(low, Math.min(high, value)) : low;
    }
}
