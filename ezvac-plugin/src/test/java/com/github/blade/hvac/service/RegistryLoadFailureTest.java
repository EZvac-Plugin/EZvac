package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A save file that cannot be read must never be overwritten. Before 1.7
 * HOTFIX 1, an unreadable hvac.yml with no usable backup was replaced by an
 * empty save on shutdown, and the next run's save rotated the original out
 * of hvac.yml.bak too, so a single typo could lose every device.
 */
class RegistryLoadFailureTest {
    private static final String VALID = """
            meta:
              format: 5
            thermostats:
              item0:
                id: main
                group: house
                worldUuid: 0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10
                x: 1
                y: 64
                z: 1
                targetF: 70.0
            """;
    private static final String BROKEN = VALID + "  broken: [unclosed\n";

    @TempDir Path folder;
    private TestHost host;
    private Path data;
    private Path backup;

    @BeforeEach
    void setUp() {
        host = new TestHost(folder.toFile());
        data = folder.resolve("hvac.yml");
        backup = folder.resolve("hvac.yml.bak");
    }

    private HvacRegistry registry() {
        return new HvacRegistry(host, HvacSettings.defaults());
    }

    /** Exactly what HvacPlugin.onDisable does after a failed enable. */
    private static void shutDown(HvacRegistry registry) {
        registry.shutdownEquipment();
        registry.save(true);
    }

    private List<String> files() throws IOException {
        try (var stream = Files.list(folder)) {
            return stream.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void anUnreadableFileWithNoBackupIsLeftUntouchedAcrossRestarts() throws IOException {
        Files.writeString(data, BROKEN, StandardCharsets.UTF_8);

        // First start: refused, then the shutdown save must write nothing.
        HvacRegistry first = registry();
        IllegalStateException refused = assertThrows(IllegalStateException.class, first::load);
        assertTrue(refused.getMessage().contains("left untouched"));
        assertFalse(first.isWritable());
        shutDown(first);
        assertEquals(BROKEN, Files.readString(data, StandardCharsets.UTF_8));
        assertEquals(List.of("hvac.yml"), files(), "no backup rotation, no empty save");

        // Second start: still refused, because the file was never replaced with an empty one.
        HvacRegistry second = registry();
        assertThrows(IllegalStateException.class, second::load);
        second.markDirty();
        second.save(false);
        shutDown(second);
        assertEquals(BROKEN, Files.readString(data, StandardCharsets.UTF_8));
    }

    @Test
    void anUnreadableFileAndBackupAreBothLeftUntouched() throws IOException {
        Files.writeString(data, BROKEN, StandardCharsets.UTF_8);
        Files.writeString(backup, "also: [broken\n", StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        IllegalStateException refused = assertThrows(IllegalStateException.class, registry::load);
        assertTrue(refused.getMessage().contains("Both hvac.yml and hvac.yml.bak are unreadable"));
        shutDown(registry);
        assertEquals(BROKEN, Files.readString(data, StandardCharsets.UTF_8));
        assertEquals("also: [broken\n", Files.readString(backup, StandardCharsets.UTF_8));
        assertEquals(List.of("hvac.yml", "hvac.yml.bak"), files());
    }

    @Test
    void recoveringFromTheBackupKeepsTheUnreadableFileAside() throws IOException {
        Files.writeString(data, BROKEN, StandardCharsets.UTF_8);
        Files.writeString(backup, VALID, StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        registry.load();

        assertNotNull(TestHost.labelled(registry, "main"), "loaded from the backup");
        assertTrue(registry.isWritable());
        List<String> files = files();
        String kept = files.stream().filter(name -> name.startsWith("hvac.yml.unreadable-")).findFirst()
                .orElseThrow(() -> new AssertionError("unreadable original was not kept: " + files));
        assertEquals(BROKEN, Files.readString(folder.resolve(kept), StandardCharsets.UTF_8));
        assertTrue(host.warnings().stream().anyMatch(message -> message.contains(kept)),
                "the log names the kept file");

        // Normal saving resumes, and the kept original is never touched by it.
        registry.markDirty();
        registry.save(true);
        assertEquals(BROKEN, Files.readString(folder.resolve(kept), StandardCharsets.UTF_8));
    }

    @Test
    void aFixedFileLoadsAndSavesNormallyAgain() throws IOException {
        Files.writeString(data, BROKEN, StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        assertThrows(IllegalStateException.class, registry::load);

        Files.writeString(data, VALID, StandardCharsets.UTF_8);
        registry.load();
        assertTrue(registry.isWritable());
        assertNotNull(TestHost.labelled(registry, "main"));
        registry.markDirty();
        registry.save(true);
        assertTrue(Files.exists(backup), "saving works again once the file is readable");
    }

    @Test
    void aMissingFileStillStartsEmptyAndSaves() throws IOException {
        HvacRegistry registry = registry();
        registry.load();
        assertTrue(registry.isWritable());
        registry.markDirty();
        registry.save(true);
        assertTrue(Files.exists(data), "a brand-new install creates its save file");
    }
}
