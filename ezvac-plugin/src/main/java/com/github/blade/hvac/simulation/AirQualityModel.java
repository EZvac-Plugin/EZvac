package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.AirQualitySettings;

/**
 * What is in a space's air (3.2): fine particles (PM2.5), coarse particles
 * (PM2.5 to PM10) and pollen. Pure and stateless, like {@link HumidityModel}.
 */
public final class AirQualityModel {
    /** Nothing is ever simulated past this, in µg/m³ or grains/m³. */
    public static final double MAXIMUM = 5_000.0;

    private AirQualityModel() {}

    /**
     * The air in a space, or a rate or amount per kind of particle.
     *
     * @param fine   PM2.5, µg/m³
     * @param coarse particles between PM2.5 and PM10, µg/m³; PM10 is fine + coarse
     * @param pollen grains/m³; not part of the AQI
     */
    public record Air(double fine, double coarse, double pollen) {
        public static final Air NONE = new Air(0.0, 0.0, 0.0);
        /** Not simulated yet: a space starts at the outdoor air. */
        public static final Air UNKNOWN = new Air(Double.NaN, Double.NaN, Double.NaN);

        public Air {
            fine = clean(fine);
            coarse = clean(coarse);
            pollen = clean(pollen);
        }

        private static double clean(double value) {
            return Double.isNaN(value) ? Double.NaN : Math.max(0.0, Math.min(MAXIMUM, value));
        }

        public boolean known() { return Double.isFinite(fine) && Double.isFinite(coarse) && Double.isFinite(pollen); }

        public double pm25() { return fine; }

        public double pm10() { return fine + coarse; }

        public Air plus(Air other) {
            return new Air(fine + other.fine, coarse + other.coarse, pollen + other.pollen);
        }

        public Air times(double factor) {
            return new Air(fine * factor, coarse * factor, pollen * factor);
        }

        /** Saves it as {@code <prefix>Fine}, {@code <prefix>Coarse} and {@code <prefix>Pollen}, when known. */
        public void write(java.util.Map<String, Object> map, String prefix) {
            if (!known()) return;
            map.put(prefix + "Fine", fine);
            map.put(prefix + "Coarse", coarse);
            map.put(prefix + "Pollen", pollen);
        }

        /** What {@link #write} saved, or {@link #UNKNOWN} for a save from before 3.2. */
        public static Air read(java.util.Map<String, Object> map, String prefix) {
            return new Air(number(map.get(prefix + "Fine")), number(map.get(prefix + "Coarse")),
                    number(map.get(prefix + "Pollen")));
        }

        private static double number(Object value) {
            return value instanceof Number number && Double.isFinite(number.doubleValue())
                    ? number.doubleValue() : Double.NaN;
        }

        /** Part of the way from this toward the other, as a reading blends a room into the outdoors. */
        public Air toward(Air other, double share) {
            if (!(share > 0.0)) return this;
            if (share >= 1.0) return other;
            return new Air(fine + (other.fine - fine) * share, coarse + (other.coarse - coarse) * share,
                    pollen + (other.pollen - pollen) * share);
        }
    }

    /**
     * What is adding or removing particles in a space during one step.
     *
     * @param sources         particles added per in-game hour, already diluted to the space's size
     * @param systemFiltering share of the step a system's equipment ran, 0 to 1
     * @param purifierScale   sum of running purifiers' strength in this space, 1 = one unit at its reference size
     */
    public record Loads(Air sources, double systemFiltering, double purifierScale) {
        public static final Loads NONE = new Loads(Air.NONE, 0.0, 0.0);
    }

    /**
     * @param air      the space's new air
     * @param captured what the purifiers took out during the step, in µg/m³ (grains/m³ for pollen) of this space
     */
    public record Result(Air air, Air captured) {}

    /**
     * Advances a space's air. Each kind of particle drifts toward the outdoors
     * through infiltration, is added by the sources, and is removed by
     * settling, the system's filter and purifiers. The rates hold for the whole
     * step, so it is solved exactly: an exponential approach to equilibrium.
     */
    public static Result step(Air start, Air outdoor, Loads loads, long elapsedTicks, double infiltrationPerHour,
                              AirQualitySettings settings) {
        if (!outdoor.known()) throw new IllegalArgumentException("outdoor air must be known");
        Air from = start.known() ? start : outdoor;
        if (elapsedTicks <= 0) return new Result(from, Air.NONE);
        double hours = elapsedTicks / ThermalModel.MINECRAFT_TICKS_PER_HOUR;
        double system = Math.max(0.0, Math.min(1.0, loads.systemFiltering()));
        double purifiers = Math.max(0.0, loads.purifierScale());
        Air sources = loads.sources().known() ? loads.sources() : Air.NONE;
        AirQualitySettings.Rates settling = settings.settling();
        AirQualitySettings.Rates filter = settings.systemFilter();
        AirQualitySettings.Rates purifier = settings.purifier().rate();
        double[] fine = species(from.fine(), outdoor.fine(), sources.fine(), infiltrationPerHour,
                settling.fine() + filter.fine() * system, purifier.fine() * purifiers, hours);
        double[] coarse = species(from.coarse(), outdoor.coarse(), sources.coarse(), infiltrationPerHour,
                settling.coarse() + filter.coarse() * system, purifier.coarse() * purifiers, hours);
        double[] pollen = species(from.pollen(), outdoor.pollen(), sources.pollen(), infiltrationPerHour,
                settling.pollen() + filter.pollen() * system, purifier.pollen() * purifiers, hours);
        return new Result(new Air(fine[0], coarse[0], pollen[0]), new Air(fine[1], coarse[1], pollen[1]));
    }

    /**
     * One kind of particle: dC/dt = infiltration·(outdoor − C) + source − (removal + purifier)·C.
     *
     * @return the new concentration, and what the purifiers captured over the step
     */
    static double[] species(double start, double outdoor, double source, double infiltration, double removal,
                            double purifier, double hours) {
        double k = infiltration + removal + purifier;
        if (!(k > 0.0)) {
            double value = start + source * hours;
            return new double[]{value, 0.0};
        }
        double equilibrium = (infiltration * outdoor + source) / k;
        double decay = Math.exp(-k * hours);
        double value = equilibrium + (start - equilibrium) * decay;
        // The integral of the concentration over the step, so the purifiers' share is exact too.
        double exposure = equilibrium * hours + (start - equilibrium) * (1.0 - decay) / k;
        return new double[]{value, Math.max(0.0, purifier * exposure)};
    }
}
