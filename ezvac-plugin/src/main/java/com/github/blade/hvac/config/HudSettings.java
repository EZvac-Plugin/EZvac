package com.github.blade.hvac.config;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
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
        String noneText,
        Medium water,
        List<String> hiddenSections
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

    /**
     * What the HUD shows in one medium. The top-level 1.8 settings are the air
     * medium; {@code hud.water} holds the water one, because a pool reads on
     * its own scale: 84 F is a pleasant pool and a warm room.
     */
    public record Medium(List<String> sidebarLines, String actionBarFormat, String bossBarFormat,
                         double barColdF, double barHotF,
                         Band cold, Band cool, Band comfortable, Band warm, Band hot) {
        public Medium {
            sidebarLines = List.copyOf(Objects.requireNonNull(sidebarLines, "sidebar lines"));
            Objects.requireNonNull(actionBarFormat, "actionbar format");
            Objects.requireNonNull(bossBarFormat, "bossbar format");
            if (sidebarLines.isEmpty() || sidebarLines.size() > 15)
                throw new IllegalArgumentException("hud sidebar lines must have 1 to 15 lines");
            if (!Double.isFinite(barColdF) || !Double.isFinite(barHotF) || barColdF >= barHotF)
                throw new IllegalArgumentException("hud bar cold-f must be below hot-f");
            Objects.requireNonNull(cold, "cold"); Objects.requireNonNull(cool, "cool");
            Objects.requireNonNull(comfortable, "comfortable"); Objects.requireNonNull(warm, "warm");
            Objects.requireNonNull(hot, "hot");
            if (!(cold.limitF() <= cool.limitF() && cool.limitF() <= warm.limitF() && warm.limitF() <= hot.limitF()))
                throw new IllegalArgumentException(
                        "hud comfort limits must rise: cold.below <= cool.below <= warm.above <= hot.above");
        }
    }

    /** The air medium, built from the top-level settings. */
    public Medium air() {
        return new Medium(sidebarLines, actionBarFormat, bossBarFormat, barColdF, barHotF,
                cold, cool, comfortable, warm, hot);
    }

    public HudSettings {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(actionBarFormat, "actionbar format");
        Objects.requireNonNull(bossBarFormat, "bossbar format");
        Objects.requireNonNull(barFilled, "bar filled glyph");
        Objects.requireNonNull(barEmpty, "bar empty glyph");
        Objects.requireNonNull(noneText, "none text");
        Objects.requireNonNull(water, "water medium");
        hiddenSections = Objects.requireNonNull(hiddenSections, "hidden sections").stream()
                .map(id -> id.trim().toLowerCase(Locale.ROOT).replace(' ', '_')).filter(id -> !id.isEmpty()).toList();
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

    /** The season line added to the sidebar in 2.1, right after the outdoor line. */
    public static final String SEASON_LINE = "<gray>Season: <white>{season} <gray>day {season_day}";
    private static final String OUTSIDE_LINE = "<gray>Outside: <white>{outdoor} <gray>{weather}";

    /**
     * When the seasons first arrive in a server's config.yml, its HUD should
     * show them too. The updater never changes a list a server already has,
     * so a sidebar that is still the stock one gains the season line here; one
     * the server has made its own is left exactly as it is.
     */
    public static void addSeasonLine(YamlConfiguration config, List<String> added) {
        if (added.stream().noneMatch(path -> path.startsWith("seasons."))) return;
        List<String> lines = config.getStringList("hud.sidebar.lines");
        List<String> stock = new ArrayList<>(defaults().sidebarLines());
        stock.remove(SEASON_LINE);
        stock.remove(ADDONS_LINE);
        stock.remove(AIR_LINE);    // arrives after, in addAirLine
        if (!lines.equals(stock)) return;
        List<String> withSeason = new ArrayList<>(lines);
        withSeason.add(withSeason.indexOf(OUTSIDE_LINE) + 1, SEASON_LINE);
        config.set("hud.sidebar.lines", withSeason);
        added.add("hud.sidebar.lines");
    }

    /** The air line added to the sidebar in 3.2, right after the humidity line. */
    public static final String AIR_LINE = "<gray>Air: {aqi} <white>{aqi_band} <gray>· Pollen <white>{pollen}";
    private static final String HUMIDITY_LINE = "<gray>Humidity: <white>{humidity}";

    /**
     * When air quality first arrives in a server's config.yml (3.2), a
     * sidebar that is still the stock one gains the air line, as the season
     * line arrived in 2.1. One the server has made its own is left as it is.
     */
    public static void addAirLine(YamlConfiguration config, List<String> added) {
        if (added.stream().noneMatch(path -> path.startsWith("air-quality."))) return;
        List<String> lines = config.getStringList("hud.sidebar.lines");
        List<String> stock = new ArrayList<>(defaults().sidebarLines());
        stock.remove(AIR_LINE);
        if (!lines.equals(stock) || !lines.contains(HUMIDITY_LINE)) return;
        List<String> withAir = new ArrayList<>(lines);
        withAir.add(withAir.indexOf(HUMIDITY_LINE) + 1, AIR_LINE);
        config.set("hud.sidebar.lines", withAir);
        added.add("hud.sidebar.lines");
    }

    /** Where addon HUD sections go (API 1.1): alone on a sidebar line. */
    public static final String ADDONS_LINE = "{addons}";
    private static final String WATER_AIR_LINE = "<gray>Air: <white>{outdoor} <gray>{weather}";

    /**
     * When addon sections first arrive (2.1, with {@code hud.sections}), a
     * sidebar that is still the stock one gains the {@code {addons}} line:
     * under the season line in air, under the air line in water. A sidebar
     * the server has made its own is left exactly as it is.
     */
    public static void addAddonsLine(YamlConfiguration config, List<String> added) {
        if (added.stream().noneMatch(path -> path.startsWith("hud.sections"))) return;
        List<String> beforeAir = new ArrayList<>(defaults().sidebarLines());
        beforeAir.remove(AIR_LINE);    // arrives after, in addAirLine
        addAddonsLine(config, "hud.sidebar.lines", beforeAir, SEASON_LINE, added);
        addAddonsLine(config, "hud.water.sidebar.lines", defaultWater().sidebarLines(), WATER_AIR_LINE, added);
    }

    private static void addAddonsLine(YamlConfiguration config, String path, List<String> defaults, String after,
                                      List<String> added) {
        List<String> stock = new ArrayList<>(defaults);
        stock.remove(ADDONS_LINE);
        if (!config.getStringList(path).equals(stock)) return;
        List<String> lines = new ArrayList<>(stock);
        lines.add(lines.indexOf(after) + 1, ADDONS_LINE);
        config.set(path, lines);
        added.add(path);
    }

    public static HudSettings defaults() {
        return new HudSettings(true, Style.SIDEBAR, 10, false,
                "<gradient:#3999fb:#f69500><bold>Climate</bold></gradient> <dark_gray>-</dark_gray> <gray>EZvac",
                List.of(
                        "<gray>Here: <white>{temp}",
                        "<gray>Feels like: <white>{feels}",
                        "{bar} {comfort}",
                        HUMIDITY_LINE,
                        AIR_LINE,
                        "<gray>Outside: <white>{outdoor} <gray>{weather}",
                        SEASON_LINE,
                        ADDONS_LINE,
                        "",
                        "<gray>System: <white>{system}",
                        "<gray>Mode: <white>{mode}",
                        "<gray>Airflow: <white>{zone}",
                        "<dark_gray><font:uniform>Version: {version}</font>"),
                List.of("TAB", "FeatherBoard", "AnimatedScoreboard", "SimpleScore", "KiteBoard",
                        "QuickBoard", "TitleManager", "BetterScoreboard"),
                "<white>{temp}</white> {bar} {comfort} <dark_gray>|</dark_gray> <gray>{humidity}"
                        + " <dark_gray>|</dark_gray> <gray>{system}",
                "<white>{temp}</white> <gray>feels {feels} -</gray> {comfort} <dark_gray>|</dark_gray> <gray>{system}",
                10, "▮", "▯", "#4a4a4a", 50.0, 95.0,
                new Band(60.0, "Cold", "#5ab0ff"),
                new Band(68.0, "Cool", "#8fd3ff"),
                new Band(0.0, "Comfortable", "#6ee07a"),
                new Band(78.0, "Warm", "#ffb347"),
                new Band(86.0, "Hot", "#ff5c5c"),
                "none",
                defaultWater(),
                List.of());
    }

    private static Medium defaultWater() {
        return new Medium(
                List.of(
                        "<gray>Water: <white>{temp}",
                        "{bar} {comfort}",
                        WATER_AIR_LINE,
                        ADDONS_LINE,
                        "",
                        "<gray>Pool: <white>{system}",
                        "<gray>Mode: <white>{mode}",
                        "<gray>Circulation: <white>{zone}",
                        "<dark_gray><font:uniform>Version: {version}</font>"),
                "<aqua>Water</aqua> <white>{temp}</white> {bar} {comfort} <dark_gray>|</dark_gray> <gray>{system}",
                "<aqua>Water</aqua> <white>{temp}</white> <gray>-</gray> {comfort} <dark_gray>|</dark_gray> <gray>{system}",
                60.0, 104.0,
                new Band(70.0, "Cold", "#5ab0ff"),
                new Band(78.0, "Cool", "#8fd3ff"),
                new Band(0.0, "Comfortable", "#6ee07a"),
                new Band(86.0, "Warm", "#ffb347"),
                new Band(95.0, "Hot tub", "#ff5c5c"));
    }

    public static HudSettings load(FileConfiguration config) {
        HudSettings d = defaults();
        ConfigurationSection hud = config.getConfigurationSection("hud");
        // A config without a hud section still follows the plugin-wide units.
        if (hud == null) hud = new org.bukkit.configuration.MemoryConfiguration();
        // hud.units is no longer read: the plugin-wide units setting (2.1) decides.
        boolean celsius = TemperatureUnit.load(config, ignored -> { }) == TemperatureUnit.C;
        List<String> lines = hud.isList("sidebar.lines") ? hud.getStringList("sidebar.lines") : d.sidebarLines;
        return new HudSettings(
                hud.getBoolean("enabled", d.enabled),
                Style.parse(hud.getString("style"), d.style),
                hud.getLong("update-ticks", d.updateTicks),
                celsius,
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
                hud.getString("none-text", d.noneText),
                water(hud.getConfigurationSection("water"), d.water),
                hud.isList("sections.hidden") ? hud.getStringList("sections.hidden") : d.hiddenSections);
    }

    private static Medium water(ConfigurationSection water, Medium d) {
        if (water == null) return d;
        return new Medium(
                water.isList("sidebar.lines") ? water.getStringList("sidebar.lines") : d.sidebarLines(),
                water.getString("actionbar.format", d.actionBarFormat()),
                water.getString("bossbar.format", d.bossBarFormat()),
                water.getDouble("bar.cold-f", d.barColdF()),
                water.getDouble("bar.hot-f", d.barHotF()),
                band(water, "comfort.cold", "below-f", d.cold()),
                band(water, "comfort.cool", "below-f", d.cool()),
                band(water, "comfort.comfortable", null, d.comfortable()),
                band(water, "comfort.warm", "above-f", d.warm()),
                band(water, "comfort.hot", "above-f", d.hot()));
    }

    private static Band band(ConfigurationSection hud, String path, String limitKey, Band fallback) {
        ConfigurationSection section = hud.getConfigurationSection(path);
        if (section == null) return fallback;
        return new Band(limitKey == null ? fallback.limitF() : section.getDouble(limitKey, fallback.limitF()),
                section.getString("label", fallback.label()), section.getString("color", fallback.color()));
    }
}
