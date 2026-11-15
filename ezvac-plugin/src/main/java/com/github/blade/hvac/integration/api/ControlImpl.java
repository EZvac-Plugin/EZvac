package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateUnitView;
import com.github.blade.hvac.api.Position;
import com.github.blade.hvac.api.ThermostatView;
import com.github.blade.hvac.api.control.ControlResult;
import com.github.blade.hvac.api.control.ControlSource;
import com.github.blade.hvac.api.control.EzvacControl;
import com.github.blade.hvac.model.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A plugin's controller (API 1.12): every call is made on the main thread,
 * at once when the caller is already there, otherwise on the next tick; the
 * futures still waiting when EZvac stops complete with NOT_FOUND (a read's,
 * exceptionally), so a web server's thread never waits for ever.
 */
public final class ControlImpl implements EzvacControl {
    private final Plugin ezvac;
    private final Plugin caller;
    private final Optional<UUID> player;
    private final Supplier<DeviceControls> controls;
    /** Futures not yet completed, shared by every controller, so EZvac stopping can complete them. */
    private final Set<CompletableFuture<?>> waiting;

    public ControlImpl(Plugin ezvac, Plugin caller, Optional<UUID> player, Supplier<DeviceControls> controls,
                       Set<CompletableFuture<?>> waiting) {
        this.ezvac = ezvac;
        this.caller = Objects.requireNonNull(caller, "plugin");
        this.player = player;
        this.controls = controls;
        this.waiting = waiting;
    }

    /** A fresh set for the futures every controller shares. */
    public static Set<CompletableFuture<?>> waitingSet() { return ConcurrentHashMap.newKeySet(); }

    /** EZvac is stopping: whatever still waits completes now. */
    public static void stopping(Set<CompletableFuture<?>> waiting) {
        for (CompletableFuture<?> future : Set.copyOf(waiting)) future.completeExceptionally(
                new IllegalStateException("EZvac stopped before the call could be made"));
        waiting.clear();
    }

    @Override public Plugin plugin() { return caller; }
    @Override public Optional<UUID> player() { return player; }

    @Override
    public EzvacControl as(UUID player) {
        return new ControlImpl(ezvac, caller, Optional.of(Objects.requireNonNull(player, "player")), controls, waiting);
    }

    private ControlSource source() {
        return new ControlSource(ControlSource.Origin.API, Optional.of(caller.getName()), player);
    }

    private static BlockKey key(Position position) {
        Objects.requireNonNull(position, "position");
        return new BlockKey(position.world(), position.x(), position.y(), position.z());
    }

    private CompletableFuture<ControlResult> call(Position position, Function<DeviceControls, ControlResult> change) {
        BlockKey key = key(position);
        return onMain(() -> {
            DeviceControls now = controls.get();
            if (now == null || !ezvac.isEnabled())
                return new ControlResult(ControlResult.Status.NOT_FOUND, "EZvac isn't running.", Optional.empty());
            return change.apply(now);
        }).exceptionally(failed -> new ControlResult(ControlResult.Status.NOT_FOUND,
                "EZvac couldn't make that change: " + failed.getMessage(), Optional.empty()));
    }

    private <T> CompletableFuture<T> onMain(Supplier<T> work) {
        if (!ezvac.isEnabled()) return CompletableFuture.failedFuture(new IllegalStateException("EZvac isn't running"));
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(work.get());
            } catch (RuntimeException failed) {
                return CompletableFuture.failedFuture(failed);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        waiting.add(future);
        future.whenComplete((ignored, failed) -> waiting.remove(future));
        try {
            Bukkit.getScheduler().runTask(ezvac, () -> {
                try {
                    future.complete(work.get());
                } catch (RuntimeException failed) {
                    future.completeExceptionally(failed);
                }
            });
        } catch (RuntimeException stopping) {
            future.completeExceptionally(stopping);
        }
        return future;
    }

    @Override
    public CompletableFuture<ControlResult> setTarget(Position thermostat, double targetF) {
        return call(thermostat, c -> c.setTarget(key(thermostat), targetF, source()));
    }

    @Override
    public CompletableFuture<ControlResult> setEnabled(Position thermostat, boolean enabled) {
        return call(thermostat, c -> c.setEnabled(key(thermostat), enabled, source()));
    }

    @Override
    public CompletableFuture<ControlResult> setProfile(Position thermostatOrPanel, ThermostatView.Profile profile) {
        Objects.requireNonNull(profile, "profile");
        return call(thermostatOrPanel, c -> c.setProfile(key(thermostatOrPanel), profile, source()));
    }

    @Override
    public CompletableFuture<ControlResult> setPowered(Position unit, boolean powered) {
        return call(unit, c -> c.setPowered(key(unit), powered, source()));
    }

    @Override
    public CompletableFuture<ControlResult> setSetpoint(Position unit, int setpoint) {
        return call(unit, c -> c.setSetpoint(key(unit), setpoint, source()));
    }

    @Override
    public CompletableFuture<ControlResult> setFridgeSetpoint(Position refrigerator, int setpointF) {
        return call(refrigerator, c -> c.setFridgeSetpoint(key(refrigerator), setpointF, source()));
    }

    @Override
    public CompletableFuture<ControlResult> setFan(Position purifier, ClimateUnitView.Fan fan) {
        Objects.requireNonNull(fan, "fan");
        return call(purifier, c -> c.setFan(key(purifier), fan, source()));
    }

    @Override
    public CompletableFuture<ControlResult> startDefrost(Position freezer) {
        return call(freezer, c -> c.startDefrost(key(freezer), source()));
    }

    @Override
    public CompletableFuture<ControlResult> setAutoDefrost(Position refrigerator, boolean on) {
        return call(refrigerator, c -> c.setAutoDefrost(key(refrigerator), on, source()));
    }

    @Override
    public CompletableFuture<ControlResult> setEquipmentEnabled(Position equipment, boolean enabled) {
        return call(equipment, c -> c.setEquipmentEnabled(key(equipment), enabled, source()));
    }

    @Override
    public <T> CompletableFuture<T> read(Supplier<T> read) {
        Objects.requireNonNull(read, "read");
        return onMain(read);
    }
}
