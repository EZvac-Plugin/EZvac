package com.github.blade.hvac.hud;

import com.github.blade.hvac.config.HudSettings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a {@link ClimateSnapshot} into HUD text. Pure: no server state. */
public final class HudFormatter {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
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
        return settings.celsius() ? celsius(fahrenheit) : fahrenheit(fahrenheit);
    }

    public static String fahrenheit(double fahrenheit) {
        return String.format(Locale.US, "%.1f°F", fahrenheit);
    }

    public static String celsius(double fahrenheit) {
        return String.format(Locale.US, "%.1f°C", (fahrenheit - 32.0) * 5.0 / 9.0);
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

    public HudSettings.Band band(ClimateSnapshot snapshot) {
        return band(snapshot.localF(), medium(snapshot));
    }

    /** 0 at the bar's cold end, 1 at its hot end, clamped. */
    public double fraction(double fahrenheit) { return fraction(fahrenheit, air); }

    public double fraction(double fahrenheit, HudSettings.Medium medium) {
        double value = (fahrenheit - medium.barColdF()) / (medium.barHotF() - medium.barColdF());
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    public double fraction(ClimateSnapshot snapshot) { return fraction(snapshot.localF(), medium(snapshot)); }

    private int filledSegments(double fahrenheit, HudSettings.Medium medium) {
        return (int) Math.round(fraction(fahrenheit, medium) * settings.barLength());
    }

    public String barPlain(double fahrenheit) { return barPlain(fahrenheit, air); }

    public String barPlain(ClimateSnapshot snapshot) { return barPlain(snapshot.localF(), medium(snapshot)); }

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

    /** The bar with legacy section-sign colours, for placeholder consumers. */
    public String barLegacy(double fahrenheit) { return legacy(barMini(fahrenheit, air)); }

    public String barLegacy(ClimateSnapshot snapshot) { return legacy(barMini(snapshot.localF(), medium(snapshot))); }

    private static String legacy(String mini) {
        return LegacyComponentSerializer.legacySection().serialize(MINI.deserialize(mini));
    }

    private String comfortMini(double fahrenheit, HudSettings.Medium medium) {
        HudSettings.Band band = band(fahrenheit, medium);
        return "<color:" + band.color() + ">" + escape(band.label()) + "</color>";
    }

    public String group(ClimateSnapshot snapshot) {
        return snapshot.group() == null ? settings.noneText() : snapshot.group();
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

    /** Replaces the tokens in a MiniMessage template. Plain values are escaped. */
    public String fill(String template, ClimateSnapshot snapshot) {
        HudSettings.Medium medium = medium(snapshot);
        return template
                .replace("{temp}", escape(temperature(snapshot.localF())))
                .replace("{bar}", barMini(snapshot.localF(), medium))
                .replace("{comfort}", comfortMini(snapshot.localF(), medium))
                .replace("{outdoor}", escape(temperature(snapshot.outdoorF())))
                .replace("{group}", escape(group(snapshot)))
                .replace("{thermostat}", escape(thermostat(snapshot)))
                .replace("{mode}", escape(mode(snapshot)))
                .replace("{target}", escape(target(snapshot)))
                .replace("{zone}", escape(zone(snapshot)))
                .replace("{version}", escape(version));
    }

    public Component render(String template, ClimateSnapshot snapshot) {
        return MINI.deserialize(fill(template, snapshot));
    }

    public Component title() {
        return MINI.deserialize(settings.title().replace("{version}", escape(version)));
    }

    public BossBar.Color bossBarColor(double fahrenheit) { return bossBarColor(fahrenheit, air); }

    public BossBar.Color bossBarColor(ClimateSnapshot snapshot) {
        return bossBarColor(snapshot.localF(), medium(snapshot));
    }

    private BossBar.Color bossBarColor(double fahrenheit, HudSettings.Medium medium) {
        HudSettings.Band band = band(fahrenheit, medium);
        if (band == medium.cold() || band == medium.cool()) return BossBar.Color.BLUE;
        if (band == medium.warm()) return BossBar.Color.YELLOW;
        if (band == medium.hot()) return BossBar.Color.RED;
        return BossBar.Color.GREEN;
    }

    private static String escape(String value) {
        return MINI.escapeTags(value);
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
