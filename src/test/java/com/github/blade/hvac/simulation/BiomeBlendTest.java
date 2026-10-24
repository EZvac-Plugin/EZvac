package com.github.blade.hvac.simulation;

import com.github.blade.hvac.config.ClimateSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Transition zones between biomes (2.1). */
class BiomeBlendTest {
    private static final int RADIUS = 24;
    private final ClimateSettings.Weather weather = ClimateSettings.defaults().weather();

    /** The blend at x along a straight border: plains (72/52, rain) for x < 0, desert (100/78, dry) from 0. */
    private static BiomeBlend.Blended across(int x) {
        List<BiomeBlend.Sample> samples = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx += BiomeBlend.SAMPLE_SPACING)
            for (int dz = -RADIUS; dz <= RADIUS; dz += BiomeBlend.SAMPLE_SPACING) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > RADIUS) continue;
                boolean desert = x + dx >= 0;
                samples.add(new BiomeBlend.Sample(desert ? 100 : 72, desert ? 78 : 52, desert ? 0.0 : 0.4,
                        desert ? Precipitation.NONE : Precipitation.RAIN, BiomeBlend.weight(distance, RADIUS)));
            }
        return BiomeBlend.blend(samples);
    }

    @Test
    void deepInsideABiomeNothingChanges() {
        BiomeBlend.Blended plains = across(-200);
        assertEquals(BiomeBlend.Blended.of(72, 52, 0.4, Precipitation.RAIN), plains, "exactly, not nearly");
        assertTrue(plains.pure());
        assertEquals(BiomeBlend.Blended.of(100, 78, 0.0, Precipitation.NONE), across(200));
    }

    @Test
    void aBorderRampsInsteadOfJumping() {
        double previous = across(-RADIUS - 8).noonF();
        double steepest = 0.0;
        for (int x = -RADIUS - 4; x <= RADIUS + 8; x += BiomeBlend.SAMPLE_SPACING) {
            double noon = across(x).noonF();
            assertTrue(noon >= previous, "warmer the further into the desert: " + x);
            steepest = Math.max(steepest, noon - previous);
            previous = noon;
        }
        assertTrue(steepest < 6.0, "no 4-block step anywhere near the old 28 F jump: " + steepest);
        double border = across(0).noonF();
        assertTrue(border > 80 && border < 92, "the border is in between: " + border);
        BiomeBlend.Blended edge = across(-4);
        assertTrue(edge.rainShare() > 0.3 && edge.rainShare() < 0.7, "partly rain at the edge: " + edge.rainShare());
        assertEquals(1.0, edge.rainShare() + edge.snowShare() + (1.0 - edge.wetShare()), 1e-12);
    }

    @Test
    void samplesInUnloadedChunksAreLeftOut() {
        assertNull(BiomeBlend.blend(List.of()), "nothing loaded: the caller falls back to the block itself");
        BiomeBlend.Blended one = BiomeBlend.blend(List.of(
                new BiomeBlend.Sample(25, 5, 0.5, Precipitation.SNOW, 1.0),
                new BiomeBlend.Sample(100, 78, 0.0, Precipitation.NONE, 0.0)));
        assertEquals(BiomeBlend.Blended.of(25, 5, 0.5, Precipitation.SNOW), one, "a weightless sample counts for nothing");
    }

    @Test
    void closerBiomesCountMore() {
        assertEquals(1.0, BiomeBlend.weight(0, RADIUS));
        assertTrue(BiomeBlend.weight(4, RADIUS) > BiomeBlend.weight(20, RADIUS));
        assertTrue(BiomeBlend.weight(RADIUS, RADIUS) > 0.0, "the edge of the radius still counts a little");
        assertEquals(0.0, BiomeBlend.weight(RADIUS + 4, RADIUS));
        assertEquals(1.0, BiomeBlend.weight(0, 0));
        assertEquals(0.0, BiomeBlend.weight(4, 0), "radius 0 is the block alone");
    }

    @Test
    void theWeatherWordFollowsWhatTheSurroundingsMostlyGet() {
        assertEquals(Precipitation.RAIN, new BiomeBlend.Blended(80, 60, 0.2, 0.6, 0.0).dominant());
        assertEquals(Precipitation.NONE, new BiomeBlend.Blended(90, 70, 0.1, 0.3, 0.0).dominant());
        assertEquals(Precipitation.SNOW, new BiomeBlend.Blended(30, 10, 0.4, 0.2, 0.5).dominant());
    }

    @Test
    void rainAtADesertsEdgeCoolsItPartway() {
        WeatherModel.Conditions rain = WeatherModel.Conditions.settled(true, false);
        BiomeBlend.Blended plains = BiomeBlend.Blended.of(72, 52, 0.4, Precipitation.RAIN);
        assertEquals(WeatherModel.effect(Precipitation.RAIN, rain, weather), WeatherModel.effect(plains, rain, weather),
                "a single biome gets exactly today's effect");
        assertEquals(WeatherModel.Effect.calm(weather),
                WeatherModel.effect(BiomeBlend.Blended.of(100, 78, 0, Precipitation.NONE), rain, weather));
        WeatherModel.Effect half = WeatherModel.effect(new BiomeBlend.Blended(86, 65, 0.2, 0.5, 0.0), rain, weather);
        assertEquals(WeatherModel.effect(Precipitation.RAIN, rain, weather).meanShiftF() / 2, half.meanShiftF(), 1e-9);
    }

    @Test
    void betweenCellsReadingsAreInterpolated() {
        BiomeBlend.Blended a = BiomeBlend.Blended.of(72, 52, 0.4, Precipitation.RAIN);
        BiomeBlend.Blended b = BiomeBlend.Blended.of(100, 78, 0.0, Precipitation.NONE);
        assertEquals(86.0, BiomeBlend.lerp(a, b, 0.5).noonF(), 1e-12);
        assertSame(a, BiomeBlend.lerp(a, b, 0.0));
        assertSame(b, BiomeBlend.lerp(a, b, 1.0));
    }
}
