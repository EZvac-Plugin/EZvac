# The EZvac API

From 2.1 Pilot 4, other plugins can use what EZvac knows about the climate:
the temperature and "feels like" anywhere and around every player, the
outdoors, thermostats and their systems, dehumidifiers and humidifiers, the
seasons, and events when things change. An addon is an ordinary Paper plugin
that depends on EZvac.

API version **1.0** reads only: nothing an addon does through it changes
EZvac. Ways to change things (targets, power) will come in a later API version.

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
        <version>ALPHA-2.1-P4</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (Kotlin)**

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.EZvac-Plugin.EZvac:ezvac-api:ALPHA-2.1-P4") }
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

All fired on the main thread. They are informational in 1.0: none can be
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
