package com.github.blade.hvac.api;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * What a refrigeration appliance holds beyond a climate unit's view (API
 * 1.12): what a web panel needs to show it whole. Its inside and setpoint
 * are the view's own {@code insideF} and {@code setpoint}: a refrigerator's
 * are its freezer's, and its fridge's are here.
 *
 * @param doorOpen        whether anyone has a barrel of it open
 * @param frost           a deep freezer's or refrigerator's freezer's frost, 0 none to 1 a full ring; empty for the others
 * @param defrosting      whether it is defrosting now
 * @param autoDefrost     whether it defrosts itself: a refrigerator's freezer unless that's switched off; never a deep freezer
 * @param fridgeInsideF   a refrigerator's fridge compartment's inside, in F; empty for the others, and before it first ran
 * @param fridgeSetpointF a refrigerator's fridge compartment's setpoint, in F; empty for the others
 * @param chillerF        a mini fridge's chiller box, in F; empty for the others, and before it first ran
 * @param barrels         how many barrels it keeps cold: a fridge's one to three, a refrigerator's two
 * @since 1.12
 */
public record ColdStorage(boolean doorOpen, OptionalDouble frost, boolean defrosting, boolean autoDefrost,
                          OptionalDouble fridgeInsideF, OptionalInt fridgeSetpointF, OptionalDouble chillerF,
                          int barrels) {
    public ColdStorage {
        frost = Objects.requireNonNullElse(frost, OptionalDouble.empty());
        fridgeInsideF = Objects.requireNonNullElse(fridgeInsideF, OptionalDouble.empty());
        fridgeSetpointF = Objects.requireNonNullElse(fridgeSetpointF, OptionalInt.empty());
        chillerF = Objects.requireNonNullElse(chillerF, OptionalDouble.empty());
    }
}
