package com.github.blade.hvac.model;

import java.util.UUID;

/**
 * Who may change a registered device.
 *
 * <p>The whole rule is one predicate on purpose: every command, sign edit, and
 * interaction routes through it, so there is a single place to read and a
 * single place to test. Wiring it in is thin; the decision is here.
 */
public final class Ownership {
    private Ownership() {}

    /**
     * Devices created before ownership existed carry no owner and stay
     * admin-only rather than becoming public. That is deliberate: on a server
     * that opens building up to players, treating unowned devices as public
     * would make every piece of pre-existing infrastructure editable by
     * everybody the moment the permission is granted - the hole opening at
     * exactly the moment the server opens.
     *
     * <p>An administrator always passes, which is what keeps the system
     * recoverable: ownership can be reassigned, and nothing can become
     * permanently unreachable.
     */
    public static boolean mayModify(UUID owner, UUID actor, boolean administrator) {
        if (administrator) return true;
        return owner != null && owner.equals(actor);
    }

    /** Parses a persisted owner, treating anything unreadable as unowned. */
    public static UUID read(Object value) {
        if (!(value instanceof String text) || text.isBlank()) return null;
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Writes an owner only when there is one, so unowned records stay clean. */
    public static void write(java.util.Map<String, Object> map, UUID owner) {
        if (owner != null) map.put("owner", owner.toString());
    }
}
