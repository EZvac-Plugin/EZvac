# The EZvac API

From ALPHA 2.1, other plugins can use what EZvac knows about the climate:
the temperature and "feels like" anywhere and around every player, the
outdoors, thermostats and their systems, dehumidifiers, humidifiers and air
purifiers, the air quality, the seasons, and events when things change. Addons can also add their own
sections to EZvac's climate HUD. An addon is an ordinary Paper plugin that
depends on EZvac.

API version **1.5** reads only: nothing an addon does through it changes
EZvac's climate or systems. Ways to change things (targets, power) will come
in a later API version.

The official addons are complete open-source examples:
[LiveExposure](https://github.com/EZvac-Plugin/LiveExposure) (body
temperature for survival players, with a section on the HUD) and
[EZdebug+](https://github.com/EZvac-Plugin/EZdebugPlus) (every device near a
player, drawn from `devices(world)` and the device events).

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
        <version>ALPHA-3.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**Gradle (Kotlin)**

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.EZvac-Plugin.EZvac:ezvac-api:ALPHA-3.1") }
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
| `thermostats()`, `thermostats(world)` | Thermostats: room and target temperature, mode, on/off, pool or room, humidity, running and total equipment |
| `thermostatAt(position)`, `thermostatOf(world, system)` | The thermostat whose sign is at a position, or the thermostat of a system, by the system key a view or reading gives you (1.3) |
| `thermostat(world, id)` | Deprecated in 1.3: labels can repeat, so it gives the lowest-placed thermostat with that label |
| `linkTool()` | Whether an item is EZvac's link tool, and what it has selected: a thermostat and its system, or a sensor hub (1.3) |
| `climateUnits()`, `climateUnits(world)` | Dehumidifiers, humidifiers and, since 1.5, air purifiers: humidity, setpoint, state, power, a dehumidifier's tank, a purifier's filter and the air it cleans (1.5) |
| `equipment()`, `equipment(world)` | Heat pumps, air conditioners, furnaces, boilers and pool heat pumps: kind, system, on/off, running, mode, motor RPM (1.2), and tier (1.4): 1 single-stage, 2 two-stage, 3 variable |
| `vents()`, `vents(world)` | Vents: system, and whether they blow air or water (1.2) |
| `instruments()`, `instruments(world)` | Thermometers and hygrometers, sensors, sensor hubs (with the sensors they show), RPM monitors and settings panels (1.2); air monitors, air sensors and wind sensors (1.5) |
| `devices(world)` | Every device in a world, of every kind (1.2) |
| `season(world)` | The season and its day, or empty where there are no seasons |
| `windForecast(world, days)` | Today's wind and the next days', each a `Wind.Day` with its level and mean speed; empty where there is no wind (1.5) |
| `units()` | Whether the server shows players °F or °C |

A `ClimateReading` holds the temperature, feels-like, humidity (NaN in water),
the outdoor temperature, whether the spot is in water, the weather, the
season, which system conditions the spot and how strongly, and since 1.5 its
`airQuality()` and the `wind()` felt there. A `ThermostatView` has its room's
`airQuality()` too, and an `OutdoorClimate` the `wind()` out in the open.

A `Wind` (1.5) holds the speed in m/s (`kmh()` and `mph()` convert it) and its
`Level`: `CALM`, `BREEZY`, `WINDY` or `GALE`. It is calm indoors, in water,
where a system conditions the spot, outside the overworld and on servers with
`wind.enabled: false`.

An `AirQuality` (1.5) holds PM2.5 and PM10 in µg/m³, pollen in grains/m³, the
AQI (0-500) with its `Band`, the `dominant()` pollutant (PM2.5 reads as smoke,
PM10 as dust) and the `PollenLevel`. It is empty in water, before a room is
first simulated, and on servers with `air-quality.enabled: false`. EZvac only
measures the air; what it does to players is up to addons.

> **Switch with a default branch.** 1.5 adds `ClimateUnitView.Kind.AIR_PURIFIER`,
> two `ClimateUnitView.State`s and `InstrumentView.Kind.AIR_MONITOR`,
> `AIR_SENSOR` and `WIND_SENSOR`. A `switch` over an EZvac enum with no `default` throws
> `MatchException` at runtime when it meets one an addon was built without,
> so give such switches a `default` branch.

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
| `DeviceAddedEvent` | A device is created: with `/hvac create`, or a vent with the link tool (1.2) |
| `DeviceRemovedEvent` | A device is gone: broken (`BROKEN`), removed with `/hvac remove` or unlinked with the link tool (`COMMAND`), or found missing (`MISSING`) (1.2) |

EZvac only builds an event while some plugin listens to it, so it costs
nothing on servers without addons.

## Systems

Since EZvac 3.0 (API 1.3) a system is a thermostat and whatever is linked to it
with the link tool; it has no name of its own. The `system` a view or reading
gives you is an **opaque key**:

- a system upgraded from EZvac 2.x keeps its old group name as its key, and a
  new one is `#` and a number;
- a key is stable while its system exists. When a headless system (one whose
  thermostat is gone) is linked to a thermostat, its devices take that
  thermostat's key;
- **don't show keys to players.** Show the thermostat's label instead:
  `ThermostatView.id()`, `ClimateReading.thermostat()`, or
  `thermostatOf(world, key)` for any view's system. Labels may repeat, so two
  houses can both be Home.

```java
String name = ezvac.thermostatOf(world, vent.system())
        .map(ThermostatView::id).orElse("Headless");
```

## Devices

Since API 1.2 every device is in the API with its position, filterable by
world: thermostats, equipment, vents, dehumidifiers and humidifiers, and the
instruments (thermometers, sensors, hubs, RPM monitors, settings panels).
Each view is an `HvacDevice`, a sealed type, so one `switch` covers them all:

```java
for (HvacDevice device : ezvac.devices(world)) {
    String label = switch (device) {
        case ThermostatView thermostat -> "Thermostat " + thermostat.id();
        case EquipmentView equipment -> equipment.kind() + (equipment.running() ? " (running)" : "");
        case VentView vent -> vent.medium() + " vent of " + vent.system();
        case ClimateUnitView unit -> unit.kind() + " " + unit.label();
        case InstrumentView instrument -> instrument.kind() + " " + instrument.label();
    };
    addMarker(device.position(), label);   // your map's own call
}
```

- **Staying in sync:** a map addon reads `devices(world)` once, then follows
  `DeviceAddedEvent` and `DeviceRemovedEvent`. The removed event carries the
  device as it was, so you know which marker to take down. Devices hvac.yml
  brings back at startup fire no event; read the lists when your plugin
  enables.
- **What changes without an event:** a running state, a mode, a hub's
  links, or which system a device is linked to (3.0). `EquipmentStateChangeEvent` covers equipment starting and
  stopping; for anything else, read the lists again.
- **Readings aren't in the views.** A sensor's view says where it is and what
  it reads, not its value. To show a value, read the spot with
  `readingAt(device.position().toLocation())`.
- **Mind what you publish.** These are every player's builds. A public web map
  of everyone's systems is the server's choice to make, so let admins switch
  it off.

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
- **Which EZvac has which API:** 1.0 came with ALPHA 2.1 Pilot 4, 1.1 with
  ALPHA 2.1 (HUD sections), 1.2 with ALPHA 2.2 (every device, and the device
  events), and 1.3 with ALPHA 3.0 (linked systems: `thermostatAt`,
  `thermostatOf`, the link tool), 1.4 with ALPHA 3.1 (`EquipmentView.tier()`),
  and 1.5 with ALPHA 3.2 (`AirQuality`, air purifiers, air monitors and air
  sensors, `Wind` and wind sensors).
  Depend on the oldest EZvac whose API has what you use.
