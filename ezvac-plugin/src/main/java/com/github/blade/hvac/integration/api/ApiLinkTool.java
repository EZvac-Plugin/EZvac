package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.EzvacLinkTool;
import com.github.blade.hvac.command.LinkTool;
import com.github.blade.hvac.model.Thermostat;
import com.github.blade.hvac.service.HvacRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** The link tool as the API shows it (1.3). */
final class ApiLinkTool implements EzvacLinkTool {
    private final LinkTool tool;
    private final HvacRegistry registry;

    ApiLinkTool(LinkTool tool, HvacRegistry registry) {
        this.tool = Objects.requireNonNull(tool, "tool");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public boolean isLinkTool(ItemStack item) {
        return tool.isTool(item);
    }

    @Override
    public Optional<LinkSelection> selection(ItemStack item) {
        LinkTool.Selection selection = tool.selection(item);
        if (selection == null) return Optional.empty();
        if (selection.thermostat() != null) {
            // The system is the thermostat's as it is now: linking can have moved it.
            Thermostat thermostat = registry.thermostatAt(selection.thermostat());
            if (thermostat == null) return Optional.empty();
            return Optional.of(new LinkSelection(Optional.of(thermostat.system().key()),
                    Optional.of(ApiViews.position(thermostat.position())), Optional.empty()));
        }
        if (registry.sensorHubAt(selection.hub()) == null) return Optional.empty();
        return Optional.of(new LinkSelection(Optional.empty(), Optional.empty(),
                Optional.of(ApiViews.position(selection.hub()))));
    }
}
