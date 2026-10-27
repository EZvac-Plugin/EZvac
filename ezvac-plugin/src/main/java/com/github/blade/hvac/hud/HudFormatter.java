package com.github.blade.hvac.hud;

import com.github.blade.hvac.config.HudSettings;
import com.github.blade.hvac.config.TemperatureUnit;
import com.github.blade.hvac.service.AirText;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a {@link ClimateSnapshot} into HUD text. Pure: no server state. */
public final class HudFormatter {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    /** {@code {section:<id>}}: one addon section (API 1.1). */
    private static final Pattern SECTION = Pattern.compile("\\{section:([a-z0-9_.-]+:[a-z0-9_.-]+)}", Pattern.CASE_INSENSITIVE);
    /** Where every addon section not placed on its own goes, alone on a sidebar line. */
    public static final String ADDONS = HudSettings.ADDONS_LINE;
    private static final Pattern RELEASE =
            Pattern.compile("ALPHA-([0-9]+(?:[.][0-9]+)*)(?:-HOTFIX([0-9]+))?(?:-P([0-9]+))?", Pattern.CASE_INSENSITIVE);

    private final HudSettings settings;
    private final HudSettings.Medium air;
    private final String version;

    public HudFormatter(HudSettings settings) {
        this(settings, "");
    }

    /** @param pluginVersion the full plugin version, shown short through {version} */
    public HudFormatter(HudSettings settings, String pluginVersion) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.air = settings.air();
        this.version = shortVersion(pluginVersion);
    }

    /**
     * ALPHA-1.8 becomes A1.8, ALPHA-1.8-HOTFIX1 becomes A1.8_H1, and
     * ALPHA-1.6-P1 becomes A1.6_P1. Anything else is shown as it is.
     */
    public static String shortVersion(String version) {
        if (version == null) return "";
        Matcher match = RELEASE.matcher(version.trim());
        if (!match.matches()) return version.trim();
        StringBuilder out = new StringBuilder("A").append(match.group(1));
        if (match.group(2) != null) out.append("_H").append(match.group(2));
        if (match.group(3) != null) out.append("_P").append(match.group(3));
        return out.toString();
    }

    public String version() { return version; }

    public HudSettings settings() { return settings; }

    // ---- values ----

    /** A temperature in the configured units, such as "72.4°F". */
    public String temperature(double fahrenheit) {
        return (settings.celsius() ? TemperatureUnit.C : TemperatureUnit.F).degrees(fahrenheit);
    }

    public static String fahrenheit(double fahrenheit) {
        return TemperatureUnit.F.degrees(fahrenheit);
    }

    public static String celsius(double fahrenheit) {
        return TemperatureUnit.C.degrees(fahrenheit);
    }

    /** The settings for the snapshot's medium: water readings use the water scale and wording. */
    public HudSettings.Medium medium(ClimateSnapshot snapshot) {
        return snapshot.water() ? settings.water() : air;
    }

    public HudSettings.Band band(double fahrenheit) { return band(fahrenheit, air); }

    public HudSettings.Band band(double fahrenheit, HudSettings.Medium medium) {
        if (fahrenheit < medium.cold().limitF()) return medium.cold();
        if (fahrenheit < medium.cool().limitF()) return medium.cool();
        if (fahrenheit > medium.hot().limitF()) return medium.hot();
        if (fahrenheit > medium.warm().limitF()) return medium.warm();
        return medium.comfortable();
    }

    /** Air is judged by how it feels (humidity and wind included); water by its temperature. */
    public HudSettings.Band band(ClimateSnapshot snapshot) {
        return band(snapshot.comfortF(), medium(snapshot));
    }

    /** 0 at the bar's cold end, 1 at its hot end, clamped. */
    public double fraction(double fahrenheit) { return fraction(fahrenheit, air); }

    public double fraction(double fahrenheit, HudSettings.Medium medium) {
        double value = (fahrenheit - medium.barColdF()) / (medium.barHotF() - medium.barColdF());
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    public double fraction(ClimateSnapshot snapshot) { return fraction(snapshot.comfortF(), medium(snapshot)); }

    private int filledSegments(double fahrenheit, HudSettings.Medium medium) {
        return (int) Math.round(fraction(fahrenheit, medium) * settings.barLength());
    }

    public String barPlain(double fahrenheit) { return barPlain(fahrenheit, air); }

    public String barPlain(ClimateSnapshot snapshot) { return barPlain(snapshot.comfortF(), medium(snapshot)); }

    private String barPlain(double fahrenheit, HudSettings.Medium medium) {
        int filled = filledSegments(fahrenheit, medium);
        return settings.barFilled().repeat(filled) + settings.barEmpty().repeat(settings.barLength() - filled);
    }

    /** The bar as MiniMessage: filled part in the comfort colour, the rest dimmed. */
    public String barMini(double fahrenheit) { return barMini(fahrenheit, air); }

    private String barMini(double fahrenheit, HudSettings.Medium medium) {
        int filled = filledSegments(fahrenheit, medium);
        StringBuilder out = new StringBuilder();
        if (filled > 0)
            out.append("<color:").append(band(fahrenheit, medium).color()).append('>')
                    .append(escape(settings.barFilled().repeat(filled))).append("</color>");
        if (filled < settings.barLength())
            out.append("<color:").append(settings.barEmptyColor()).append('>')
                    .append(escape(settings.barEmpty().repeat(settings.barLength() - filled))).append("</color>");
        return out.toString();
    }

    /** A bar of the HUD's glyphs and length filled to {@code fraction} (clamped) in {@code color}: for addon meters. */
    public Component bar(double fraction, TextColor color) {
        double clamped = Double.isNaN(fraction) ? 0.0 : Math.max(0.0, Math.min(1.0, fraction));
        int filled = (int) Math.round(clamped * settings.barLength());
        StringBuilder out = new StringBuilder();
        if (filled > 0)
            out.append("<color:").append(color == null ? TextColor.color(0xFFFFFF).asHexString() : color.asHexString())
                    .append('>').append(escape(settings.barFilled().repeat(filled))).append("</color>");
        if (filled < settings.barLength())
            out.append("<color:").append(settings.barEmptyColor()).append('>')
                    .append(escape(settings.barEmpty().repeat(settings.barLength() - filled))).append("</color>");
        return MINI.deserialize(out.toString());
    }

    /** The bar with legacy section-sign colours, for placeholder consumers. */
    public String barLegacy(double fahrenheit) { return legacy(barMini(fahrenheit, air)); }

    public String barLegacy(ClimateSnapshot snapshot) { return legacy(barMini(snapshot.comfortF(), medium(snapshot))); }

    private static String legacy(String mini) {
        return LegacyComponentSerializer.legacySection().serialize(MINI.deserialize(mini));
    }

    private String comfortMini(double fahrenheit, HudSettings.Medium medium) {
        HudSettings.Band band = band(fahrenheit, medium);
        return "<color:" + band.color() + ">" + escape(band.label()) + "</color>";
    }

    public String system(ClimateSnapshot snapshot) {
        return snapshot.system() == null ? settings.noneText() : snapshot.system();
    }

    public String thermostat(ClimateSnapshot snapshot) {
        return snapshot.thermostat() == null ? settings.noneText() : snapshot.thermostat();
    }

    /** "Cooling to 70.0°F", "Idle", or the none text where nothing conditions the air. */
    public String mode(ClimateSnapshot snapshot) {
        if (snapshot.mode() == null) return settings.noneText();
        String mode = capitalize(snapshot.mode());
        if (Double.isNaN(snapshot.targetF()) || snapshot.mode().equals("IDLE")) return mode;
        return mode + " to " + temperature(snapshot.targetF());
    }

    /** "45%", or the none text in water or where humidity is unknown. */
    /** The AQI number (3.2), or the none-text where the air isn't measured. */
    public String aqi(ClimateSnapshot snapshot) {
        return snapshot.air().known() ? AirText.aqiNumber(snapshot.air()) : settings.noneText();
    }

    /** The AQI in its band's colour, for MiniMessage templates. */
    private String aqiMini(ClimateSnapshot snapshot) {
        if (!snapshot.air().known()) return escape(settings.noneText());
        String color = NamedTextColor.NAMES.key(AirText.color(snapshot.air()));
        return "<" + color + ">" + AirText.aqiNumber(snapshot.air()) + "</" + color + ">";
    }

    /** The AQI coloured with legacy codes, for scoreboard plugins. */
    public String aqiLegacy(ClimateSnapshot snapshot) {
        if (!snapshot.air().known()) return settings.noneText();
        return AirText.legacyColor(snapshot.air()) + AirText.aqiNumber(snapshot.air()) + "§r";
    }

    /** Good, Moderate, Sensitive, Unhealthy, Very bad or Hazardous; empty where the air isn't measured. */
    public String aqiBand(ClimateSnapshot snapshot) {
        return snapshot.air().known() ? AirText.band(snapshot.air()).shortName() : "";
    }

    /** {@code Calm}, or {@code Windy 22 km/h} (3.2); the none-text where there is no wind to read. */
    public String wind(ClimateSnapshot snapshot) {
        return snapshot.windLevel().isEmpty() ? settings.noneText() : snapshot.wind();
    }

    /** Calm, Breezy, Windy or Gale; the none-text where there is no wind to read. */
    public String windLevel(ClimateSnapshot snapshot) {
        return snapshot.windLevel().isEmpty() ? settings.noneText() : snapshot.windLevel();
    }

    public String pollen(ClimateSnapshot snapshot) {
        return snapshot.pollen().isEmpty() ? settings.noneText() : snapshot.pollen();
    }

    public String humidity(ClimateSnapshot snapshot) {
        if (snapshot.water() || !Double.isFinite(snapshot.humidity())) return settings.noneText();
        return Math.round(snapshot.humidity()) + "%";
    }

    public String feelsLike(ClimateSnapshot snapshot) {
        return temperature(snapshot.feelsLikeF());
    }

    public String target(ClimateSnapshot snapshot) {
        return Double.isNaN(snapshot.targetF()) ? settings.noneText() : temperature(snapshot.targetF());
    }

    /** Air: "Core", "62% leakage", "Outside". Water: "Core", "62% mixing", "Natural water". */
    public String zone(ClimateSnapshot snapshot) {
        if (!snapshot.conditioned()) return snapshot.water() ? "Natural water" : "Outside";
        if (snapshot.core()) return "Core";
        return Math.round(snapshot.influence() * 100) + (snapshot.water() ? "% mixing" : "% leakage");
    }

    // ---- templates ----

    /** The sidebar lines worth showing: a season line is left out where there are no seasons. */
    public List<String> shownLines(List<String> templates, ClimateSnapshot snapshot) {
        if (!snapshot.season().isEmpty()) return templates;
        return templates.stream().filter(template -> !template.contains("{season")).toList();
    }

    /** Replaces the tokens in a MiniMessage template. Plain values are escaped. */
    public String fill(String template, ClimateSnapshot snapshot) {
        HudSettings.Medium medium = medium(snapshot);
        return template
                .replace("{temp}", escape(temperature(snapshot.localF())))
                .replace("{feels}", escape(feelsLike(snapshot)))
                .replace("{humidity}", escape(humidity(snapshot)))
                .replace("{aqi_band}", escape(aqiBand(snapshot)))
                .replace("{aqi}", aqiMini(snapshot))
                .replace("{pollen}", escape(pollen(snapshot)))
                .replace("{wind_level}", escape(windLevel(snapshot)))
                .replace("{wind}", escape(wind(snapshot)))
                .replace("{weather}", escape(snapshot.weather()))
                .replace("{season_day}", snapshot.seasonDay() > 0 ? Integer.toString(snapshot.seasonDay()) : "")
                .replace("{season}", escape(snapshot.season()))
                .replace("{bar}", barMini(snapshot.comfortF(), medium))
                .replace("{comfort}", comfortMini(snapshot.comfortF(), medium))
                .replace("{outdoor}", escape(temperature(snapshot.outdoorF())))
                .replace("{system}", escape(system(snapshot)))
                .replace("{group}", escape(system(snapshot)))
                .replace("{thermostat}", escape(thermostat(snapshot)))
                .replace("{mode}", escape(mode(snapshot)))
                .replace("{target}", escape(target(snapshot)))
                .replace("{zone}", escape(zone(snapshot)))
                .replace("{version}", escape(version));
    }

    public Component render(String template, ClimateSnapshot snapshot) {
        return MINI.deserialize(fill(template, snapshot));
    }

    /**
     * A one-line template with any {@code {section:<id>}} replaced by that
     * addon section's one-line form: for the action bar and the boss bar.
     */
    public Component render(String template, ClimateSnapshot snapshot, SectionFrame sections) {
        if (!template.contains("{section:")) return render(template, snapshot);
        // Sections are found in the template itself, so a system named "{section:..}" stays a name.
        Matcher match = SECTION.matcher(template);
        StringBuilder out = new StringBuilder();
        int from = 0;
        while (match.find()) {
            out.append(fill(template.substring(from, match.start()), snapshot));
            String id = sectionId(match.group(1));
            out.append(MINI.serialize(hidden(id) ? Component.empty() : sections.inline(id)));
            from = match.end();
        }
        out.append(fill(template.substring(from), snapshot));
        return MINI.deserialize(out.toString());
    }

    /** A section id as the registry writes it: lowercase, with spaces in a plugin's name as underscores. */
    public static String sectionId(String id) {
        return id.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * The sidebar's lines for this update. Season lines go where there are no
     * seasons; a line that is just {@code {section:<id>}} becomes that addon
     * section's lines, and a line that is just {@code {addons}} becomes the
     * lines of every other section. A hidden section takes no lines.
     */
    public List<Component> lines(List<String> templates, ClimateSnapshot snapshot, SectionFrame sections) {
        List<String> shown = shownLines(templates, snapshot);
        Set<String> placed = new HashSet<>();
        int own = 0;
        for (String template : shown) {
            Matcher match = SECTION.matcher(template.trim());
            if (match.matches()) placed.add(sectionId(match.group(1)));
            else if (!template.trim().equals(ADDONS)) own++;
        }
        // The server's own lines always fit; addon sections share what is left, the first come first served.
        int room = Math.max(0, MAX_SIDEBAR_LINES - own);
        List<Component> out = new ArrayList<>(shown.size());
        for (String template : shown) {
            String trimmed = template.trim();
            Matcher match = SECTION.matcher(trimmed);
            if (match.matches()) {
                room -= addSection(out, sections, sectionId(match.group(1)), room);
            } else if (trimmed.equals(ADDONS)) {
                for (String id : sections.ids())
                    if (!placed.contains(id)) room -= addSection(out, sections, id, room);
            } else {
                out.add(render(template, snapshot));
            }
        }
        return out;
    }

    /** The sidebar shows at most this many lines. */
    public static final int MAX_SIDEBAR_LINES = 15;

    /** Adds a section's lines if they fit in {@code room}; a section is shown whole or not at all. */
    private int addSection(List<Component> out, SectionFrame sections, String id, int room) {
        if (hidden(id)) return 0;
        List<Component> lines = sections.lines(id);
        if (lines.isEmpty() || lines.size() > room) return 0;
        out.addAll(lines);
        return lines.size();
    }

    public Component title() {
        return MINI.deserialize(settings.title().replace("{version}", escape(version)));
    }

    public BossBar.Color bossBarColor(double fahrenheit) { return bossBarColor(fahrenheit, air); }

    public BossBar.Color bossBarColor(ClimateSnapshot snapshot) {
        return bossBarColor(snapshot.comfortF(), medium(snapshot));
    }

    private BossBar.Color bossBarColor(double fahrenheit, HudSettings.Medium medium) {
        HudSettings.Band band = band(fahrenheit, medium);
        if (band == medium.cold() || band == medium.cool()) return BossBar.Color.BLUE;
        if (band == medium.warm()) return BossBar.Color.YELLOW;
        if (band == medium.hot()) return BossBar.Color.RED;
        return BossBar.Color.GREEN;
    }

    /** A section the server switched off in {@code hud.sections.hidden}. */
    private boolean hidden(String id) {
        return settings.hiddenSections().contains(id);
    }

    private static String escape(String value) {
        return MINI.escapeTags(value);
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
