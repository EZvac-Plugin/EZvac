# Configuration

Everything is in `plugins/EZvac/config.yml`. Each setting has a comment there
too; this page explains them in plain words. Temperatures in `config.yml` are
always in °F, whatever `units` says (their keys end in `-f`), and durations are
in server ticks (20 ticks is normally one second; an in-game hour is
1,000 ticks).

## Your config.yml stays up to date

- **New settings are added for you.** When an EZvac update adds settings, they
  are written into your `config.yml` on startup, with their comments. Nothing
  you already have is changed, even where a default has since changed, and the
  previous file is kept as `config.yml.before-<version>.bak`. A `config.yml`
  that can't be read is left alone.
- **A bad value in a 2.0 section doesn't stop EZvac.** In `weather`,
  `humidity`, `dehumidifier`, `humidifier` or `variance`, that section uses its
  defaults and the log names the setting to fix.
- **A bad value in the other sections** (controller, temperatures, speeds,
  airflow, audio) stops EZvac safely rather than running with settings that
  contradict each other. The log says which one.

Restart the server after editing `config.yml`.

## units

```yaml
units: F
```

`F` or `C`: the unit everyone sees and types temperatures in, on signs, in chat,
on the HUD and in placeholders. Typing `72F` or `22C` always works whatever
this says, so nobody sets 72 °C by habit. `%ezvac_temp_f%` and `%ezvac_temp_c%`
stay in their own unit.

Before 2.1 only the HUD could show Celsius, through `hud.units`. A config that
had `hud.units: C` starts with `units: C` when it's updated, and `hud.units` is
no longer read.

## controller

When the thermostat acts.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `period-ticks` | 20 | How often systems are updated |
| `save-period-ticks` | 600 | How often `hvac.yml` is saved |
| `display-period-ticks` | 60 | How often every nearby sign is fully redrawn |
| `cooling-start-above-f` | 1.0 | Cooling starts this far above the target |
| `heating-start-below-f` | 1.0 | Heating starts this far below the target |
| `cooling-stop-below-f` | 0.3 | Cooling stops this far below the target |
| `heating-stop-above-f` | 0.3 | Heating stops this far above the target |
| `minimum-run-ticks` | 300 | Equipment runs at least this long once started |
| `minimum-off-ticks` | 200 | Equipment rests at least this long once stopped |

## temperature

| Setting | Default | Meaning |
| --- | ---: | --- |
| `minimum-target-f`, `maximum-target-f` | 60, 85 | The range a room thermostat accepts |
| `pool-minimum-target-f`, `pool-maximum-target-f` | 75, 104 | The range a pool thermostat accepts |
| `cooling-f-per-minecraft-hour` | 3.75 | How fast one unit at full speed cools |
| `heating-f-per-minecraft-hour` | 5.0 | How fast one unit at full speed heats |
| `idle-drift-f-per-minecraft-hour` | 1.25 | How fast a room with nothing running drifts toward the outdoors |
| `active-leak-f-per-minecraft-hour` | 0.50 | How much the outdoors works against a running system |
| `maximum-group-capacity` | 2.0 | Most a group's units can add up to, in single units |

## performance.variable-speed

Heat pumps, air conditioners and pool heat pumps slow down near the target.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `approach-band-f` | 3.0 | Within this of the target they start slowing |
| `minimum-capacity` | 0.40 | The slowest they run, as a share of full |
| `minimum-rpm`, `maximum-rpm` | 1200, 3600 | Speed range in the Normal profile |
| `eco-maximum-rpm`, `turbo-maximum-rpm` | 2400, 4200 | Top speed in the Eco and Turbo profiles |
| `ramp-up-rpm-per-second`, `ramp-down-rpm-per-second` | 900, 1200 | How fast they speed up and slow down |

## airflow

| Setting | Default | Meaning |
| --- | ---: | --- |
| `core-range` | 15 | Blocks of airflow at full strength |
| `maximum-range` | 30 | Blocks of airflow before a system has no effect |
| `nodes-per-tick` | 4000 | Work budget for working out airflow, per tick |
| `maximum-cells-per-system` | 200000 | Most blocks one system's airflow covers |

## audio

| Setting | Default | Meaning |
| --- | ---: | --- |
| `enabled` | true | Turns all EZvac sounds on or off |
| `radius-blocks` | 25.0 | Only players this close hear a sound |
| `hum-refresh-ticks` | 60 | How often running equipment hums |
| `vent-airflow-refresh-ticks` | 80 | How often running systems' vents play their airflow sound |

### Sounds

Every sound is a line under `audio.sounds`: `"sound volume pitch"`.

```yaml
audio:
  sounds:
    equipment-hum: ["block.beacon.ambient 1.6 0.55"]
    vent-airflow: ["block.conduit.ambient 0.3 0.5"]
```

| Event | Plays when |
| --- | --- |
| `equipment-hum` | heat pumps, air conditioners and boilers are running (every `hum-refresh-ticks`) |
| `equipment-start`, `equipment-stop` | those start and stop |
| `furnace-start`, `furnace-stop` | a furnace starts and stops |
| `unit-start`, `unit-stop` | a dehumidifier or humidifier switches on and off |
| `dehumidifier-start`, `dehumidifier-hum` | a dehumidifier starts, and while it runs |
| `dehumidifier-emptying` | a full tank starts to empty |
| `humidifier-bubbles` | now and then while a humidifier runs |
| `vent-airflow` | air vents of running systems near a player, one per cluster of vents |

- Lines in one list play together, and `[]` silences that event.
- The key can be any sound, a resource pack's own included. One the server
  doesn't know is still played (a resource pack may add it), and the log
  mentions it once.
- Pick **mono** sounds: stereo ones, such as `item.elytra.flying`, sound the
  same at any distance.
- The game only plays pitches from 0.5 to 2.0; others are brought into range
  with a warning.
- Sounds play at their block and fade with distance like any block sound.
- `audio.hum-volume` and `audio.hum-pitch` from older configs are no longer
  read. A hum you tuned with them carried over into `equipment-hum` and
  `dehumidifier-hum` when your config was updated.

## displays

| Setting | Default | Meaning |
| --- | ---: | --- |
| `screens` | true | A floating screen in front of every EZvac sign; false shows the plain signs only |

See [Screens and control menus](screens-and-menus.md).

## biome-blend

| Setting | Default | Meaning |
| --- | ---: | --- |
| `radius-blocks` | 24 | How far around a spot biomes are averaged, 0-64; 0 turns transitions off |

See [Climate](climate.md#biome-transitions).

## seasons

| Setting | Default | Meaning |
| --- | ---: | --- |
| `enabled` | true | false turns the seasons off everywhere |
| `days-per-season` | 15 | Minecraft days in each season; four make a year |
| `strength` | 1.0 | How much of each biome's seasonal swing applies: 1 is realistic, 0.5 mild, 0 none (0-3) |
| `water-lag-days` | 5.0 | How many days lakes and the sea follow behind the air |

The calendar itself is kept in `seasons.yml`, not here; change it with
[`/hvac season`](commands.md#seasons). See [Climate](climate.md#seasons).

## altitude

| Setting | Default | Meaning |
| --- | ---: | --- |
| `lapse-f-per-block` | 0.1 | How much colder the air is for every block above `from-y` (0-1); 0 turns it off |
| `from-y` | 64 | The height it starts from |

## underground

| Setting | Default | Meaning |
| --- | ---: | --- |
| `enabled` | true | false keeps the outdoor swing, seasons and weather all the way down |
| `start-depth` | 4 | Blocks below the surface (or sea level, if lower) where the ground's steady temperature starts to take over |
| `full-depth` | 20 | Where it has taken over completely; must be deeper than `start-depth` |

## biomes

Every vanilla biome has a built-in climate (see the table in
[Climate](climate.md#outdoor-temperature)). To change one, or to give a data
pack's biome its own, add it by its id. Values are year-round averages at sea
level; anything left out keeps the built-in value:

```yaml
biomes:
  minecraft:plains: { seasonal-f: 10 }
  mypack:volcano: { noon-f: 120, midnight-f: 100, seasonal-f: 0, precipitation: none }
```

| Key | Meaning |
| --- | --- |
| `noon-f`, `midnight-f` | Average noon and midnight temperature. A biome EZvac doesn't know needs both |
| `seasonal-f` | How far mid-summer and mid-winter move from those averages |
| `precipitation` | `rain`, `snow` or `none` (a desert); EZvac's weather effects follow it |

A broken entry is skipped with a warning, and that biome keeps its built-in
climate.

## weather

| Setting | Default | Meaning |
| --- | ---: | --- |
| `enabled` | true | Weather changes the outdoor air at all |
| `ease-hours` | 2.0 | In-game hours for a change of weather to take full effect |
| `rain-cooling-f`, `thunder-cooling-f`, `snow-cooling-f` | 6, 9, 3 | How much each cools the daily average |
| `swing-reduction` | 0.6 | Share of the day/night swing full rain takes away |
| `water-share` | 0.1 | Share of the air's change natural water follows |
| `wind-clear-ms`, `wind-rain-ms`, `wind-thunder-ms` | 1.5, 3, 5 | Outdoor wind for "feels like", in m/s |

With `enabled: false` temperatures ignore the weather, but weather sensors
still report the sky. More in [Climate](climate.md#weather).

## humidity

| Setting | Default | Meaning |
| --- | ---: | --- |
| `outdoor-rh-dry`, `outdoor-rh-wet` | 30, 90 | Outdoor RH in the driest and wettest biomes |
| `rain-rh` | 97 | Outdoor RH that rain or snow pulls toward |
| `infiltration-per-hour` | 0.15 | How fast a room's moisture follows the outdoors |
| `coil-dew-point-f` | 45 | How dry cooling equipment can make the air |
| `cooling-dry-f-per-minecraft-hour` | 4.0 | How fast cooling dries the air |
| `evaporation-f-per-minecraft-hour` | 30.0 | How fast water in a room adds moisture |

## variance

| Setting | Default | Meaning |
| --- | ---: | --- |
| `enabled` | true | Readings vary from spot to spot |
| `indoor-f`, `outdoor-f` | 0.5, 1.5 | Most a reading strays in a room and outdoors |
| `pool-f`, `natural-water-f` | 0.3, 0.8 | The same in a pool and in natural water |
| `dew-point-f` | 0.3 | How much moisture varies |
| `spacing-blocks` | 6.0 | How far apart the variance changes |
| `drift-hours` | 3.0 | In-game hours over which a spot drifts to a new value |

## dehumidifier and humidifier

| Setting | Dehumidifier | Humidifier | Meaning |
| --- | ---: | ---: | --- |
| `range` | 30 | 30 | Reach of the unit's own space, in blocks of airflow |
| `maximum-cells` | 50000 | 50000 | Most blocks that space covers |
| `dry-f-per-minecraft-hour` / `add-f-per-minecraft-hour` | 3.0 | 3.0 | How fast it dries or adds moisture in a space of `reference-cells` |
| `reference-cells` | 1500 | 1500 | Space size it works at full strength in; bigger is slower |
| `dry-dew-point-f` | 35 | | How dry its coil can make the air ("Air too cold" below this) |
| `tank-fill-hours` | 8.0 | | In-game hours of full drying that fill the tank |
| `empty-seconds` | 30 | | How long a full tank takes to empty |
| `default-setpoint` | 45 | 40 | Setpoint of a new unit, % RH |
| `presets` | 35-60 | 30-50 | The setpoint range the control menu reaches, from the lowest to the highest, in steps of 5 |
| `hysteresis` | 2.0 | 2.0 | How far past the setpoint before it starts again, % RH |

## hud

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | true | The `/hvac hud` command works at all |
| `style` | sidebar | `sidebar`, `actionbar` or `bossbar` |
| `update-ticks` | 10 | How often each player's HUD is refreshed |
| `none-text` | none | Shown where no system conditions the air |
| `sidebar.title`, `sidebar.lines` | | The sidebar's title and lines |
| `sidebar.yield-to-plugins` | TAB, … | Scoreboard plugins the sidebar steps aside for |
| `actionbar.format`, `bossbar.format` | | The one-line formats |
| `sections.hidden` | [] | Addon HUD sections never to show, by id, such as `liveexposure:body` ([addon sections](hud-and-placeholders.md#addon-sections)) |
| `bar` | | `length`, `filled` and `empty` symbols, `empty-color`, and the `cold-f`/`hot-f` ends of the bar |
| `comfort` | | The `cold`, `cool`, `comfortable`, `warm` and `hot` bands, each with its `label`, `color` and `below-f`/`above-f` |
| `water` | | The same settings used while the player is in water |

Tokens and examples are in [HUD and placeholders](hud-and-placeholders.md#customising).
