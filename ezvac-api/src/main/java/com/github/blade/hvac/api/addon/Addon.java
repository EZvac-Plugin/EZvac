package com.github.blade.hvac.api.addon;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An addon that EZvac's Addons menu can switch on and off while the server
 * runs (API 1.9). Register it once in {@code onEnable} with
 * {@link com.github.blade.hvac.api.EzvacApi#registerAddon}: what comes back
 * says whether it, and each of its parts, should run now, since a server's
 * choices are kept in EZvac's config.yml through restarts.
 *
 * <p>Switched off, an addon stays loaded but does nothing: it stops its
 * tasks, takes down what it shows, and its listeners do nothing, until it is
 * switched on again. Both calls come on the main thread.
 *
 * <p>The menu lists EZvac's official addons. Any plugin may register; one
 * that isn't official is always on.
 */
public interface Addon {
    /** Switched on or off in the Addons menu: start or stop everything the addon does. */
    void setActive(boolean active);

    /**
     * Parts of the addon a server can switch on their own, in the order the
     * menu lists them. A part is only asked to run while the addon itself is
     * on; none by default.
     */
    default List<Part> parts() {
        return List.of();
    }

    /**
     * One switchable part of an addon.
     *
     * @param id          its key in EZvac's config.yml: short, lower case, never changed once released
     * @param name        as the menu shows it: {@code Snow and ice}
     * @param description one line saying what switching it off stops
     * @param switcher    called with true or false when the server switches it
     */
    record Part(String id, String name, String description, Consumer<Boolean> switcher) {
        public Part {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(switcher, "switcher");
            if (!id.matches("[a-z0-9][a-z0-9-]{0,31}"))
                throw new IllegalArgumentException("A part's id is lower-case letters, digits and dashes: " + id);
        }
    }
}
