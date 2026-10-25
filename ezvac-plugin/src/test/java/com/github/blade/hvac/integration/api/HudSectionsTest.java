package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.hud.HudFormatter;
import com.github.blade.hvac.hud.SectionFrame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** The registry behind the API's HUD sections. */
class HudSectionsTest {
    private final Logger logger = Logger.getAnonymousLogger();
    private final HudSections sections = new HudSections(logger, new HudFormatter(HudSettings.defaults()),
            (player, reading) -> { throw new AssertionError("no section here reads the climate"); });

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static String plain(Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }

    private static List<String> plain(List<Component> lines) {
        return lines.stream().map(HudSectionsTest::plain).toList();
    }

    @Test
    void idsAreThePluginAndTheKeyInLowercase() {
        Plugin addon = plugin("LiveExposure");
        assertEquals("liveexposure:body", sections.register(addon, "body", context -> List.of()));
        assertEquals(List.of("liveexposure:body"), sections.sections());
        assertThrows(IllegalArgumentException.class, () -> sections.register(addon, "Body", context -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> sections.register(addon, "", context -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> sections.register(addon, "a:b", context -> List.of()));
    }

    @Test
    void registeringAgainReplacesAndOrderIsKept() {
        Plugin a = plugin("A");
        Plugin b = plugin("B");
        sections.register(a, "one", context -> List.of(Component.text("old")));
        sections.register(b, "two", context -> List.of(Component.text("B")));
        sections.register(a, "one", context -> List.of(Component.text("new")));
        assertEquals(List.of("a:one", "b:two"), sections.sections());
        SectionFrame frame = sections.frame(null, null);
        assertEquals(List.of("new"), plain(frame.lines("a:one")));
        assertEquals("new", plain(frame.inline("a:one")));
        sections.unregister(a, "one");
        assertEquals(List.of("b:two"), sections.sections());
    }

    @Test
    void aDisabledPluginTakesItsSectionsWithIt() {
        Plugin a = plugin("A");
        Plugin b = plugin("B");
        sections.register(a, "one", context -> List.of());
        sections.register(a, "two", context -> List.of());
        sections.register(b, "three", context -> List.of());
        sections.forget(a);
        assertEquals(List.of("b:three"), sections.sections());
        assertTrue(sections.any());
        sections.clear();
        assertFalse(sections.any());
        assertSame(SectionFrame.NONE, sections.frame(null, null));
    }

    @Test
    void aBrokenSectionIsLeftOutAndLoggedOnce() {
        List<LogRecord> logged = new ArrayList<>();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logged.add(record); }
            @Override public void flush() {}
            @Override public void close() {}
        });
        Plugin a = plugin("A");
        sections.register(a, "broken", context -> { throw new IllegalStateException("oops"); });
        sections.register(a, "fine", context -> List.of(Component.text("ok")));
        for (int update = 0; update < 3; update++) {
            SectionFrame frame = sections.frame(null, null);
            assertEquals(List.of(), frame.lines("a:broken"));
            assertEquals(Component.empty(), frame.inline("a:broken"));
            assertEquals(List.of("ok"), plain(frame.lines("a:fine")));
        }
        assertEquals(1, logged.size(), "logged once, not every update");
        assertTrue(logged.get(0).getMessage().contains("a:broken"));

        // Once it works again, a new failure is worth telling the admin about.
        boolean[] broken = {false};
        sections.register(a, "flaky", context -> {
            if (broken[0]) throw new IllegalStateException("again");
            return List.of(Component.text("ok"));
        });
        broken[0] = true;
        sections.frame(null, null).lines("a:flaky");
        broken[0] = false;
        sections.frame(null, null).lines("a:flaky");
        broken[0] = true;
        sections.frame(null, null).lines("a:flaky");
        assertEquals(3, logged.size(), "logged again after it had recovered");
    }

    @Test
    void aSectionIsDrawnOncePerFrame() {
        int[] calls = {0};
        sections.register(plugin("A"), "one", context -> { calls[0]++; return List.of(Component.text("x")); });
        SectionFrame frame = sections.frame(null, null);
        frame.lines("a:one");
        frame.lines("a:one");
        assertEquals(1, calls[0]);
        assertEquals(List.of(), frame.lines("nobody:here"));
    }
}
