package com.github.blade.hvac.service;

import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.model.EquipmentType;
import com.github.blade.hvac.model.OperatingMode;
import com.github.blade.hvac.model.RpmMonitor;
import com.github.blade.hvac.model.Tier;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every screen of the new look (BETA 1.0 Pilot 7) fits its panel in every
 * state: nothing runs off, no two buttons touch, no button covers text, and
 * no text runs out of its zone, in Fahrenheit and Celsius, with long names
 * and warnings.
 */
class PanelsTest {
    private static final String LONG = "Upstairs-bathroom-long-name";

    private static void fits(Panel panel, String what) {
        assertEquals(List.of(), panel.problems(), what);
    }

    @Test
    void thermostatsFitInEveryState() {
        for (TemperatureUnit units : TemperatureUnit.values())
            for (OperatingMode mode : OperatingMode.values())
                for (boolean enabled : new boolean[] {true, false})
                    for (String blocked : new String[] {"", "No vent", "No unit", "Units off"})
                        for (int attention : new int[] {0, 3, 12})
                            for (boolean pool : new boolean[] {false, true})
                                for (String name : new String[] {"Hall", LONG}) {
                                    var state = new Panels.ThermostatState(name, pool, pool ? 104 : 72.4,
                                            pool ? 82 : 70, mode, enabled, blocked, pool ? 60 : 50, pool ? 104 : 90,
                                            0.4, attention);
                                    fits(Panels.thermostat(units, state), "thermostat " + state + " " + units);
                                    fits(Panels.mini(units, state), "mini " + state + " " + units);
                                }
    }

    @Test
    void refrigerationFitsInEveryState() {
        for (TemperatureUnit units : TemperatureUnit.values())
            for (String activity : new String[] {"Cooling", "Holding", "Door open", "Defrosting", "Off"})
                for (String name : new String[] {"Shop", LONG}) {
                    fits(Panels.cold(units, new Panels.ColdState(name, activity, 37.2, 37, false, false, Double.NaN, 3,
                            0, false)), "fridge " + activity + " " + units);
                    fits(Panels.cold(units, new Panels.ColdState(name, activity, 44.1, 50, false, true, 27.4, 1,
                            0, false)), "mini fridge " + activity + " " + units);
                    fits(Panels.cold(units, new Panels.ColdState(name, activity, -18.6, -20, true, false, Double.NaN,
                            1, 0.6, activity.equals("Defrosting"))), "deep freezer " + activity + " " + units);
                    fits(Panels.refrigerator(units, new Panels.RefrigeratorState(name, activity, 50, 50, -5, -5, 0.4)),
                            "refrigerator " + activity + " " + units);
                }
    }

    @Test
    void theEquipmentMonitorFitsEveryKindTierAndPage() {
        List<RpmMonitor.Row> rows = new ArrayList<>();
        for (EquipmentType type : EquipmentType.values())
            rows.add(new RpmMonitor.Row(type, Tier.VARIABLE, 120, type.ordinal() % 2, 12_400));
        for (int from = 0; from < rows.size(); from += 3) {
            List<RpmMonitor.Row> page = rows.subList(from, Math.min(from + 3, rows.size()));
            fits(Panels.monitor(LONG, "Main-system", true, page, from / 3, 2), "monitor page " + from);
            fits(Panels.monitor("Mon", "", true, page, 0, 1), "one page");
        }
        fits(Panels.monitor("Mon", "", false, List.of(), 0, 1), "unlinked");
        fits(Panels.monitor("Mon", "Main", true, List.of(), 0, 1), "no equipment");
    }

    @Test
    void theCheckCatchesWhatWouldGoWrong() {
        Panel panel = new Panel(Panel.Size.SIGN);
        panel.button("a", 10, 10, 20, 12, "A", "a").button("b", 25, 10, 20, 12, "B", "b");
        panel.label("long", 60, 10, 10, Component.text("far too long for this"), 1, Panel.Align.LEFT);
        panel.button("off", 95, 45, 20, 12, "X", "x");
        List<String> problems = panel.problems();
        assertTrue(problems.stream().anyMatch(p -> p.contains("a and b overlap")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("long's text")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("off runs off")), problems.toString());
    }
}
