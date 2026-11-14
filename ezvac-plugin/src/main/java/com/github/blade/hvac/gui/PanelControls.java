package com.github.blade.hvac.gui;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.api.event.ThermostatTargetChangeEvent;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.integration.api.ApiEvents;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.Freezer;
import com.github.blade.hvac.model.FridgeFreezer;
import com.github.blade.hvac.model.Refrigerator;
import com.github.blade.hvac.model.RpmMonitor;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.PanelScreens;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The buttons on new-look screens (BETA 1.0 Pilot 7): a click on a button's
 * box does what the same button in the sign's menu does, with the same
 * permission, then the screen answers at once and the button lights up.
 */
public final class PanelControls implements Listener {
    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final DisplayService displays;
    private final HvacSettings settings;
    private final ClimateSettings climate;
    private final PanelScreens panels;
    /** Each player's last press, in server ticks: holding the button down repeats it, but not faster than this. */
    private final java.util.Map<java.util.UUID, Integer> lastPress = new java.util.HashMap<>();
    static final int PRESS_TICKS = 3;

    public PanelControls(HvacPlugin plugin, HvacRegistry registry, DisplayService displays, HvacSettings settings,
                         ClimateSettings climate, PanelScreens panels) {
        this.plugin = plugin;
        this.registry = registry;
        this.displays = displays;
        this.settings = settings;
        this.climate = climate;
        this.panels = panels;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onButton(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction box) || event.getHand() != EquipmentSlot.HAND) return;
        PanelScreens.Hit hit = panels.hit(box);
        if (hit == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("ezvac.use")) {
            player.sendActionBar(Component.text("You may not control this HVAC device.", NamedTextColor.RED));
            return;
        }
        if (!PanelScreens.inReach(player, hit.position())) return;
        int now = plugin.getServer().getCurrentTick();
        Integer last = lastPress.get(player.getUniqueId());
        if (last != null && now - last < PRESS_TICKS && now >= last) return;
        lastPress.put(player.getUniqueId(), now);
        boolean worked = press(hit.position(), hit.action());
        ControlMenus.click(player, worked);
        if (!worked) return;
        panels.flash(hit.position(), hit.action());
        registry.markDirty();
        plugin.requestImmediateSync();
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastPress.remove(event.getPlayer().getUniqueId());
    }

    /** Does what the button says: true when something changed. */
    private boolean press(BlockKey position, String action) {
        Thermostat thermostat = registry.thermostatAt(position);
        if (thermostat != null) return thermostat(thermostat, action);
        RpmMonitor monitor = registry.rpmMonitorAt(position);
        if (monitor != null) {
            int rows = displays.rowCount(monitor);
            if (RpmMonitor.pages(rows) < 2) return false;
            if (action.equals("next")) monitor.nextPage(rows);
            else if (action.equals("prev")) monitor.previousPage(rows);
            else return false;
            displays.refreshRpmMonitor(monitor, true);
            return true;
        }
        var hub = registry.sensorHubAt(position);
        if (hub != null) {
            if (hub.pages() < 2) return false;
            if (action.equals("next")) hub.nextPage();
            else if (action.equals("prev")) hub.previousPage();
            else return false;
            displays.refreshSensorHub(hub, true);
            return true;
        }
        var panel = registry.settingsPanelAt(position);
        if (panel != null) {
            if (panel.system() == null) return false;
            com.github.blade.hvac.model.OperatingProfile profile = switch (action) {
                case "eco" -> com.github.blade.hvac.model.OperatingProfile.ECO;
                case "normal" -> com.github.blade.hvac.model.OperatingProfile.NORMAL;
                case "turbo" -> com.github.blade.hvac.model.OperatingProfile.TURBO;
                default -> null;
            };
            if (profile == null || profile == panel.profile()) return false;
            registry.setProfile(panel.system(), profile);
            displays.refreshSettingsPanel(panel, true);
            Thermostat head = registry.thermostatFor(panel.system());
            if (head != null) displays.refreshThermostat(head, true);
            return true;
        }
        ClimateUnit unit = registry.climateUnitAt(position);
        if (unit == null || !unit.position().equals(position)) return false;
        if (unit instanceof Refrigerator refrigerator) return refrigerator(refrigerator, action);
        if (unit instanceof com.github.blade.hvac.model.AirPurifier purifier) {
            if (!action.startsWith("mode-")) return false;
            com.github.blade.hvac.model.AirPurifier.Mode mode;
            try {
                mode = com.github.blade.hvac.model.AirPurifier.Mode.valueOf(
                        action.substring(5).toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return false;
            }
            if (purifier.mode() == mode) return false;
            purifier.setMode(mode);
            displays.refreshClimateUnit(purifier, true);
            return true;
        }
        // A dehumidifier or humidifier: five points at a time, within its presets, as its menu.
        int step = action.equals("up") ? 5 : action.equals("down") ? -5 : 0;
        if (step == 0) return false;
        boolean drying = unit instanceof com.github.blade.hvac.model.Dehumidifier;
        int low = drying ? climate.dehumidifier().minimumSetpoint() : climate.humidifier().minimumSetpoint();
        int high = drying ? climate.dehumidifier().maximumSetpoint() : climate.humidifier().maximumSetpoint();
        int setpoint = Setpoints.stepHumidity(unit.setpoint(), step, low, high);
        if (setpoint == unit.setpoint()) return false;
        unit.setSetpoint(setpoint);
        displays.refreshClimateUnit(unit, true);
        return true;
    }

    private boolean thermostat(Thermostat thermostat, String action) {
        double step = action.equals("up") ? 1.0 : action.equals("down") ? -1.0 : 0.0;
        if (step == 0.0) return false;
        double minimum = thermostat.pool() ? settings.poolMinimumTargetF() : settings.minimumTargetF();
        double maximum = thermostat.pool() ? settings.poolMaximumTargetF() : settings.maximumTargetF();
        double target = Setpoints.stepTarget(thermostat.targetF(), step, settings.units(), minimum, maximum);
        if (Math.abs(target - thermostat.targetF()) <= 1e-9) return false;
        double previous = thermostat.targetF();
        thermostat.setTargetF(target, settings);
        ApiEvents.targetChanged(thermostat, previous, ThermostatTargetChangeEvent.Cause.MENU, plugin.controller());
        displays.refreshThermostat(thermostat, true);
        return true;
    }

    private boolean refrigerator(Refrigerator refrigerator, String action) {
        switch (action) {
            case "up", "down" -> {
                int before = refrigerator.setpoint();
                refrigerator.setSetpoint(before + (action.equals("up") ? 1 : -1));
                if (refrigerator.setpoint() == before) return false;
            }
            case "fridge-up", "fridge-down" -> {
                if (!(refrigerator instanceof FridgeFreezer both)) return false;
                int before = both.fridgeSetpoint();
                both.setFridgeSetpoint(before + (action.equals("fridge-up") ? 1 : -1));
                if (both.fridgeSetpoint() == before) return false;
            }
            case "defrost" -> {
                if (!(refrigerator instanceof Freezer freezer) || freezer.defrosting()) return false;
                freezer.startDefrost(climate.refrigeration());
            }
            default -> {
                return false;
            }
        }
        displays.refreshClimateUnit(refrigerator, true);
        return true;
    }
}
