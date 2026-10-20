package com.github.blade.hvac.simulation;

/**
 * Moist-air arithmetic. EZvac simulates moisture as a dew point, the amount
 * of water in the air, and shows relative humidity, which also depends on
 * temperature. That split is what makes the physics come out right on its
 * own: heating a room lowers its RH without touching its moisture, and
 * cooling air past its dew point is what wrings water out of it.
 *
 * <p>Pure functions over Fahrenheit and percent, using the Magnus formula
 * (Alduchov and Eskridge constants).
 */
public final class Psychrometrics {
    private static final double A = 17.625;
    private static final double B = 243.04;
    private static final double MINIMUM_RH = 0.5;

    private Psychrometrics() {}

    /** Relative humidity in percent, 0.5 to 100. A dew point above the temperature means saturated air. */
    public static double relativeHumidity(double temperatureF, double dewPointF) {
        requireFinite("temperature", temperatureF);
        requireFinite("dew point", dewPointF);
        if (dewPointF >= temperatureF) return 100.0;
        double value = 100.0 * saturationPressure(toC(dewPointF)) / saturationPressure(toC(temperatureF));
        return Math.max(MINIMUM_RH, Math.min(100.0, value));
    }

    /** The dew point of air at a temperature and relative humidity (percent, clamped to 0.5-100). */
    public static double dewPointF(double temperatureF, double relativeHumidity) {
        requireFinite("temperature", temperatureF);
        requireFinite("relative humidity", relativeHumidity);
        double rh = Math.max(MINIMUM_RH, Math.min(100.0, relativeHumidity));
        double c = toC(temperatureF);
        double gamma = Math.log(rh / 100.0) + A * c / (B + c);
        return Math.min(temperatureF, toF(B * gamma / (A - gamma)));
    }

    /**
     * How warm it feels: Steadman's apparent temperature, the formula the
     * Australian Bureau of Meteorology uses. Unlike the classic heat index it
     * is continuous across every temperature, and it includes wind, so a
     * stormy day feels colder than the thermometer says. Indoors the wind is 0.
     *
     * @param windMs wind speed in metres per second
     */
    public static double apparentTemperatureF(double temperatureF, double relativeHumidity, double windMs) {
        requireFinite("temperature", temperatureF);
        requireFinite("relative humidity", relativeHumidity);
        requireFinite("wind", windMs);
        double c = toC(temperatureF);
        double rh = Math.max(0.0, Math.min(100.0, relativeHumidity));
        double vapourHpa = rh / 100.0 * saturationPressure(c);
        return toF(c + 0.33 * vapourHpa - 0.70 * Math.max(0.0, windMs) - 4.00);
    }

    /** Saturation vapour pressure over water, hPa. */
    static double saturationPressure(double celsius) {
        return 6.1094 * Math.exp(A * celsius / (B + celsius));
    }

    static double toC(double fahrenheit) { return (fahrenheit - 32.0) * 5.0 / 9.0; }
    static double toF(double celsius) { return celsius * 9.0 / 5.0 + 32.0; }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
