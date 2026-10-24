package com.github.blade.hvac.service;

import com.github.blade.hvac.model.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VentSoundTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");

    @Test
    void aRunOfVentsSoundsLikeOneStreamOfAir() {
        List<BlockKey> duct = List.of(new BlockKey(WORLD, 0, 70, 0), new BlockKey(WORLD, 3, 70, 0),
                new BlockKey(WORLD, 6, 70, 0), new BlockKey(WORLD, 9, 70, 0), new BlockKey(WORLD, 20, 70, 0));
        assertEquals(List.of(new BlockKey(WORLD, 0, 70, 0), new BlockKey(WORLD, 9, 70, 0), new BlockKey(WORLD, 20, 70, 0)),
                HvacAudioService.onePerCluster(duct, HvacAudioService.VENT_CLUSTER_BLOCKS));
        BlockKey otherWorld = new BlockKey(UUID.randomUUID(), 1, 70, 0);
        assertEquals(2, HvacAudioService.onePerCluster(List.of(duct.get(0), otherWorld), 6).size(),
                "vents in different worlds never share a sound");
        assertTrue(HvacAudioService.onePerCluster(List.of(), 6).isEmpty());
    }
}
