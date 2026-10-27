package com.github.blade.hvac.service;

import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.model.*;
import com.github.blade.hvac.simulation.EtaEstimator;
import com.github.blade.hvac.simulation.PerformanceModel;
import com.github.blade.hvac.simulation.ThermalModel;
import com.github.blade.hvac.simulation.ThermostatControl;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Equipment tiers (3.1): single-stage, two-stage and variable, like real
 * equipment. Every unit from before tiers is variable, at full capacity; the
 * budget tiers also deliver less.
 */
class TierTest {
    private static final HvacSettings SETTINGS = HvacSettings.defaults();
    private static final double EPSILON = 1e-9;

    private static EquipmentUnit unit(EquipmentType type, Tier tier, double rpm) {
        BlockKey position = new BlockKey(UUID.randomUUID(), 1, 64, 1);
        Map<String, Object> values = new LinkedHashMap<>();
        position.write(values, "");
        values.put("system", "test");
        values.put("equipmentType", type.name());
        values.put("running", true);
        values.put("operatingMode", type == EquipmentType.AIR_CONDITIONER ? "COOLING" : "HEATING");
        values.put("motorRpm", rpm);
        if (tier != null) values.put("tier", tier.number());
        return EquipmentUnit.fromMap(values);
    }

    @Test
    void tiersAreTheThreeKindsOfRealEquipment() {
        assertEquals(Tier.SINGLE_STAGE, Tier.of(1));
        assertEquals(Tier.VARIABLE, Tier.of(7), "out of range is the nearest");
        assertEquals(Tier.VARIABLE, Tier.parse("t3"));
        assertEquals(Tier.TWO_STAGE, Tier.parse("Dual"));
        assertEquals(Tier.SINGLE_STAGE, Tier.parse("single-stage"));
        assertEquals(Tier.VARIABLE, Tier.parse("modulating"));
        assertNull(Tier.parse("house"), "an old group name is no tier");
        assertEquals("★☆☆", SETTINGS.stars(Tier.SINGLE_STAGE));
        assertEquals("★★★", SETTINGS.stars(Tier.VARIABLE));
        assertEquals("Variable-speed", Tier.VARIABLE.displayName(EquipmentType.HEAT_PUMP));
        assertEquals("Modulating", Tier.VARIABLE.displayName(EquipmentType.FURNACE), "a burner modulates");
        assertEquals("Two-stage", Tier.TWO_STAGE.displayName(EquipmentType.BOILER));
    }

    @Test
    void everyUnitFromBeforeTiersIsVariableAtFullCapacity() {
        assertEquals(Tier.VARIABLE, Tier.LEGACY);
        assertEquals(Tier.VARIABLE, unit(EquipmentType.AIR_CONDITIONER, null, 0).tier(), "a save without a tier");
        assertEquals(Tier.VARIABLE, unit(EquipmentType.FURNACE, null, 0).tier());
        // A variable compressor is the compressor that came before, bit for bit.
        for (double rpm = 0.0; rpm <= 4_400.0; rpm += 100.0)
            assertEquals(PerformanceModel.variableCapacityFromRpm(rpm, SETTINGS),
                    unit(EquipmentType.HEAT_PUMP, Tier.VARIABLE, rpm).operatingCapacity(SETTINGS), "at " + rpm);
        for (double error = 0.0; error <= 5.0; error += 0.25)
            assertEquals(PerformanceModel.requestedVariableCapacity(error, SETTINGS),
                    PerformanceModel.stagedRequest(Tier.VARIABLE, error, SETTINGS));
        // A modulating furnace gives the full heat a furnace always had, far from its target.
        EquipmentUnit furnace = unit(EquipmentType.FURNACE, Tier.VARIABLE, 0);
        furnace.advanceMotor(PerformanceModel.stagedRequest(Tier.VARIABLE, 5.0, SETTINGS), 200, SETTINGS);
        assertEquals(1.0, furnace.operatingCapacity(SETTINGS), EPSILON);
        assertEquals(PerformanceModel.FURNACE_MAXIMUM_RPM, furnace.motorRpm(), EPSILON);
        List<EtaEstimator.UnitSnapshot> before = List.of(new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 1_500));
        List<EtaEstimator.UnitSnapshot> after = List.of(new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 1_500,
                Tier.VARIABLE));
        assertEquals(EtaEstimator.estimateTicks(78, 72, 85, OperatingMode.COOLING, before, SETTINGS),
                EtaEstimator.estimateTicks(78, 72, 85, OperatingMode.COOLING, after, SETTINGS));
    }

    @Test
    void theBudgetTiersDeliverLess() {
        double full = SETTINGS.variableMaximumRpm();
        assertEquals(0.70, unit(EquipmentType.HEAT_PUMP, Tier.SINGLE_STAGE, full).operatingCapacity(SETTINGS), EPSILON);
        assertEquals(0.85, unit(EquipmentType.HEAT_PUMP, Tier.TWO_STAGE, full).operatingCapacity(SETTINGS), EPSILON);
        assertEquals(1.0, unit(EquipmentType.HEAT_PUMP, Tier.VARIABLE, full).operatingCapacity(SETTINGS), EPSILON);
        assertEquals(0.70, unit(EquipmentType.BOILER, Tier.SINGLE_STAGE, 0).operatingCapacity(SETTINGS), EPSILON,
                "burners too");
    }

    @Test
    void eachTierAsksForWhatItCanDo() {
        double band = SETTINGS.variableApproachBandF();
        for (double error : new double[]{0.0, band, band + 2.0})
            assertEquals(1.0, PerformanceModel.stagedRequest(Tier.SINGLE_STAGE, error, SETTINGS), "flat out at " + error);
        assertEquals(0.65, PerformanceModel.stagedRequest(Tier.TWO_STAGE, band, SETTINGS), "low stage near the target");
        assertEquals(1.0, PerformanceModel.stagedRequest(Tier.TWO_STAGE, band + 0.1, SETTINGS), "high stage far off");
        assertEquals(SETTINGS.variableMinimumCapacity(), PerformanceModel.stagedRequest(Tier.VARIABLE, 0.0, SETTINGS));

        EquipmentUnit single = unit(EquipmentType.AIR_CONDITIONER, Tier.SINGLE_STAGE, 0);
        single.advanceMotor(0.4, 200, SETTINGS);
        assertEquals(SETTINGS.variableMaximumRpm(), single.motorRpm(), EPSILON, "a single-stage unit ignores a low ask");
        EquipmentUnit twoStage = unit(EquipmentType.AIR_CONDITIONER, Tier.TWO_STAGE, 0);
        twoStage.advanceMotor(0.65, 200, SETTINGS);
        assertEquals(PerformanceModel.variableTargetRpm(0.65, SETTINGS), twoStage.motorRpm(), EPSILON);

        EquipmentUnit furnace = unit(EquipmentType.FURNACE, Tier.TWO_STAGE, 0);
        furnace.advanceMotor(0.65, 200, SETTINGS);
        assertEquals(0.65, furnace.share(), EPSILON);
        assertEquals(0.85 * 0.65, furnace.operatingCapacity(SETTINGS), EPSILON);
        assertEquals(PerformanceModel.FURNACE_MAXIMUM_RPM * 0.65, furnace.motorRpm(), EPSILON, "the blower slows");
        EquipmentUnit singleFurnace = unit(EquipmentType.FURNACE, Tier.SINGLE_STAGE, 0);
        singleFurnace.advanceMotor(0.4, 200, SETTINGS);
        assertEquals(1.0, singleFurnace.share(), "a single-stage burner is flat out or off");
        EquipmentUnit modulating = unit(EquipmentType.BOILER, Tier.VARIABLE, 0);
        modulating.advanceMotor(0.1, 200, SETTINGS);
        assertEquals(SETTINGS.variableMinimumCapacity(), modulating.share(), EPSILON, "a burner modulates down to 40%");
        modulating.setTier(Tier.SINGLE_STAGE);
        assertEquals(1.0, modulating.share(), "a new tier starts afresh");
    }

    @Test
    void theEtaFollowsTheTier() {
        OptionalLong single = EtaEstimator.estimateTicks(80, 72, 85, OperatingMode.COOLING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 0, Tier.SINGLE_STAGE)), SETTINGS);
        OptionalLong variable = EtaEstimator.estimateTicks(80, 72, 85, OperatingMode.COOLING,
                List.of(new EtaEstimator.UnitSnapshot(EquipmentType.HEAT_PUMP, 0, Tier.VARIABLE)), SETTINGS);
        assertTrue(single.isPresent() && variable.isPresent());
        assertTrue(single.getAsLong() > variable.getAsLong(), "the budget unit is slower: " + single + " vs " + variable);
    }

    @Test
    void theTiersAreConfiguredAndCheckedInConfigYml() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("performance.tiers.single-stage.capacity", 0.5);
        config.set("displays.stars.full", "*");
        HvacSettings loaded = HvacSettings.load(config);
        assertEquals(0.5, loaded.tierCapacity(Tier.SINGLE_STAGE));
        assertEquals(0.85, loaded.tierCapacity(Tier.TWO_STAGE), "the rest stay at their defaults");
        assertEquals(1.0, loaded.tierCapacity(Tier.VARIABLE), "variable is always the full capacity");
        assertEquals("**☆", loaded.stars(Tier.TWO_STAGE));
        config.set("performance.tiers.two-stage.low-stage", 1.2);
        assertThrows(IllegalArgumentException.class, () -> HvacSettings.load(config));
    }

    /**
     * One room on a warm afternoon with a small load, held at 72 F for an
     * hour by one unit of each tier. A variable unit throttles to the load; a
     * two-stage one drops to its low stage; a single-stage one runs flat out,
     * overshoots and cycles. Mirrors FullCycleScenarioTest, with each tier's
     * own staging and capacity.
     */
    @Test
    void theBetterTheTierTheFewerTheStarts() {
        Map<Tier, Integer> starts = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            double room = 73.2;
            double target = 72.0;
            double rpm = 0.0;
            OperatingMode mode = OperatingMode.IDLE;
            long ticksInMode = SETTINGS.minimumOffTicks();
            int count = 0;
            for (int cycle = 0; cycle < 3_600; cycle++) {
                boolean running = mode == OperatingMode.COOLING;
                double requested = PerformanceModel.stagedRequest(tier, Math.abs(room - target), SETTINGS);
                rpm = PerformanceModel.approach(rpm, running ? PerformanceModel.variableTargetRpm(requested, SETTINGS) : 0.0,
                        1.0, SETTINGS.variableRampUpRpmPerSecond(), SETTINGS.variableRampDownRpmPerSecond());
                double capacity = running
                        ? SETTINGS.tierCapacity(tier) * PerformanceModel.variableCapacityFromRpm(rpm, SETTINGS) : 0.0;
                room = ThermalModel.step(room, target - SETTINGS.coolingStopBelowF(), 80.0,
                        running ? OperatingMode.COOLING : OperatingMode.IDLE, capacity, 20, SETTINGS).temperatureF();
                var next = ThermostatControl.advance(mode, ticksInMode, room, target, true, true, true, 20, SETTINGS);
                if (mode == OperatingMode.IDLE && next.mode() == OperatingMode.COOLING) count++;
                mode = next.mode();
                ticksInMode = next.ticksInMode();
            }
            starts.put(tier, count);
        }
        assertTrue(starts.get(Tier.VARIABLE) <= starts.get(Tier.TWO_STAGE), starts::toString);
        assertTrue(starts.get(Tier.TWO_STAGE) <= starts.get(Tier.SINGLE_STAGE), starts::toString);
        assertTrue(starts.get(Tier.VARIABLE) < starts.get(Tier.SINGLE_STAGE), "the tiers must feel different: " + starts);
    }
}
