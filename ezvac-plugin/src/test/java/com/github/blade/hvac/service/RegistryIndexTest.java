package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the registry through long random sequences of mutations and, after
 * every step, checks that (a) its indexes match a from-scratch rebuild and
 * (b) every indexed lookup answers exactly what the old full-scan code did.
 */
class RegistryIndexTest {
    private static final UUID WORLD_A = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final UUID WORLD_B = UUID.fromString("7d1e2f3a-4b5c-4d6e-8f70-112233445566");
    private static final String[] LABELS = {"house", "office", "pool", "barn", "lab"};
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    @TempDir Path folder;

    @Test
    void indexesStayConsistentThroughRandomMutations() {
        for (long seed = 1; seed <= 25; seed++) runSequence(seed, 400);
    }

    private void runSequence(long seed, int steps) {
        Random random = new Random(seed);
        HvacRegistry registry = new HvacRegistry(new TestHost(folder.resolve("s" + seed).toFile()), SETTINGS);
        for (int step = 0; step < steps; step++) {
            String operation = mutate(registry, random);
            String context = "seed " + seed + " step " + step + " after " + operation;
            assertNull(registry.indexDiscrepancy(), () -> context + ": index differs from a rebuild");
            assertLookupsMatchFullScan(registry, random, context);
            if (step % 97 == 96) {
                // A save and reload must rebuild exactly the same view.
                registry.markDirty();
                registry.save(true);
                Map<String, Object> before = snapshot(registry);
                registry.load();
                assertNull(registry.indexDiscrepancy(), context + ": after reload");
                assertEquals(before, snapshot(registry), context + ": reload changed lookups");
            }
        }
    }

    private static BlockKey randomPosition(Random random) {
        UUID world = random.nextInt(5) == 0 ? WORLD_B : WORLD_A;
        // Small ranges so positions collide, but wide enough to span several grid cells.
        return new BlockKey(world, random.nextInt(400) - 200, 60 + random.nextInt(4), random.nextInt(400) - 200);
    }

    private static String label(Random random) {
        return LABELS[random.nextInt(LABELS.length)];
    }

    private static <T> T pick(Collection<T> values, Random random) {
        if (values.isEmpty()) return null;
        int index = random.nextInt(values.size());
        Iterator<T> iterator = values.iterator();
        for (int i = 0; i < index; i++) iterator.next();
        return iterator.next();
    }

    /** Equipment that owns an output, built the way load() builds it. */
    private static EquipmentUnit unitWithOutput(BlockKey position, String system, EquipmentType type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("worldUuid", position.worldId().toString());
        map.put("x", position.x()); map.put("y", position.y()); map.put("z", position.z());
        map.put("group", system);
        map.put("equipmentType", type.name());
        map.put("running", true);
        map.put("operatingMode", "HEATING");
        map.put("outputWorldUuid", position.worldId().toString());
        map.put("outputX", position.x() + 1); map.put("outputY", position.y()); map.put("outputZ", position.z());
        return EquipmentUnit.fromMap(map);
    }

    private static String mutate(HvacRegistry registry, Random random) {
        int choice = random.nextInt(14);
        try {
            switch (choice) {
                case 0 -> registry.addThermostat(new Thermostat(randomPosition(random),
                        "t" + random.nextInt(30), label(random), 70, 70, SETTINGS));
                case 1 -> {
                    Thermostat value = pick(registry.thermostats(), random);
                    if (value != null) registry.removeThermostat(value.position());
                }
                case 2 -> registry.addEquipment(new EquipmentUnit(randomPosition(random), label(random),
                        EquipmentType.values()[random.nextInt(EquipmentType.values().length)]));
                case 3 -> registry.addEquipment(unitWithOutput(randomPosition(random), label(random),
                        EquipmentType.FURNACE));
                case 4 -> {
                    EquipmentUnit value = pick(registry.equipment(), random);
                    // Offline the output cannot be retracted, so owners move to pending cleanup.
                    if (value != null) registry.removeEquipment(value.position());
                }
                case 5 -> {
                    EquipmentUnit value = pick(registry.equipment(), random);
                    if (value != null) registry.regroupEquipment(value, label(random));
                }
                case 6 -> {
                    Thermostat value = pick(registry.thermostats(), random);
                    String label = label(random);
                    // Mirrors /hvac thermostat <id> group: a group never gets a second controller.
                    Thermostat existing = value == null ? null
                            : registry.thermostatFor(SystemId.of(value.position(), label));
                    if (value != null && (existing == null || existing == value))
                        registry.regroupThermostat(value, label);
                }
                case 7 -> {
                    BlockKey position = random.nextBoolean() && !registry.vents().isEmpty()
                            ? pick(registry.vents(), random).position() : randomPosition(random);
                    // Re-adding at an existing vent's block relinks it, as the duct tool does.
                    registry.addVent(new ClimateVent(position, new SystemId(position.worldId(), label(random))));
                }
                case 8 -> {
                    ClimateVent value = pick(registry.vents(), random);
                    if (value != null) registry.removeVent(value.position());
                }
                case 9 -> registry.addSettingsPanel(new SettingsPanel(randomPosition(random), label(random), "panel"));
                case 10 -> {
                    SettingsPanel value = pick(registry.settingsPanels(), random);
                    if (value != null) registry.removeSettingsPanel(value.position());
                }
                case 11 -> {
                    List<BlockKey> owned = new ArrayList<>(registry.ownedOutputs());
                    BlockKey key = owned.isEmpty() || random.nextInt(4) == 0
                            ? randomPosition(random) : owned.get(random.nextInt(owned.size()));
                    registry.releaseOutputOwnership(key);
                }
                case 12 -> registry.reconcilePendingCleanup();
                default -> registry.shutdownEquipment();
            }
        } catch (IllegalArgumentException refused) {
            // Occupied block, duplicate id or group: refused before any change, which is fine.
        }
        return "operation " + choice;
    }

    private static void assertLookupsMatchFullScan(HvacRegistry registry, Random random, String context) {
        Set<SystemId> systems = new HashSet<>();
        for (UUID world : List.of(WORLD_A, WORLD_B))
            for (String label : LABELS) systems.add(new SystemId(world, label));
        for (SystemId system : systems) {
            Thermostat expected = registry.thermostats().stream().filter(value -> value.system().equals(system))
                    .min(Comparator.comparing(Thermostat::position)).orElse(null);
            assertSame(expected, registry.thermostatFor(system), context + ": thermostatFor " + system);
            List<EquipmentUnit> units = registry.equipment().stream().filter(unit -> unit.system().equals(system))
                    .sorted(Comparator.comparing(EquipmentUnit::position)).toList();
            assertEquals(units, registry.equipmentFor(system), context + ": equipmentFor " + system);
            SettingsPanel panel = registry.settingsPanels().stream().filter(value -> value.system().equals(system))
                    .min(Comparator.comparing(SettingsPanel::position)).orElse(null);
            assertSame(panel, registry.settingsPanelFor(system), context + ": settingsPanelFor " + system);
            List<ClimateVent> vents = registry.vents().stream().filter(vent -> vent.system().equals(system))
                    .sorted(Comparator.comparing(ClimateVent::position)).toList();
            assertEquals(vents, List.copyOf(registry.ventsFor(system)), context + ": ventsFor " + system);
            for (boolean water : new boolean[]{false, true}) {
                boolean loop = registry.equipment().stream()
                        .anyMatch(unit -> unit.system().equals(system) && unit.type().waterLoop() == water);
                assertEquals(loop, registry.hasEquipmentOfLoop(system, water), context + ": hasEquipmentOfLoop");
            }
        }
        for (int id = 0; id < 30; id++) {
            String name = (id % 2 == 0 ? "t" : "T") + id;
            Thermostat expected = registry.thermostats().stream().filter(value -> value.id().equalsIgnoreCase(name))
                    .min(Comparator.comparing(Thermostat::position)).orElse(null);
            assertSame(expected, registry.thermostatById(name), context + ": thermostatById " + name);
        }
        Set<BlockKey> outputs = new HashSet<>();
        for (EquipmentUnit unit : registry.equipment()) if (unit.ownedOutput() != null) outputs.add(unit.ownedOutput());
        for (EquipmentUnit unit : registry.pendingCleanup()) if (unit.ownedOutput() != null) outputs.add(unit.ownedOutput());
        assertEquals(outputs, registry.ownedOutputs(), context + ": ownedOutputs");

        // The grid search AirflowService runs must find exactly the vents a full scan finds.
        int radius = SETTINGS.airflowMaximumRange() + 2;
        int shift = registry.ventCellShift();
        for (int probe = 0; probe < 5; probe++) {
            BlockKey point = randomPosition(random);
            Set<ClimateVent> expected = new HashSet<>();
            for (ClimateVent vent : registry.vents()) {
                BlockKey position = vent.position();
                if (!position.worldId().equals(point.worldId())) continue;
                long dx = position.x() - point.x(), dy = position.y() - point.y(), dz = position.z() - point.z();
                if (dx * dx + dy * dy + dz * dz <= (long) radius * radius) expected.add(vent);
            }
            Set<ClimateVent> found = new HashSet<>();
            var cells = registry.ventCells(point.worldId());
            if (cells != null)
                for (int cellX = (point.x() - radius) >> shift; cellX <= (point.x() + radius) >> shift; cellX++)
                    for (int cellZ = (point.z() - radius) >> shift; cellZ <= (point.z() + radius) >> shift; cellZ++) {
                        List<ClimateVent> bucket = cells.get(HvacRegistry.cellKey(cellX, cellZ));
                        if (bucket == null) continue;
                        for (ClimateVent vent : bucket) {
                            BlockKey position = vent.position();
                            long dx = position.x() - point.x(), dy = position.y() - point.y(), dz = position.z() - point.z();
                            if (dx * dx + dy * dy + dz * dz <= (long) radius * radius) found.add(vent);
                        }
                    }
            assertEquals(expected, found, context + ": grid search near " + point);
        }
    }

    /** Lookup answers, by value, for comparing across a save/load cycle. */
    private static Map<String, Object> snapshot(HvacRegistry registry) {
        Map<String, Object> view = new TreeMap<>();
        for (UUID world : List.of(WORLD_A, WORLD_B))
            for (String label : LABELS) {
                SystemId system = new SystemId(world, label);
                Thermostat controller = registry.thermostatFor(system);
                view.put(system + " thermostat", controller == null ? null : controller.position());
                view.put(system + " equipment", registry.equipmentFor(system).stream().map(EquipmentUnit::position).toList());
                view.put(system + " vents", List.copyOf(registry.ventsFor(system)));
                SettingsPanel panel = registry.settingsPanelFor(system);
                view.put(system + " panel", panel == null ? null : panel.position());
            }
        view.put("outputs", new TreeSet<>(registry.ownedOutputs()));
        view.put("pending", registry.pendingCleanup().stream().map(EquipmentUnit::position).sorted().toList());
        return view;
    }
}
