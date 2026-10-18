package com.github.blade.hvac.config;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable, validated settings for the climate HUD and placeholders. Kept
 * apart from {@link HvacSettings} so the simulation tuning record does not
 * grow with presentation options. Every key has a default, so a config.yml
 * written before 1.8 loads unchanged.
 */
public record HudSettings(
        boolean enabled,
        Style style,
        long updateTicks,
        boolean celsius,
        String title,
        List<String> sidebarLines,
        List<String> sidebarYieldTo,
        String actionBarFormat,
        String bossBarFormat,
        int barLength,
        String barFilled,
        String barEmpty,
        String barEmptyColor,
        double barColdF,
        double barHotF,
        Band cold,
        Band cool,
        Band comfortable,
        Band warm,
        Band hot,
        String noneText
) {
    /** Where the built-in overlay is drawn. */
    public enum Style {
        SIDEBAR, ACTIONBAR, BOSSBAR;

        static Style parse(String value, Style fallback) {
            if (value == null) return fallback;
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (Style style : values()) if (style.name().equals(normalized)) return style;
            throw new IllegalArgumentException("hud.style must be sidebar, actionbar or bossbar");
        }
    }

    /**
     * A comfort band. {@code limitF} is the upper bound for cold and cool, the
     * lower bound for warm and hot, and unused for comfortable.
     */
    public record Band(double limitF, String label, String color) {
        public Band {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(color, "color");
            if (TextColor.fromHexString(color) == null)
                throw new IllegalArgumentException("comfort colors must be #RRGGBB, got '" + color + "'");
        }

        public TextColor textColor() { return Objects.requireNonNull(TextColor.fromHexString(color)); }
    }

    public HudSettings {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(actionBarFormat, "actionbar format");
        Objects.requireNonNull(bossBarFormat, "bossbar format");
        Objects.requireNonNull(barFilled, "bar filled glyph");
        Objects.requireNonNull(barEmpty, "bar empty glyph");
        Objects.requireNonNull(noneText, "none text");
        sidebarLines = List.copyOf(Objects.requireNonNull(sidebarLines, "sidebar lines"));
        sidebarYieldTo = List.copyOf(Objects.requireNonNull(sidebarYieldTo, "sidebar yield-to plugins"));
        if (updateTicks < 1) throw new IllegalArgumentException("hud.update-ticks must be at least 1");
        if (sidebarLines.isEmpty() || sidebarLines.size() > 15)
            throw new IllegalArgumentException("hud.sidebar.lines must have 1 to 15 lines");
        if (barLength < 1 || barLength > 40) throw new IllegalArgumentException("hud.bar.length must be 1 to 40");
        if (barFilled.isEmpty() || barEmpty.isEmpty())
            throw new IllegalArgumentException("hud.bar glyphs must not be empty");
        if (TextColor.fromHexString(barEmptyColor) == null)
            throw new IllegalArgumentException("hud.bar.empty-color must be #RRGGBB");
        if (!Double.isFinite(barColdF) || !Double.isFinite(barHotF) || barColdF >= barHotF)
            throw new IllegalArgumentException("hud.bar.cold-f must be below hud.bar.hot-f");
        Objects.requireNonNull(cold, "cold"); Objects.requireNonNull(cool, "cool");
        Objects.requireNonNull(comfortable, "comfortable"); Objects.requireNonNull(warm, "warm");
        Objects.requireNonNull(hot, "hot");
        if (!(cold.limitF() <= cool.limitF() && cool.limitF() <= warm.limitF() && warm.limitF() <= hot.limitF()))
            throw new IllegalArgumentException(
                    "hud.comfort limits must rise: cold.below <= cool.below <= warm.above <= hot.above");
    }

    public static HudSettings defaults() {
        return new HudSettings(true, Style.SIDEBAR, 10, false,
                "<gradient:#3999fb:#f69500><bold>Climate</bold></gradient> <dark_gray>-</dark_gray> <gray>EZvac",
                List.of(
                        "<gray>Here: <white>{temp}",
                        "{bar} {comfort}",
                        "<gray>Outside: <white>{outdoor}",
                        "",
                        "<gray>System: <white>{group}",
                        "<gray>Mode: <white>{mode}",
                        "<gray>Airflow: <white>{zone}",
                        "<dark_gray><font:uniform>Version: {version}</font>"),
                List.of("TAB", "FeatherBoard", "AnimatedScoreboard", "SimpleScore", "KiteBoard",
                        "QuickBoard", "TitleManager", "BetterScoreboard"),
                "<white>{temp}</white> {bar} {comfort} <dark_gray>|</dark_gray> <gray>{group}",
                "<white>{temp}</white> <gray>-</gray> {comfort} <dark_gray>|</dark_gray> <gray>{group}",
                10, "▮", "▯", "#4a4a4a", 50.0, 95.0,
                new Band(60.0, "Cold", "#5ab0ff"),
                new Band(68.0, "Cool", "#8fd3ff"),
                new Band(0.0, "Comfortable", "#6ee07a"),
                new Band(78.0, "Warm", "#ffb347"),
                new Band(86.0, "Hot", "#ff5c5c"),
                "none");
    }

    public static HudSettings load(FileConfiguration config) {
        HudSettings d = defaults();
        ConfigurationSection hud = config.getConfigurationSection("hud");
        if (hud == null) return d;
        String units = hud.getString("units", "F").trim().toUpperCase(Locale.ROOT);
        if (!units.equals("F") && !units.equals("C"))
            throw new IllegalArgumentException("hud.units must be F or C");
        List<String> lines = hud.isList("sidebar.lines") ? hud.getStringList("sidebar.lines") : d.sidebarLines;
        return new HudSettings(
                hud.getBoolean("enabled", d.enabled),
                Style.parse(hud.getString("style"), d.style),
                hud.getLong("update-ticks", d.updateTicks),
                units.equals("C"),
                hud.getString("sidebar.title", d.title),
                lines,
                hud.isList("sidebar.yield-to-plugins") ? hud.getStringList("sidebar.yield-to-plugins")
                        : d.sidebarYieldTo,
                hud.getString("actionbar.format", d.actionBarFormat),
                hud.getString("bossbar.format", d.bossBarFormat),
                hud.getInt("bar.length", d.barLength),
                hud.getString("bar.filled", d.barFilled),
                hud.getString("bar.empty", d.barEmpty),
                hud.getString("bar.empty-color", d.barEmptyColor),
                hud.getDouble("bar.cold-f", d.barColdF),
                hud.getDouble("bar.hot-f", d.barHotF),
                band(hud, "comfort.cold", "below-f", d.cold),
                band(hud, "comfort.cool", "below-f", d.cool),
                band(hud, "comfort.comfortable", null, d.comfortable),
                band(hud, "comfort.warm", "above-f", d.warm),
                band(hud, "comfort.hot", "above-f", d.hot),
                hud.getString("none-text", d.noneText));
    }

    private static Band band(ConfigurationSection hud, String path, String limitKey, Band fallback) {
        ConfigurationSection section = hud.getConfigurationSection(path);
        if (section == null) return fallback;
        return new Band(limitKey == null ? fallback.limitF() : section.getDouble(limitKey, fallback.limitF()),
                section.getString("label", fallback.label()), section.getString("color", fallback.color()));
    }
}
