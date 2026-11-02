package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mini thermostats (BETA 1.0 Pilot 2): a thermostat on a wall button with a
 * small display. What it shows, that it fits, where it goes, what it saves,
 * and that a button is a mini thermostat or a sensor, never both.
 */
class MiniThermostatTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    @TempDir Path folder;

    private static MiniScreen screen(TemperatureUnit units, double roomF, double targetF,
                                     OperatingMode mode, double progress, int attention, String blocked) {
        return MiniScreen.of(units, roomF, targetF, mode, true, progress, attention, blocked);
    }

    @Test
    void itShowsOnlyTheRoomTheTargetAndHowFarItHasCome() {
        assertEquals("71°\nSet 75°\n" + "|".repeat(16), screen(TemperatureUnit.F, 71.3, 75.0,
                OperatingMode.HEATING, 0.5, 0, "").plain(), "whole degrees, thin bars, half of them lit");
        assertEquals("71°\nSet 72°\n●", screen(TemperatureUnit.F, 71.3, 72.0,
                OperatingMode.IDLE, Double.NaN, 0, "").plain(), "holding: a quiet dot, no gauge");
        assertEquals("22°\nSet 22°\n●", screen(TemperatureUnit.C, 71.3, 72.0,
                OperatingMode.IDLE, Double.NaN, 0, "").plain(), "in the server's unit, without its letter");
    }

    @Test
    void whatNeedsAttentionIsCountedAndWhatStopsItSaid() {
        assertEquals("71°\nSet 72° ⚠2\nNo vent", screen(TemperatureUnit.F, 71.3, 72.0,
                OperatingMode.IDLE, Double.NaN, 2, "No vent").plain());
        assertEquals("No unit", DisplayService.miniBlocked(java.util.EnumSet.of(Attention.NO_UNITS,
                Attention.NO_VENTS), false), "the first thing to fix");
        assertEquals("Units off", DisplayService.miniBlocked(java.util.EnumSet.of(Attention.UNITS_OFF), true));
        assertEquals("", DisplayService.miniBlocked(java.util.EnumSet.of(Attention.UNDERSIZED), false));
        assertEquals("71°\nSet 72°\nOff", MiniScreen.of(TemperatureUnit.F, 71.3, 72,
                OperatingMode.IDLE, false, Double.NaN, 0, "").plain());
    }

    @Test
    void everythingFitsItsSmallPanelWhateverTheTemperature() {
        for (TemperatureUnit units : TemperatureUnit.values())
            for (double f = -40; f <= 140; f += 0.7)
                for (int attention : new int[] {0, 1, 4, 9})
                    for (String blocked : new String[] {"", "No unit", "No vent", "Units off"}) {
                        // Targets run from a room's lowest to a pool's highest, 104 F.
                        MiniScreen mini = screen(units, f, 40 + (f + 40) % 65, OperatingMode.COOLING, 0.4,
                                attention, blocked);
                        for (int width : mini.widths()) assertTrue(width <= MiniScreen.WIDTH, mini.plain());
                        String big = PlainTextComponentSerializer.plainText().serialize(mini.bigText());
                        assertTrue(2 * MiniScreen.boldWidth(big) <= MiniScreen.LINE_WIDTH,
                                "the big reading, bold at twice the size: " + big);
                        assertTrue(mini.plain().split("\n")[1].endsWith(attention > 0 ? "⚠" + attention : "°"),
                                "the count is never cut: " + mini.plain());
                    }
    }

    @Test
    void theGaugeFillsFromWhereTheRunStartedToTheTarget() {
        assertEquals(0.0, Thermostat.progress(68, 68, 72), 1e-9);
        assertEquals(0.5, Thermostat.progress(68, 70, 72), 1e-9);
        assertEquals(0.75, Thermostat.progress(80, 74, 72), 1e-9, "cooling counts down the same way");
        assertEquals(1.0, Thermostat.progress(68, 73, 72), 1e-9, "past the target: full");
        assertEquals(0.0, Thermostat.progress(68, 66, 72), 1e-9, "fallen behind: empty");
        Thermostat thermostat = new Thermostat(new BlockKey(WORLD, 0, 64, 0), "Hall", new SystemId(WORLD, "#1"),
                72, 68, SETTINGS, false);
        assertTrue(Double.isNaN(thermostat.progress()), "idle: no gauge");
        thermostat.setControlState(OperatingMode.HEATING, 0);
        assertEquals(0.0, thermostat.progress(), 1e-9, "a run starts where the room is");
        thermostat.setRoomF(70);
        assertEquals(0.5, thermostat.progress(), 1e-9);
    }

    @Test
    void itSitsJustInFrontOfItsButtonsFaceWhicheverWayItFaces() {
        // A wall button faces away from its wall; its face is 2 pixels off the wall, so 0.375 from the middle.
        for (int[] facing : new int[][] {{0, -1}, {0, 1}, {1, 0}, {-1, 0}}) {
            ScreenPlacement.Layout layout = ScreenPlacement.mini(facing[0], facing[1], MiniScreen.BODY_LINES);
            assertNull(layout.header(), "no header");
            double front = facing[0] != 0 ? (layout.body().x() - 0.5) * facing[0] : (layout.body().z() - 0.5) * facing[1];
            assertTrue(front < -0.36 + 1e-9 && front > -0.375, "just in front of the face: " + front);
            assertEquals(0.5, facing[0] != 0 ? layout.body().z() : layout.body().x(), 1e-9, "centred on it");
            assertTrue(layout.big().y() > layout.body().y(), "the big reading over the panel's top");
        }
        double height = ScreenPlacement.height(ScreenPlacement.Mount.BUTTON, MiniScreen.BODY_LINES);
        double width = MiniScreen.LINE_WIDTH * ScreenPlacement.Mount.BUTTON.scale() / 40.0;
        assertTrue(width > 0.375 && width < 0.5 && height > 0.25 && height < 0.35,
                "a little bigger than the 6 by 4 pixel button: " + width + " by " + height);
    }

    @Test
    void aMiniThermostatSavesThatItIsOneAndNothingElseChanges() {
        Thermostat mini = new Thermostat(new BlockKey(WORLD, 0, 64, 0), "Hall", new SystemId(WORLD, "#1"),
                72, 70, SETTINGS, false);
        assertFalse(mini.toMap().containsKey("mini"), "a sign thermostat saves as before");
        mini.setMini(true);
        assertEquals(true, mini.toMap().get("mini"));
        assertTrue(Thermostat.fromMap(mini.toMap(), SETTINGS).mini());
    }

    @Test
    void aButtonIsAMiniThermostatOrASensorNeverBoth() {
        HvacRegistry registry = new HvacRegistry(new TestHost(folder.toFile()), SETTINGS);
        BlockKey button = new BlockKey(WORLD, 4, 64, 0), other = new BlockKey(WORLD, 8, 64, 0);
        Thermostat mini = new Thermostat(button, "Hall", registry.newSystem(WORLD), 72, 70, SETTINGS, false);
        mini.setMini(true);
        registry.addThermostat(mini);
        assertThrows(IllegalArgumentException.class,
                () -> registry.addSensor(new Sensor(button, "Probe", Sensor.Kind.TEMPERATURE)),
                "a sensor can't be made on a mini thermostat's button");
        registry.addSensor(new Sensor(other, "Probe", Sensor.Kind.TEMPERATURE));
        Thermostat second = new Thermostat(other, "Den", registry.newSystem(WORLD), 72, 70, SETTINGS, false);
        second.setMini(true);
        assertThrows(IllegalArgumentException.class, () -> registry.addThermostat(second),
                "nor a mini thermostat on a sensor's button");
        assertSame(mini, registry.thermostatAt(button));
        assertNull(registry.thermostatAt(other));
    }

    @Test
    void threeDigitTemperaturesAreShownWhole() {
        // The maintainer's check before Pilot 2: the Nether (112 °F, the basalt deltas 120) and a 104 °F pool.
        for (double room : new double[] {99.6, 104.0, 112.0, 120.0, 130.4})
            for (double target : new double[] {60.0, 85.0, 104.0})
                for (int attention : new int[] {0, 3, 9}) {
                    MiniScreen mini = screen(TemperatureUnit.F, room, target, OperatingMode.COOLING, 0.3, attention, "");
                    String[] lines = mini.plain().split("\n");
                    assertEquals(MiniScreen.degrees(TemperatureUnit.F, room), lines[0], "the room, whole");
                    assertTrue(lines[1].contains(MiniScreen.degrees(TemperatureUnit.F, target)),
                            "the target, whole: " + lines[1]);
                    String big = PlainTextComponentSerializer.plainText().serialize(mini.bigText());
                    assertTrue(2 * MiniScreen.boldWidth(big) <= MiniScreen.LINE_WIDTH, big);
                    for (int width : mini.widths()) assertTrue(width <= MiniScreen.WIDTH, mini.plain());
                }
        assertEquals("Set 104°", screen(TemperatureUnit.F, 104, 104, OperatingMode.IDLE, Double.NaN, 0, "")
                .plain().split("\n")[1]);
        assertEquals("104° ⚠9", screen(TemperatureUnit.F, 104, 104, OperatingMode.IDLE, Double.NaN, 9, "")
                .plain().split("\n")[1], "with a count it gives up its Set, never a digit");
    }
}
