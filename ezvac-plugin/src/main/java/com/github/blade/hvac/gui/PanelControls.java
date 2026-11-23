package com.github.blade.hvac.gui;

import com.github.blade.hvac.HvacPlugin;
import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.control.ControlResult;
import com.github.blade.hvac.api.control.ControlSource;
import com.github.blade.hvac.config.ClimateSettings;
import com.github.blade.hvac.config.HvacSettings;
import com.github.blade.hvac.integration.api.DeviceControls;
import com.github.blade.hvac.model.BlockKey;
import com.github.blade.hvac.model.ClimateUnit;
import com.github.blade.hvac.model.Dehumidifier;
import com.github.blade.hvac.model.Refrigerator;
import com.github.blade.hvac.model.RpmMonitor;
import com.github.blade.hvac.model.SensorHub;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.DisplayService;
import com.github.blade.hvac.service.HvacRegistry;
import com.github.blade.hvac.service.PanelScreens;
import com.github.blade.hvac.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The buttons on new-look screens (BETA 1.0 Pilot 7): a click on a button's
 * box does what the same button in the sign's menu does, with the same
 * permission, then the screen answers at once and the button lights up.
 * Every change goes through {@link DeviceControls}, as the API's do, so
 * plugins can cancel it ({@code DeviceControlEvent}, from the screen).
 */
public final class PanelControls implements Listener {
    private final HvacPlugin plugin;
    private final HvacRegistry registry;
    private final DisplayService displays;
    private final HvacSettings settings;
    private final ClimateSettings climate;
    private final PanelScreens panels;
    private final DeviceControls controls;
    /** Each player's last press, in server ticks: holding the button down repeats it, but not faster than this. */
    private final Map<UUID, Integer> lastPress = new HashMap<>();
    static final int PRESS_TICKS = 3;

    public PanelControls(HvacPlugin plugin, HvacRegistry registry, DisplayService displays, HvacSettings settings,
                         ClimateSettings climate, PanelScreens panels, DeviceControls controls) {
        this.plugin = plugin;
        this.registry = registry;
        this.displays = displays;
        this.settings = settings;
        this.climate = climate;
        this.panels = panels;
        this.controls = controls;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onButton(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction box) || event.getHand() != EquipmentSlot.HAND) return;
        PanelScreens.Hit hit = panels.hit(box);
        if (hit == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("ezvac.use")) {
            player.sendActionBar(Component.text(Lang.text("menu.panel.not-allowed"), NamedTextColor.RED));
            return;
        }
        if (!PanelScreens.inReach(player, hit.position())) return;
        int now = plugin.getServer().getCurrentTick();
        Integer last = lastPress.get(player.getUniqueId());
        if (last != null && now - last < PRESS_TICKS && now >= last) return;
        lastPress.put(player.getUniqueId(), now);
        boolean worked = press(hit.position(), hit.action(), player);
        ControlMenus.click(player, worked);
        if (worked) panels.flash(hit.position(), hit.action());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastPress.remove(event.getPlayer().getUniqueId());
    }

    /** Does what the button says: true when something changed. */
    private boolean press(BlockKey position, String action, Player player) {
        ControlSource source = new ControlSource(ControlSource.Origin.SCREEN, Optional.empty(),
                Optional.of(player.getUniqueId()));
        Thermostat thermostat = registry.thermostatAt(position);
        if (thermostat != null) {
            double step = action.equals("up") ? 1.0 : action.equals("down") ? -1.0 : 0.0;
            if (step == 0.0) return false;
            double minimum = thermostat.pool() ? settings.poolMinimumTargetF() : settings.minimumTargetF();
            double maximum = thermostat.pool() ? settings.poolMaximumTargetF() : settings.maximumTargetF();
            return changed(controls.setTarget(position,
                    Setpoints.stepTarget(thermostat.targetF(), step, settings.units(), minimum, maximum), source));
        }
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
        SensorHub hub = registry.sensorHubAt(position);
        if (hub != null) {
            if (hub.pages() < 2) return false;
            if (action.equals("next")) hub.nextPage();
            else if (action.equals("prev")) hub.previousPage();
            else return false;
            displays.refreshSensorHub(hub, true);
            return true;
        }
        if (registry.settingsPanelAt(position) != null) {
            ThermostatView.Profile profile = switch (action) {
                case "eco" -> ThermostatView.Profile.ECO;
                case "normal" -> ThermostatView.Profile.NORMAL;
                case "turbo" -> ThermostatView.Profile.TURBO;
                default -> null;
            };
            return profile != null && changed(controls.setProfile(position, profile, source));
        }
        ClimateUnit unit = registry.climateUnitAt(position);
        if (unit == null || !unit.position().equals(position)) return false;
        if (action.startsWith("mode-")) {
            try {
                return changed(controls.setFan(position,
                        ClimateUnitView.Fan.valueOf(action.substring(5).toUpperCase(Locale.ROOT)), source));
            } catch (IllegalArgumentException unknown) {
                return false;
            }
        }
        return switch (action) {
            case "defrost" -> changed(controls.startDefrost(position, source));
            case "fridge-up", "fridge-down" -> unit instanceof com.github.blade.hvac.model.FridgeFreezer both
                    && changed(controls.setFridgeSetpoint(position,
                    both.fridgeSetpoint() + (action.equals("fridge-up") ? 1 : -1), source));
            case "up", "down" -> {
                int sign = action.equals("up") ? 1 : -1;
                if (unit instanceof Refrigerator)
                    yield changed(controls.setSetpoint(position, unit.setpoint() + sign, source));
                // A dehumidifier or humidifier: five points at a time, within its presets, as its menu.
                boolean drying = unit instanceof Dehumidifier;
                int low = drying ? climate.dehumidifier().minimumSetpoint() : climate.humidifier().minimumSetpoint();
                int high = drying ? climate.dehumidifier().maximumSetpoint() : climate.humidifier().maximumSetpoint();
                yield changed(controls.setSetpoint(position,
                        Setpoints.stepHumidity(unit.setpoint(), 5 * sign, low, high), source));
            }
            default -> false;
        };
    }

    private static boolean changed(ControlResult result) {
        return result.status() == ControlResult.Status.DONE || result.status() == ControlResult.Status.ADJUSTED;
    }
}
