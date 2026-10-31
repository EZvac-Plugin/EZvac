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
 * BETA 1.0: recovering hvac.yml from an automatic backup, telling which
 * entries were kept aside, discarding them on purpose, and salvaging what
 * still reads of an unreadable file.
 */
class RegistryRepairTest {
    private static final String MAIN = """
              item0:
                id: main
                group: house
                worldUuid: 0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10
                x: 1
                y: 64
                z: 1
                targetF: 70.0
            """;
    private static final String HALL = """
              item1:
                id: hall
                group: hall
                worldUuid: 0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10
                x: 5
                y: 64
                z: 5
                targetF: 70.0
            """;
    private static final String VALID = "meta:\n  format: 8\nthermostats:\n" + MAIN;

    @TempDir Path folder;
    private TestHost host;
    private Path data;

    @BeforeEach
    void setUp() {
        host = new TestHost(folder.toFile());
        data = folder.resolve("hvac.yml");
    }

    private HvacRegistry registry() {
        return new HvacRegistry(host, HvacSettings.defaults());
    }

    @Test
    void withNoUsableBakItRecoversFromTheNewestReadableBackup() throws IOException {
        Files.writeString(data, "thermostats: [broken\n", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("hvac.yml.bak"), "also: [broken\n", StandardCharsets.UTF_8);
        Path backups = Files.createDirectory(folder.resolve("backups"));
        Files.writeString(backups.resolve("hvac-2026-10-29_08-00-00-auto.yml"), VALID, StandardCharsets.UTF_8);
        Files.writeString(backups.resolve("hvac-2026-10-30_08-00-00-auto.yml"), "newest: [broken\n",
                StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        registry.load();
        assertNotNull(TestHost.labelled(registry, "main"), "loaded from the newest backup that reads");
        assertEquals("backups/hvac-2026-10-29_08-00-00-auto.yml", registry.recoveredFrom());
        assertNotNull(registry.setAside());
        assertEquals("thermostats: [broken\n", Files.readString(folder.resolve(registry.setAside())));
        assertEquals(VALID, Files.readString(data), "the recovered copy is hvac.yml now");
    }

    @Test
    void anUnreadableFileCanBeSalvagedButANewerOneIsNeverForced() throws IOException {
        Files.writeString(data, "thermostats: [broken\n", StandardCharsets.UTF_8);
        HvacRegistry.LoadFailure unreadable = assertThrows(HvacRegistry.LoadFailure.class, registry()::load);
        assertTrue(unreadable.salvageable());
        Files.writeString(data, "meta:\n  format: 99\n", StandardCharsets.UTF_8);
        HvacRegistry.LoadFailure newer = assertThrows(HvacRegistry.LoadFailure.class, registry()::load);
        assertFalse(newer.salvageable());
    }

    @Test
    void entriesKeptAsideAreListedAndCanBeDiscardedOnPurpose() throws IOException {
        // item1 has no world: it can't be loaded, so it's kept aside. A newer EZvac's section stays regardless.
        Files.writeString(data, VALID + "  item1:\n    id: lost\n    x: 1\n"
                + "rockets:\n  item0:\n    fuel: 3\n", StandardCharsets.UTF_8);
        HvacRegistry registry = registry();
        registry.load();
        assertEquals(1, registry.keptAside());
        HvacRegistry.KeptAside aside = registry.keptAsideRecords().get(0);
        assertEquals("thermostats", aside.section());
        assertEquals("item1", aside.entry());
        assertEquals(1, registry.discardKeptAside());
        assertEquals(0, registry.keptAside());
        registry.save(true);
        String saved = Files.readString(data);
        assertFalse(saved.contains("lost"), "discarded for good");
        assertTrue(saved.contains("fuel: 3"), "a newer EZvac's records stay");
        assertTrue(saved.contains("id: main"));
    }

    @Test
    void salvagingKeepsEveryEntryThatStillReads() {
        String broken = "meta:\n  format: 8\nthermostats:\n" + MAIN + "  item9: [unclosed\n" + HALL
                + "vents:\n  item0:\n    x: {broken\n";
        RegistrySalvage.Result result = RegistrySalvage.salvage(broken);
        assertEquals(List.of("thermostats.item9", "vents.item0"), result.dropped());
        assertTrue(result.yaml().contains("id: main") && result.yaml().contains("id: hall"), result.yaml());
        assertTrue(result.yaml().contains("format: 8"));
        assertEquals(2, result.kept(), "two thermostats; meta is kept but isn't an entry");
    }

    @Test
    void salvagingOnDiskKeepsTheOriginalAndLoadsAfterwards() throws IOException {
        String broken = VALID + "  item9: [unclosed\n";
        Files.writeString(data, broken, StandardCharsets.UTF_8);
        RegistrySalvage.Saved saved = RegistrySalvage.salvageFile(folder.toFile());
        assertEquals(broken, Files.readString(folder.resolve(saved.original())));
        HvacRegistry registry = registry();
        registry.load();
        assertNotNull(TestHost.labelled(registry, "main"));
        assertNull(registry.recoveredFrom(), "it reads fine now");
    }
}
