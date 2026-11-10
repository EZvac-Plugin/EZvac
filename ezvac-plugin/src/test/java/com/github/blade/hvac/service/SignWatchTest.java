package com.github.blade.hvac.service;

import com.github.blade.hvac.model.BlockKey;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SignWatchTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");

    @Test
    void signsWithin64BlocksOfAPlayerAreWatched() {
        LongOpenHashSet chunks = new LongOpenHashSet();
        DisplayService.watchAround(chunks, 0, 0);   // a player standing in chunk 0,0
        Map<UUID, LongOpenHashSet> watched = Map.of(WORLD, chunks);
        assertTrue(DisplayService.watched(watched, new BlockKey(WORLD, 8, 64, 8)));
        assertTrue(DisplayService.watched(watched, new BlockKey(WORLD, 64, 64, -64)), "64 blocks away");
        assertTrue(DisplayService.watched(watched, new BlockKey(WORLD, -64, 64, 79)));
        assertFalse(DisplayService.watched(watched, new BlockKey(WORLD, 100, 64, 0)), "100 blocks away");
        assertFalse(DisplayService.watched(watched, new BlockKey(UUID.randomUUID(), 8, 64, 8)), "another world");
    }

    @Test
    void negativeChunksAreWatchedToo() {
        LongOpenHashSet chunks = new LongOpenHashSet();
        DisplayService.watchAround(chunks, -10, -10);
        Map<UUID, LongOpenHashSet> watched = Map.of(WORLD, chunks);
        assertTrue(DisplayService.watched(watched, new BlockKey(WORLD, -160, 64, -160)));
        assertTrue(DisplayService.watched(watched, new BlockKey(WORLD, -97, 64, -224)));
        assertFalse(DisplayService.watched(watched, new BlockKey(WORLD, -60, 64, -160)));
    }
}
