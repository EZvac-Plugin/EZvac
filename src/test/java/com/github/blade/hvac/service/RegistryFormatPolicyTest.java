package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** EZvac supports upgrades only: newer files are refused untouched, older ones are backed up. */
class RegistryFormatPolicyTest {
    private static final String WORLD = "0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10";

    @TempDir Path folder;
    private TestHost host;
    private Path data;

    @BeforeEach
    void setUp() {
        host = new TestHost(folder.toFile());
        data = folder.resolve("hvac.yml");
    }

    private static String file(String meta) {
        return meta + """
                thermostats:
                  item0:
                    id: main
                    group: house
                    worldUuid: %s
                    x: 1
                    y: 64
                    z: 1
                    targetF: 70.0
                """.formatted(WORLD);
    }

    private HvacRegistry registry() {
        return new HvacRegistry(host, HvacSettings.defaults());
    }

    @Test
    void aNewerFormatIsRefusedAndNeverOverwrittenEvenByTheShutdownSave() throws IOException {
        String newer = file("meta:\n  format: " + (HvacRegistry.FORMAT_VERSION + 1) + "\n")
                + "somethingNew:\n  item0:\n    value: 1\n";
        Files.writeString(data, newer, StandardCharsets.UTF_8);
        HvacRegistry registry = registry();

        IllegalStateException refused = assertThrows(IllegalStateException.class, registry::load);
        assertTrue(refused.getMessage().contains("Downgrading EZvac is not supported"));

        // Exactly what HvacPlugin.onDisable does after a failed enable.
        registry.shutdownEquipment();
        registry.save(true);
        registry.markDirty();
        registry.save(false);
        assertEquals(newer, Files.readString(data, StandardCharsets.UTF_8));
        assertFalse(Files.exists(folder.resolve("hvac.yml.bak")), "no backup rotation either");
    }

    @Test
    void anOlderFormatIsBackedUpOnceBeforeItsFirstSave() throws IOException {
        String older = file("meta:\n  format: 4\n");
        Files.writeString(data, older, StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        registry.load();
        assertNotNull(registry.thermostatById("main"));
        Path backup = folder.resolve("hvac.yml.format4.bak");
        assertFalse(Files.exists(backup), "loading alone writes nothing");

        registry.markDirty();
        registry.save(true);
        assertEquals(older, Files.readString(backup, StandardCharsets.UTF_8));
        assertTrue(Files.readString(data, StandardCharsets.UTF_8)
                .contains("format: " + HvacRegistry.FORMAT_VERSION));

        // Later saves never replace the original backup.
        registry.markDirty();
        registry.save(true);
        assertEquals(older, Files.readString(backup, StandardCharsets.UTF_8));
    }

    @Test
    void anExistingUpgradeBackupIsNeverReplaced() throws IOException {
        Path backup = folder.resolve("hvac.yml.format4.bak");
        Files.writeString(backup, "the very first original", StandardCharsets.UTF_8);
        Files.writeString(data, file("meta:\n  format: 4\n"), StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        registry.load();
        registry.markDirty();
        registry.save(true);
        assertEquals("the very first original", Files.readString(backup, StandardCharsets.UTF_8));
    }

    @Test
    void theCurrentFormatAndAMissingFormatLoadWithoutABackup() throws IOException {
        for (String meta : new String[]{"meta:\n  format: " + HvacRegistry.FORMAT_VERSION + "\n", ""}) {
            Files.writeString(data, file(meta), StandardCharsets.UTF_8);
            HvacRegistry registry = registry();
            registry.load();
            assertNotNull(registry.thermostatById("main"));
            registry.markDirty();
            registry.save(true);
            try (var files = Files.list(folder)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith("hvac.yml.format")),
                        "no upgrade backup for meta '" + meta.trim() + "'");
            }
        }
        assertTrue(host.warnings().isEmpty(), () -> "unexpected warnings: " + host.warnings());
    }

    @Test
    void aRefusedRegistryBecomesWritableAgainAfterASuccessfulReload() throws IOException {
        Files.writeString(data, file("meta:\n  format: 99\n"), StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        assertThrows(IllegalStateException.class, registry::load);

        Files.writeString(data, file("meta:\n  format: " + HvacRegistry.FORMAT_VERSION + "\n"),
                StandardCharsets.UTF_8);
        registry.load();
        registry.markDirty();
        registry.save(true);
        assertTrue(Files.exists(folder.resolve("hvac.yml.bak")), "saving works again");
    }
}
