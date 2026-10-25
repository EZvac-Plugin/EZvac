# The EZvac API

From ALPHA 2.1, other plugins can use what EZvac knows about the climate:
the temperature and "feels like" anywhere and around every player, the
outdoors, thermostats and their systems, dehumidifiers and humidifiers, the
seasons, and events when things change. Addons can also add their own
sections to EZvac's climate HUD. An addon is an ordinary Paper plugin that
depends on EZvac.

API version **1.1** reads only: nothing an addon does through it changes
EZvac's climate or systems. Ways to change things (targets, power) will come
in a later API version.

[LiveExposure](https://github.com/EZvac-Plugin/LiveExposure), the first
addon, is a complete open-source example: body temperature for survival
players, with a section on the HUD.

## Adding the dependency

The API is published through [JitPack](https://jitpack.io/#EZvac-Plugin/EZvac),
built from EZvac's release tags. Use it with `provided` (Maven) or
`compileOnly` (Gradle) scope: EZvac carries the API's classes at runtime, so
never shade them into your jar.

**Maven**

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.EZvac-Plugin.EZvac</groupId>
        <artifactId>ezvac-api</artifactId>
        <version>ALPHA-2.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (Kotlin)**

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.EZvac-Plugin.EZvac:ezvac-api:ALPHA-2.1") }
```

In your `plugin.yml`, depend on EZvac so it loads first:

```yaml
depend: [EZvac]        # or softdepend: [EZvac] if your plugin also works without it
```

## Getting the API

```java
EzvacApi ezvac = EzvacApi.get();   // throws IllegalStateException if EZvac is missing
```

`EzvacApi.get()` asks Paper's services manager, where EZvac registers itself
while it's enabled. `ezvac.apiVersion()` tells you which API version the
server runs.

## What you can read

| Method | Gives |
| --- | --- |
| `readingAt(location)` | The climate at a spot, exactly as `/hvac temperature` reads it |
| `readingFor(player)` | The climate a player is in, as their HUD reads it (head first, the water when in water) |
| `latestFor(playerId)` | The reading the last player update gave; safe from any thread |
| `outdoorAt(location)` | The outdoors there: air, natural water, humidity, dew point, feels-like, weather, and how much the season shifts it |
| `thermostats()`, `thermostat(world, id)` | Thermostats: room and target temperature, mode, on/off, pool or room, humidity, running and total equipment |
| `climateUnits()` | Dehumidifiers and humidifiers: humidity, setpoint, state, power, a dehumidifier's tank |
| `season(world)` | The season and its day, or empty where there are no seasons |
| `units()` | Whether the server shows players °F or °C |

A `ClimateReading` holds the temperature, feels-like, humidity (NaN in water),
the outdoor temperature, whether the spot is in water, the weather, the
season, and which system conditions the spot and how strongly.

**Every temperature is Fahrenheit**, whatever the server's `units`. Records
have `...C()` helpers, and `Temperatures` converts.

## Events

All fired on the main thread. They are informational: none can be
cancelled.

| Event | When |
| --- | --- |
| `PlayerClimateUpdateEvent` | Every player's climate is read again, on the HUD's cadence (`hud.update-ticks`, every half second by default), with the previous reading. Fired only while some plugin listens to it |
| `ThermostatModeChangeEvent` | A system starts cooling or heating, or goes idle |
| `ThermostatTargetChangeEvent` | A target changes, from the control menu (`MENU`) or `/hvac thermostat set` (`COMMAND`) |
| `EquipmentStateChangeEvent` | A heat pump, air conditioner, furnace, boiler or pool heat pump starts or stops |
| `SeasonChangeEvent` | A world's season turns, as the days pass or when an admin sets it |

EZvac only builds an event while some plugin listens to it, so it costs
nothing on servers without addons.

## HUD sections

Since API 1.1 an addon can add a few lines of its own to the climate HUD
(`/hvac hud`), drawn for every player who has it switched on:

```java
ezvac.hud().register(this, "thirst", context -> {
    int thirst = thirstOf(context.player());           // your own state
    if (thirst < 0) return List.of();                  // an empty list hides it for this player
    return List.of(
            Component.text("Thirst: ", NamedTextColor.GRAY)
                    .append(Component.text(thirst + "%", NamedTextColor.WHITE)),
            context.bar(thirst / 100.0, TextColor.color(0x5AB0FF)));
});
```

- **Its id** is your plugin's name and the key, in lowercase: `myaddon:thirst`.
  Registering the same key again replaces it, and it goes away by itself
  when your plugin is disabled.
- **When it's called:** on the main thread, every HUD update (twice a second
  by default), once per player with the HUD on. Keep it cheap: draw from state
  you already have. `context.reading()` is the climate the HUD shows them.
- **Matching EZvac's look:** `context.bar(fraction, color)` is the server's own
  bar (its glyphs, length and empty colour), and `context.temperature(f)`
  writes a temperature the way the HUD does, in the server's units.
- **Where it shows:** on the sidebar, where the server's `hud.sidebar.lines`
  has `{addons}` (every section, in the order they were registered), or a
  line that's just `{section:myaddon:thirst}`. Stock sidebars have
  `{addons}` under the season line. The action bar and boss bar have one line
  only, so a section shows there only where the server puts
  `{section:<id>}` into the format. Override `inline(context)` to choose the
  one-line form; by default it's your lines joined with spaces.
- **Mistakes stay yours:** a section that throws is logged once and left out;
  the rest of the HUD carries on.
- **Servers stay in control:** `hud.sections.hidden` switches sections off by
  id, and `/hvac hud sections` lists them.
- **Older EZvacs:** `hud()` is new in 1.1. If your addon should also run on
  1.0, check `apiVersion()` before calling it.

## Threads

Call the API's methods on the main thread, except `latestFor`, which is safe
from any thread (scoreboard and chat plugins often ask from other threads).
Everything the API hands out is an immutable snapshot: keep it as long as you
like, but it won't change; ask again for fresh numbers.

## An example addon

A complete plugin that warns players when it feels too hot:

```java
package com.example.heatwarning;

import com.github.blade.hvac.api.event.PlayerClimateUpdateEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HeatWarning extends JavaPlugin implements Listener {
    private static final double HOT_F = 95.0;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onClimate(PlayerClimateUpdateEvent event) {
        double now = event.getReading().feelsLikeF();
        double before = event.getPrevious().map(reading -> reading.feelsLikeF()).orElse(now);
        if (before < HOT_F && now >= HOT_F)
            event.getPlayer().sendMessage(Component.text("It's getting dangerously hot here.", NamedTextColor.GOLD));
    }
}
```

```yaml
# plugin.yml
name: HeatWarning
version: 1.0
main: com.example.heatwarning.HeatWarning
api-version: '1.21'
depend: [EZvac]
```

## Versions and promises

- Only the `com.github.blade.hvac.api` packages are the API. Everything else
  in EZvac is internal and may change in any release.
- New things raise the minor version (1.0 → 1.1). Anything that would break
  an addon only comes with a new major version, and is announced in the
  changelog first.
- The API's version is separate from EZvac's: check `apiVersion()`, or the
  `@since` tags in the Javadoc.
