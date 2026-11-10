package com.github.blade.hvac.api.addon;

/**
 * What a server has switched for an addon (API 1.9), as
 * {@link com.github.blade.hvac.api.EzvacApi#registerAddon} hands back: read
 * it once to start in the right state. Later changes come through
 * {@link Addon#setActive} and each {@link Addon.Part}'s switcher.
 */
public interface AddonSwitches {
    /** Everything on: what an addon gets from an EZvac without the Addons menu. */
    AddonSwitches ALL_ON = new AddonSwitches() {
        @Override
        public boolean active() { return true; }

        @Override
        public boolean partOn(String id) { return true; }
    };

    /** Whether the addon should run. */
    boolean active();

    /**
     * Whether one of its parts should run: what the server switched, or for
     * a part it never switched, {@link Addon.Part#startsOn()}.
     */
    boolean partOn(String id);
}
