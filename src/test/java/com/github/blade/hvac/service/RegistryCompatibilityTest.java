package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Upgrade compatibility: every hvac.yml a released EZvac ever wrote must load
 * with the same devices, and must save back exactly as its golden records.
 *
 * <p>Each release's fixtures were produced by running that release on a real
 * Purpur 1.21.1 server and building one of every device it supports.
 * {@code hvac.yml} is the file a clean shutdown leaves (equipment stopped,
 * outputs retracted); {@code hvac-running.yml} is the last periodic save
 * while units ran - what a crash leaves - so it carries owned outputs.
 *
 * <p>The {@code expected-*.yml} goldens are what EZvac saved for that input
 * when the fixture was added (1.6 HOTFIX 1 for every release up to and
 * including it). Regenerate them only on purpose, with
 * {@code mvn verify -Dezvac.recordGolden=true}, and review the diff.
 */
class RegistryCompatibilityTest {
    private static final Path FIXTURES = Path.of("src", "test", "resources", "compat");

    /** Per release: whether it had water systems, and whether it had pool thermostats. */
    static Stream<Arguments> releases() {
        return Stream.of(
                Arguments.of("ALPHA-1.0", false, false),
                Arguments.of("ALPHA-1.1-HOTFIX", false, false),
                Arguments.of("ALPHA-1.5", false, false),
                Arguments.of("ALPHA-1.6-P1", true, false),
                Arguments.of("ALPHA-1.6", true, true),
                Arguments.of("ALPHA-1.6-HOTFIX1", true, true),
                Arguments.of("ALPHA-1.7", true, true));
    }

    @TempDir Path folder;

    @ParameterizedTest(name = "{0} clean shutdown")
    @MethodSource("releases")
    void cleanShutdownFileLoadsEveryDevice(String release, boolean water, boolean pool) throws IOException {
        TestHost host = new TestHost(folder.toFile());
        HvacRegistry registry = load(host, release, "hvac.yml");

        assertCoreDevices(registry, water, pool);
        // A clean shutdown stops every unit and retracts its output first.
        assertEquals(0, registry.equipment().stream().filter(EquipmentUnit::running).count());
        assertEquals(0, registry.equipment().stream().filter(EquipmentUnit::hasOwnedOutput).count());
        assertTrue(host.warnings().isEmpty(), () -> "unexpected warnings: " + host.warnings());
        assertSavesAsGolden(registry, release, "expected-save.yml");
    }

    @ParameterizedTest(name = "{0} crash snapshot")
    @MethodSource("releases")
    void crashSnapshotKeepsRunningUnitsAndTheirOutputs(String release, boolean water, boolean pool)
            throws IOException {
        TestHost host = new TestHost(folder.toFile());
        HvacRegistry registry = load(host, release, "hvac-running.yml");

        assertCoreDevices(registry, water, pool);
        // Heat pump and AC cool their 60 F rooms; the boiler heats the pool.
        // The furnace shares the cooling house system, so it stays idle.
        int expectedRunning = water ? 3 : 2;
        assertEquals(expectedRunning, registry.equipment().stream().filter(EquipmentUnit::running).count());
        for (EquipmentUnit unit : registry.equipment()) {
            if (!unit.running()) continue;
            assertNotNull(unit.ownedOutput(), unit.type() + " lost its owned output");
            // Every unit faces east, so its output is the block in front of it.
            BlockKey expected = new BlockKey(unit.position().worldId(), unit.position().x() + 1,
                    unit.position().y(), unit.position().z());
            assertEquals(expected, unit.ownedOutput());
        }
        assertTrue(host.warnings().isEmpty(), () -> "unexpected warnings: " + host.warnings());
        assertSavesAsGolden(registry, release, "expected-running-save.yml");
    }

    @Test
    void preReleaseLegacyFieldsStillMigrate() throws IOException {
        TestHost host = new TestHost(folder.toFile());
        HvacRegistry registry = load(host, "legacy", "hvac.yml");
        UUID world = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");

        Thermostat thermostat = registry.thermostatById("lobby");
        assertNotNull(thermostat, "ids stay case-insensitive");
        assertEquals("Lobby", thermostat.id());
        assertEquals("lobby", thermostat.group().label());
        assertEquals(70.0, thermostat.targetF());
        assertEquals(75.0, thermostat.roomF());
        assertEquals(OperatingMode.COOLING, thermostat.mode());
        assertEquals(250, thermostat.ticksInMode());

        EquipmentUnit unit = registry.equipmentAt(new BlockKey(world, 14, 64, 10));
        assertNotNull(unit);
        assertEquals(EquipmentType.AIR_CONDITIONER, unit.type());
        assertEquals("lobby", unit.group().label());
        assertEquals(new BlockKey(world, 15, 64, 10), unit.ownedOutput());

        assertEquals(1, registry.pendingCleanup().size());
        assertEquals(new BlockKey(world, 21, 64, 10), registry.pendingCleanup().iterator().next().ownedOutput());
        assertEquals(1, registry.vents().size());
        Thermometer thermometer = registry.thermometers().iterator().next();
        assertEquals("outdoor_probe", thermometer.label(), "legacy group becomes the label");
        assertTrue(host.warnings().isEmpty(), () -> "unexpected warnings: " + host.warnings());
        assertSavesAsGolden(registry, "legacy", "expected-save.yml");
    }

    private static void assertCoreDevices(HvacRegistry registry, boolean water, boolean pool) {
        assertEquals(water ? 3 : 2, registry.thermostats().size());
        assertEquals(water ? 4 : 3, registry.equipment().size());
        assertEquals(water ? 4 : 3, registry.vents().size());
        assertEquals(1, registry.thermometers().size());
        assertEquals(1, registry.rpmMonitors().size());
        assertEquals(1, registry.settingsPanels().size());
        assertTrue(registry.pendingCleanup().isEmpty());

        Thermostat house = Objects.requireNonNull(registry.thermostatById("main"));
        assertEquals("house", house.group().label());
        assertEquals(60.0, house.targetF());
        assertEquals(2, registry.equipmentFor(house.group()).size());
        assertEquals(OperatingProfile.TURBO, registry.profileFor(house.group()));
        assertEquals("office", Objects.requireNonNull(registry.thermostatById("work")).group().label());

        if (water) {
            Thermostat spa = Objects.requireNonNull(registry.thermostatById("spa"));
            assertEquals("pool", spa.group().label());
            assertEquals(pool, spa.pool());
            // Pilot 1 pools used a room thermostat, which clamps to the 85 F ceiling.
            assertEquals(pool ? 104.0 : 85.0, spa.targetF());
            assertEquals(EquipmentType.BOILER, registry.equipmentFor(spa.group()).get(0).type());
        }
    }

    private HvacRegistry load(TestHost host, String release, String file) throws IOException {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/compat/" + release + "/" + file), () -> "missing fixture " + release + "/" + file)) {
            Files.copy(input, folder.resolve("hvac.yml"));
        }
        HvacRegistry registry = new HvacRegistry(host, HvacSettings.defaults());
        registry.load();
        return registry;
    }

    /** Saves the loaded registry and compares it with what 1.6 HOTFIX 1 wrote. */
    private void assertSavesAsGolden(HvacRegistry registry, String release, String golden) throws IOException {
        registry.markDirty();
        registry.save(true);
        String actual = normalize(Files.readString(folder.resolve("hvac.yml"), StandardCharsets.UTF_8));
        Path expectedPath = FIXTURES.resolve(release).resolve(golden);
        if (Boolean.getBoolean("ezvac.recordGolden")) {
            Files.writeString(expectedPath, actual, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.isRegularFile(expectedPath), "missing golden " + expectedPath);
        assertEquals(normalize(Files.readString(expectedPath, StandardCharsets.UTF_8)), actual,
                "saved hvac.yml changed for " + release + "/" + golden);
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }
}
