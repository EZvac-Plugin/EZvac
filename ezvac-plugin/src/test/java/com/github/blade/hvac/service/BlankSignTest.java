package com.github.blade.hvac.service;

import net.kyori.adventure.text.Component;
import org.bukkit.block.sign.SignSide;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** With screens on, a device sign is kept blank (BETA 1.0): from 26.1 its text shows through the screen. */
class BlankSignTest {

    /** A sign side holding four lines, with nothing else. */
    private static SignSide side(Component[] lines) {
        return (SignSide) Proxy.newProxyInstance(SignSide.class.getClassLoader(), new Class<?>[] {SignSide.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "line" -> {
                        if (args.length == 2) {
                            lines[(int) args[0]] = (Component) args[1];
                            yield null;
                        }
                        yield lines[(int) args[0]];
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void aWrittenSignIsEmptied() {
        Component[] lines = {Component.text("EZvac | Hut"), Component.text("Room 72.0 F"),
                Component.text(""), Component.text("Cooling")};
        assertTrue(DisplayService.clear(side(lines)));
        for (Component line : lines) assertEquals(Component.empty(), line);
    }

    @Test
    void aBlankSignIsLeftAlone() {
        Component[] lines = {Component.empty(), Component.text(""), Component.empty(), Component.text("")};
        assertFalse(DisplayService.clear(side(lines)), "nothing to send");
    }
}
