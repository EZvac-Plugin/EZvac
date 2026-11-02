package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A system's profile (BETA 1.0 Pilot 2): set from its thermostat's menu or its
 * settings panel, held by exactly one of them, and saved only as before when
 * a panel holds it.
 */
class SystemProfileTest {
    private static final UUID WORLD = UUID.fromString("0b6c3f0e-6a77-4f3e-9d6b-2f1c9a7e4d10");
    private static final HvacSettings SETTINGS = HvacSettings.defaults();

    @TempDir Path folder;
    private HvacRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new HvacRegistry(new TestHost(folder.toFile()), SETTINGS);
    }

    private static BlockKey at(int x, int z) {
        return new BlockKey(WORLD, x, 64, z);
    }

    private Thermostat thermostat(int x) {
        Thermostat thermostat = new Thermostat(at(x, 0), "t" + x, registry.newSystem(WORLD),
                Thermostat.ROOM_DEFAULT_F, 70, SETTINGS, false);
        registry.addThermostat(thermostat);
        return thermostat;
    }

    private SettingsPanel panel(int x) {
        SettingsPanel panel = new SettingsPanel(at(x, 3), "p" + x);
        registry.addSettingsPanel(panel);
        return panel;
    }

    @Test
    void aSystemWithoutAPanelKeepsItsProfileOnItsThermostat() {
        Thermostat house = thermostat(0);
        assertEquals(OperatingProfile.NORMAL, registry.profileFor(house.system()));
        assertTrue(registry.setProfile(house.system(), OperatingProfile.TURBO));
        assertEquals(OperatingProfile.TURBO, registry.profileFor(house.system()));
        assertEquals(OperatingProfile.TURBO, house.profile());
        assertEquals("TURBO", house.toMap().get("profile"));
        assertEquals(OperatingProfile.TURBO,
                Thermostat.fromMap(house.toMap(), SETTINGS).profile(), "it survives a restart");
    }

    @Test
    void aNormalProfileIsNotSavedSoOlderSavesSaveBackUnchanged() {
        assertFalse(thermostat(0).toMap().containsKey("profile"));
    }

    @Test
    void aPanelJoiningTakesOverTheProfileTheSystemAlreadyHad() {
        Thermostat house = thermostat(0);
        registry.setProfile(house.system(), OperatingProfile.ECO);
        SettingsPanel panel = panel(10);
        panel.setProfile(OperatingProfile.TURBO);
        registry.linkSettingsPanel(panel, house.system());

        assertEquals(OperatingProfile.ECO, panel.profile(), "the system's profile, not the panel's old one");
        assertEquals(OperatingProfile.NORMAL, house.profile(), "only the panel holds it now");
        assertEquals(OperatingProfile.ECO, registry.profileFor(house.system()));
        assertFalse(house.toMap().containsKey("profile"));

        registry.setProfile(house.system(), OperatingProfile.TURBO);
        assertEquals(OperatingProfile.TURBO, panel.profile(), "the menu changes what the panel shows");
    }

    @Test
    void aPanelLeavingHandsTheProfileBackToTheThermostat() {
        Thermostat house = thermostat(0);
        SettingsPanel panel = panel(10);
        registry.linkSettingsPanel(panel, house.system());
        registry.setProfile(house.system(), OperatingProfile.TURBO);

        registry.linkSettingsPanel(panel, null);
        assertEquals(OperatingProfile.TURBO, registry.profileFor(house.system()), "unlinked: the system keeps it");
        assertEquals(OperatingProfile.TURBO, house.profile());

        registry.linkSettingsPanel(panel, house.system());
        assertEquals(OperatingProfile.TURBO, panel.profile());
        assertEquals(OperatingProfile.NORMAL, house.profile());
        registry.removeSettingsPanel(panel.position());
        assertEquals(OperatingProfile.TURBO, registry.profileFor(house.system()), "removed: the system keeps it");
    }

    @Test
    void aThermostatJoiningASystemWhosePanelHoldsTheProfileHoldsNone() {
        SettingsPanel panel = panel(10);
        SystemId headless = registry.newSystem(WORLD);
        registry.linkSettingsPanel(panel, headless);
        registry.setProfile(headless, OperatingProfile.ECO);
        Thermostat house = thermostat(0);
        house.setProfile(OperatingProfile.TURBO);
        registry.removeThermostat(house.position());
        Thermostat moved = new Thermostat(at(0, 0), "t0", headless, Thermostat.ROOM_DEFAULT_F, 70, SETTINGS, false);
        moved.setProfile(OperatingProfile.TURBO);
        registry.addThermostat(moved);

        assertEquals(OperatingProfile.ECO, registry.profileFor(headless), "the panel's stands");
        assertEquals(OperatingProfile.NORMAL, moved.profile());
    }

    @Test
    void aSystemWithNeitherHasNoProfileToSet() {
        assertFalse(registry.setProfile(registry.newSystem(WORLD), OperatingProfile.TURBO));
    }
}
