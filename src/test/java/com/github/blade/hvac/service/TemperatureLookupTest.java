package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateVent;
import com.github.blade.hvac.model.GroupId;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The grid-based temperature lookup must pick exactly the system the old scan
 * over every airflow map picked, ties included.
 */
class TemperatureLookupTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final UUID OTHER = UUID.fromString("7d1e2f3a-4b5c-4d6e-8f70-112233445566");

    @TempDir Path folder;

    @Test
    void gridLookupMatchesTheFullScan() {
        HvacSettings settings = HvacSettings.defaults();
        int range = settings.airflowMaximumRange();
        for (long seed = 1; seed <= 20; seed++) {
            Random random = new Random(seed);
            HvacRegistry registry = new HvacRegistry(new TestHost(folder.resolve("r" + seed).toFile()), settings);
            AirflowService airflow = new AirflowService(registry, new OutdoorTemperatureService(), settings);

            // Systems clustered so their maps overlap, plus a few in another world.
            int groups = 12 + random.nextInt(12);
            List<BlockKey> probes = new ArrayList<>();
            for (int g = 0; g < groups; g++) {
                UUID world = g % 6 == 5 ? OTHER : WORLD;
                GroupId group = new GroupId(world, "g" + g);
                Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
                map.defaultReturnValue((byte) -1);
                int vents = 1 + random.nextInt(3);
                for (int v = 0; v < vents; v++) {
                    BlockKey vent = new BlockKey(world, random.nextInt(160) - 80, 64 + random.nextInt(6),
                            random.nextInt(160) - 80);
                    registry.addVent(new ClimateVent(vent, group));
                    // Map cells the way a rebuild would: within the range of a vent,
                    // distance growing with path length. Coarse distances force ties.
                    for (int cell = 0; cell < 400; cell++) {
                        int dx = random.nextInt(2 * range + 3) - range - 1;
                        int dy = random.nextInt(9) - 4;
                        int dz = random.nextInt(2 * range + 3) - range - 1;
                        int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (manhattan == 0 || manhattan > range + 1) continue;
                        long key = AirflowService.pack(vent.x() + dx, vent.y() + dy, vent.z() + dz);
                        byte distance = (byte) ((manhattan - 1) / 4 * 4);
                        byte existing = map.get(key);
                        if (existing == -1 || distance < existing) map.put(key, distance);
                        probes.add(new BlockKey(world, vent.x() + dx, vent.y() + dy, vent.z() + dz));
                    }
                }
                airflow.putCompletedMap(group, map);
            }
            for (int i = 0; i < 200; i++)   // and spots no system reaches
                probes.add(new BlockKey(WORLD, random.nextInt(400) - 200, 66, random.nextInt(400) - 200));

            for (BlockKey probe : probes) {
                long key = AirflowService.pack(probe.x(), probe.y(), probe.z());
                AirflowService.Nearest grid = airflow.nearestGroup(
                        airflow.candidateGroups(probe.worldId(), probe.x(), probe.z()), key, null);
                AirflowService.Nearest scan = airflow.nearestGroupByFullScan(probe.worldId(), key, null);
                assertEquals(scan, grid, "seed " + seed + " at " + probe);
                if (scan != null) {
                    GroupId required = scan.group();
                    assertEquals(airflow.nearestGroupByFullScan(probe.worldId(), key, required),
                            airflow.nearestGroup(airflow.candidateGroups(probe.worldId(), probe.x(), probe.z()),
                                    key, required), "seed " + seed + " required group at " + probe);
                }
            }
        }
    }

    @Test
    void packedPositionsUnpackForEvaporationSampling() {
        for (int[] p : new int[][] {{0, 0, 0}, {-1, -64, -1}, {12_345, 319, -98_765}, {-30_000_000, -2_048, 29_999_999}}) {
            long key = AirflowService.pack(p[0], p[1], p[2]);
            assertEquals(p[0], AirflowService.unpackX(key));
            assertEquals(p[1], AirflowService.unpackY(key));
            assertEquals(p[2], AirflowService.unpackZ(key));
        }
        assertEquals(0.05, new AirflowService.WaterExposure(500, new long[25]).wetFraction(), 1e-12);
        assertEquals(0.0, AirflowService.WaterExposure.NONE.wetFraction());
    }

    @Test
    void aHeatPumpsOwnWaterOutputIsNotAPool() {
        long output = AirflowService.pack(10, 64, 5);
        long pool = AirflowService.pack(3, 63, 3);
        AirflowService.WaterExposure exposure = new AirflowService.WaterExposure(400, new long[]{output, pool});
        AirflowService.WaterExposure real = exposure.excluding(WORLD, Set.of(new BlockKey(WORLD, 10, 64, 5)));
        assertArrayEquals(new long[]{pool}, real.waterCells());
        assertSame(exposure, exposure.excluding(WORLD, Set.of()), "nothing to exclude, nothing copied");
        assertSame(exposure, exposure.excluding(WORLD, Set.of(new BlockKey(OTHER, 10, 64, 5))), "an output in another world is not this one");
    }

    @Test
    void aWorldWithoutVentsHasNoCandidates() {
        HvacSettings settings = HvacSettings.defaults();
        HvacRegistry registry = new HvacRegistry(new TestHost(folder.toFile()), settings);
        AirflowService airflow = new AirflowService(registry, new OutdoorTemperatureService(), settings);
        assertTrue(airflow.candidateGroups(WORLD, 0, 0).isEmpty());
        assertNull(airflow.nearestGroup(airflow.candidateGroups(WORLD, 0, 0), AirflowService.pack(0, 64, 0), null));
    }
}
