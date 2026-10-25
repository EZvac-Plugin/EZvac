package com.github.blade.hvac.integration.api;

import com.github.blade.hvac.api.ClimateReading;
import com.github.blade.hvac.api.hud.EzvacHud;
import com.github.blade.hvac.api.hud.HudContext;
import com.github.blade.hvac.api.hud.HudSection;
import com.github.blade.hvac.hud.HudFormatter;
import com.github.blade.hvac.hud.SectionFrame;
import com.github.blade.hvac.hud.SectionSource;
import com.github.blade.hvac.service.AirflowService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Addon sections on the climate HUD (API 1.1): the registry behind
 * {@link EzvacHud}, and the frames the HUD draws them from. Main thread only.
 */
public final class HudSections implements EzvacHud, SectionSource, Listener {
    private static final Pattern KEY = Pattern.compile("[a-z0-9_.-]+");

    private record Entry(Plugin owner, HudSection section) {}

    private final Logger logger;
    private final HudFormatter format;
    /** Turns EZvac's reading into the API's, for one player. */
    private final ReadingView view;
    private final Map<String, Entry> sections = new LinkedHashMap<>();
    /** Sections that have thrown, logged once each until they are registered again. */
    private final Set<String> failed = new HashSet<>();

    /** Builds the API reading a section sees. */
    @FunctionalInterface
    public interface ReadingView {
        ClimateReading reading(Player player, AirflowService.TemperatureReading reading);
    }

    public HudSections(Logger logger, HudFormatter format, ReadingView view) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.format = Objects.requireNonNull(format, "format");
        this.view = Objects.requireNonNull(view, "view");
    }

    /** The id a section gets: {@code <plugin name>:<key>}, lowercase. */
    static String id(Plugin owner, String key) {
        return HudFormatter.sectionId(owner.getName()) + ":" + key;
    }

    @Override
    public String register(Plugin owner, String key, HudSection section) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(section, "section");
        if (key == null || !KEY.matcher(key).matches())
            throw new IllegalArgumentException("A HUD section key is lowercase letters, digits, _ - or . : " + key);
        String id = id(owner, key);
        sections.put(id, new Entry(owner, section));
        failed.remove(id);
        return id;
    }

    @Override
    public void unregister(Plugin owner, String key) {
        if (owner == null || key == null) return;
        String id = id(owner, key);
        sections.remove(id);
        failed.remove(id);
    }

    @Override
    public List<String> sections() {
        return List.copyOf(sections.keySet());
    }

    /** Forgets every section, when EZvac stops. */
    public void clear() {
        sections.clear();
        failed.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        forget(event.getPlugin());
    }

    /** Removes every section a plugin registered. */
    void forget(Plugin owner) {
        sections.values().removeIf(entry -> entry.owner() == owner);
    }

    @Override
    public List<String> ids() {
        return sections();
    }

    @Override
    public boolean any() {
        return !sections.isEmpty();
    }

    @Override
    public SectionFrame frame(Player player, AirflowService.TemperatureReading reading) {
        if (sections.isEmpty()) return SectionFrame.NONE;
        return new Frame(new Context(player, () -> view.reading(player, reading), format));
    }

    /** One player's update: each section drawn at most once, and only when asked for. */
    private final class Frame implements SectionFrame {
        private final Context context;
        private final List<String> ids = List.copyOf(sections.keySet());
        private final Map<String, List<Component>> lines = new HashMap<>();
        private final Map<String, Component> inline = new HashMap<>();

        Frame(Context context) { this.context = context; }

        @Override public List<String> ids() { return ids; }

        @Override
        public List<Component> lines(String id) {
            return lines.computeIfAbsent(id, key -> draw(key, entry -> {
                List<Component> drawn = entry.section().lines(context);
                return drawn == null ? List.of() : new ArrayList<>(drawn);
            }, List.of()));
        }

        @Override
        public Component inline(String id) {
            return inline.computeIfAbsent(id, key -> draw(key, entry -> {
                Component drawn = entry.section().inline(context);
                return drawn == null ? Component.empty() : drawn;
            }, Component.empty()));
        }

        private <T> T draw(String id, Function<Entry, T> drawing, T none) {
            Entry entry = sections.get(id);
            if (entry == null) return none;
            try {
                T drawn = drawing.apply(entry);
                failed.remove(id);
                return drawn;
            } catch (RuntimeException | LinkageError exception) {
                // An addon's mistake must never take the HUD down with it. Logged once while it keeps failing.
                if (failed.add(id))
                    logger.log(Level.WARNING, "The HUD section " + id + " from " + entry.owner().getName()
                            + " failed; it is left out for as long as it keeps failing", exception);
                return none;
            }
        }
    }

    /** What a section sees. The API reading is built once, the first time a section asks. */
    private static final class Context implements HudContext {
        private final Player player;
        private final Supplier<ClimateReading> source;
        private final HudFormatter format;
        private ClimateReading reading;

        Context(Player player, Supplier<ClimateReading> source, HudFormatter format) {
            this.player = player;
            this.source = source;
            this.format = format;
        }

        @Override public Player player() { return player; }

        @Override
        public ClimateReading reading() {
            if (reading == null) reading = source.get();
            return reading;
        }

        @Override public String temperature(double fahrenheit) { return format.temperature(fahrenheit); }

        @Override public Component bar(double fraction, TextColor color) { return format.bar(fraction, color); }
    }
}
