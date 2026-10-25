package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The registry tells the API about devices added and removed while running (API 1.2). */
class RegistryObserverTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");

    @TempDir Path folder;
    private HvacRegistry registry;
    private final List<String> seen = new ArrayList<>();

    private static BlockKey at(int x, int y, int z) {
        return new BlockKey(WORLD, x, y, z);
    }

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(folder.resolve("hvac.yml"), """
                meta:
                  format: 6
                thermometers:
                  item0:
                    worldUuid: %s
                    x: 9
                    y: 64
                    z: 9
                    label: Hall
                """.formatted(WORLD), StandardCharsets.UTF_8);
        registry = new HvacRegistry(new TestHost(folder.toFile()), HvacSettings.defaults());
        registry.setObserver(new HvacRegistry.DeviceObserver() {
            @Override public void added(Object device) { seen.add("added " + name(device)); }
            @Override public void removed(Object device, Cause cause) { seen.add("removed " + name(device) + " " + cause); }
        });
        registry.load();
    }

    private static String name(Object device) {
        return device.getClass().getSimpleName();
    }

    @Test
    void loadingHvacYmlIsNotAnAddition() {
        assertEquals(1, registry.thermometers().size(), "the saved thermometer is back");
        assertEquals(List.of(), seen);
    }

    @Test
    void addingAndRemovingAreReportedWithTheirCause() {
        registry.addSensor(new Sensor(at(1, 64, 0), "Porch", Sensor.Kind.TEMPERATURE));
        registry.addSensorHub(new SensorHub(at(2, 64, 0), "Hub"));
        registry.addThermometer(new Thermometer(at(3, 64, 0), "Den", Thermometer.Reads.BOTH));
        registry.removeThermometer(at(3, 64, 0));
        registry.removingByCommand(() -> registry.removeSensorHub(at(2, 64, 0)));
        registry.removeSensor(at(1, 64, 0));
        assertEquals(List.of("added Sensor", "added SensorHub", "added Thermometer",
                "removed Thermometer BROKEN", "removed SensorHub COMMAND", "removed Sensor BROKEN"), seen);
    }

    @Test
    void relinkingAVentMovesItToAnotherSystem() {
        registry.addVent(new ClimateVent(at(5, 64, 0), GroupId.of(at(5, 64, 0), "house")));
        registry.addVent(new ClimateVent(at(5, 64, 0), GroupId.of(at(5, 64, 0), "garage")));
        assertEquals(List.of("added ClimateVent", "removed ClimateVent COMMAND", "added ClimateVent"), seen);
    }

    @Test
    void removingNothingTellsNobody() {
        assertNull(registry.removeSensor(at(40, 64, 0)));
        assertFalse(registry.removeThermometer(at(40, 64, 0)));
        assertEquals(List.of(), seen);
    }
}
