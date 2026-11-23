package com.github.blade.hvac.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Something a player typed or did that EZvac can't do, said in the server's
 * language (BETA 1.1). It is an {@link IllegalArgumentException}, so the
 * places that catch those and show their message keep working; {@link
 * #describe(IllegalArgumentException)} gives it its colours.
 */
public final class UserError extends IllegalArgumentException {
    private final String key;
    private final transient TagResolver[] args;

    public UserError(String key, TagResolver... args) {
        super(key);
        this.key = key;
        this.args = args.clone();
    }

    public String key() {
        return key;
    }

    /** The message as the player sees it. */
    public Component component() {
        return Lang.message(key, args);
    }

    @Override
    public String getMessage() {
        return Lang.text(key, args);
    }

    /** What to show for a refused command: a UserError in its own colours, anything else in red. */
    public static Component describe(IllegalArgumentException failure) {
        if (failure instanceof UserError error) return error.component();
        return Component.text(String.valueOf(failure.getMessage()), net.kyori.adventure.text.format.NamedTextColor.RED);
    }
}
