package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.SystemId;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Size-based heating (BETA 1.0): who owns which air, and where systems' air touches. */
class ShapeTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final SystemId A = new SystemId(WORLD, "a"), B = new SystemId(WORLD, "b");

    @TempDir Path folder;

    private AirflowService airflow() {
        HvacSettings settings = HvacSettings.defaults();
        return new AirflowService(new HvacRegistry(new TestHost(folder.toFile()), settings),
                new OutdoorTemperatureService(), settings);
    }

    /** A box of air, path distances growing from a vent at (ventX, 64, 0). */
    private static Long2ByteOpenHashMap room(int fromX, int toX, int ventX) {
        Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
        map.defaultReturnValue((byte) -1);
        for (int x = fromX; x <= toX; x++)
            for (int y = 64; y < 68; y++)
                for (int z = 0; z < 10; z++)
                    map.put(AirflowService.pack(x, y, z), (byte) (Math.abs(x - ventX) + Math.abs(y - 64) + z));
        return map;
    }

    @Test
    void aSystemAloneOwnsItsWholeMap() {
        AirflowService airflow = airflow();
        airflow.putMappedSystem(A, room(0, 9, 0), false, 2);
        airflow.settleShapes();
        AirflowService.Shape shape = airflow.shape(A);
        assertEquals(400, shape.owned());
        assertEquals(2, shape.openings());
        assertTrue(shape.contacts().isEmpty());
    }

    @Test
    void twoRoomsOpenToEachOtherSplitTheAirAndTouchAlongTheSeam() {
        AirflowService airflow = airflow();
        // Each walk floods the other room through the opening, as a real walk does.
        airflow.putMappedSystem(A, room(0, 19, 0), false, 0);
        airflow.putMappedSystem(B, room(0, 19, 19), false, 0);
        airflow.settleShapes();
        AirflowService.Shape a = airflow.shape(A), b = airflow.shape(B);
        assertEquals(800, a.owned() + b.owned(), "every block counted once");
        assertEquals(400, a.owned(), "each owns its half, nearer its vent");
        assertEquals(Map.of(B, 40), a.contacts(), "a 4 by 10 seam: 40 faces");
        assertEquals(Map.of(A, 40), b.contacts(), "the same faces from the other side");
    }

    @Test
    void theOrderTheyAreMappedInChangesNothing() {
        AirflowService first = airflow(), second = airflow();
        first.putMappedSystem(A, room(0, 19, 3), false, 0);
        first.putMappedSystem(B, room(0, 19, 14), false, 0);
        second.putMappedSystem(B, room(0, 19, 14), false, 0);
        second.putMappedSystem(A, room(0, 19, 3), false, 0);
        first.settleShapes();
        second.settleShapes();
        assertEquals(first.shape(A), second.shape(A));
        assertEquals(first.shape(B), second.shape(B));
    }

    @Test
    void roomsWithAWallBetweenThemDontTouch() {
        AirflowService airflow = airflow();
        airflow.putMappedSystem(A, room(0, 9, 0), false, 0);
        airflow.putMappedSystem(B, room(11, 20, 20), false, 0); // x = 10 is the wall
        airflow.settleShapes();
        assertTrue(airflow.shape(A).contacts().isEmpty());
        assertEquals(400, airflow.shape(B).owned());
    }

    @Test
    void airAndWaterNeverTouch() {
        AirflowService airflow = airflow();
        airflow.putMappedSystem(A, room(0, 9, 0), false, 0);
        airflow.putMappedSystem(B, room(10, 19, 19), true, 0);
        airflow.settleShapes();
        assertTrue(airflow.shape(A).contacts().isEmpty(), "a pool beside a room trades nothing with its air");
        assertTrue(airflow.shape(B).water());
    }

    @Test
    void aRemovedNeighbourGivesItsAirBack() {
        AirflowService airflow = airflow();
        airflow.putMappedSystem(A, room(0, 19, 0), false, 0);
        airflow.putMappedSystem(B, room(0, 19, 19), false, 0);
        airflow.settleShapes();
        airflow.forget(B);
        airflow.settleShapes();
        assertEquals(800, airflow.shape(A).owned());
        assertTrue(airflow.shape(A).contacts().isEmpty());
    }
}
