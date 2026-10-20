# EZvac


<img width="1080" height="1080" alt="ezvac-logo" src="https://github.com/user-attachments/assets/41b316f7-3826-4913-98da-626209b56cb3" />


EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
1.21.1 and its forks, including Purpur. It provides sign thermostats,
variable-speed heat pumps and air conditioners, furnaces, ducted airflow, point
thermometers, RPM monitors, operating-profile panels, outdoor climate
simulation with weather, humidity and "feels like" temperatures, standalone
dehumidifiers, and persistent room temperatures. Systems condition either
**air** or **water**, so the same simulation heats a room or a swimming pool.

HVAC systems are identified by their world and group name.

> **Release status:** EZvac ALPHA 2.0 Pilot 1 is a prerelease of the 2.0
> realism update: **weather** changes the outdoor air, rooms have **humidity**,
> the HUD shows how warm it **feels**, and **dehumidifiers** dry a room or a
> shed. It moves `hvac.yml` to format 6 and keeps your 1.x save as
> `hvac.yml.format5.bak`. See [Weather and humidity](#weather-and-humidity)
> and [Dehumidifiers](#dehumidifiers).

## Requirements

- Paper 1.21.1 or a Paper fork such as Purpur
- Java 21
- Maven 3.9 or newer when building from source

## Build

```shell
mvn clean package
```

The production plugin will be written to:

```text
target/EzVac-ALPHA-2.0-P1.jar
```

Automated tests are included under `src/test/java` and run as part of
`mvn verify`. GitHub Actions runs the same build and test suite on every push
and pull request.

## Installation

1. Build the project or obtain the release JAR.
2. Put `EzVac-ALPHA-2.0-P1.jar` in the server's `plugins` directory.
3. Start Paper (or Purpur) with Java 21.
4. Use `/hvac help`, `/hvac list`, and `/hvac stats` to confirm operation.

### Upgrading

Upgrading from any earlier EZvac release is supported: replace the jar and
restart. Your `plugins/EZvac/hvac.yml` is read as-is, and every release's save
file is covered by the test suite.

**Downgrading is not supported.** From ALPHA 1.7 on, EZvac refuses to start on
a save file written by a newer version, and leaves the file untouched, instead
of loading it and silently discarding what it doesn't understand. When a
release changes the save format, it first keeps the original as
`hvac.yml.format<N>.bak`; restore that file if you ever need to go back.

## Core devices

- **Thermostat:** One sign controller per world-scoped HVAC group, 60-85 F.
- **Pool thermostat:** The same sign for water systems, 75-104 F. Only controls
  water-loop equipment (boilers and pool heat pumps), so it cannot be used to
  overheat a room.
- **Heat pump:** Dispenser-backed variable-speed cooling and heating.
- **Air conditioner:** Dispenser-backed variable-speed cooling.
- **Furnace:** Dispenser-backed fixed-capacity heating.
- **Boiler:** Dispenser-backed heating for water systems.
- **Pool heat pump:** Dispenser-backed variable-speed heating *and* cooling for
  water systems. It can share a pool system with a boiler.
- **Vent:** Explicitly linked iron trapdoor that seeds duct airflow.
- **Water vent:** Explicitly linked waxed copper grate that conditions a body
  of water. A system is all air or all water; water bodies behave exactly like
  rooms.
- **Thermometer:** Independent sign sensor that measures its own block.
- **Hygrometer / thermo-hygrometer:** The same sensor for humidity, or for
  temperature, humidity and "feels like" together.
- **RPM monitor:** Type-specific sign showing RPM, capacity, and unit counts.
- **Settings panel:** Sign control for Eco, Normal, and Turbo operation.
- **Dehumidifier:** Standalone unit (iron block on a waxed copper grate, with a
  sign) that dries the air around it. See [Dehumidifiers](#dehumidifiers).

Registered equipment does not consume buckets, fuel, water, lava, power, or
inventory items. EZvac records and removes only fluid sources that it created.

## Operating profiles

Create a panel while looking at a sign:

```text
/hvac create settingspanel <group> [label]
```

Right-click to move forward through the profiles and sneak-right-click to move
backward. Without a panel, a group always uses Normal.

| Profile | Default maximum RPM | Variable-unit maximum capacity |
| --- | ---: | ---: |
| Eco | 2,400 | 70% |
| Normal | 3,600 | 100% |
| Turbo | 4,200 | 115% |

Profiles affect heat pumps and air conditioners only. All variable equipment
in a group receives the same speed command. Furnaces remain fixed at full
capacity and are never averaged down by a throttled heat pump.

## Thermostat display

```text
EZvac | main
74.2 F | COOL
Set 72.0 F | 68%
ETA 0:31 | house
```

The ETA uses the same RPM ramping, operating profile, outdoor load, mixed-unit
capacity, and thermal simulation as the live controller.

## Thermometers

Create an independent point thermometer while looking at a sign:

```text
/hvac create thermometer [label]
```

It reads the temperature at its own block: full conditioned temperature in a
vent core, blended temperature near the airflow edge, or outdoor temperature
when outside every HVAC system.

Hygrometers and thermo-hygrometers are placed the same way and read the same
spot:

```text
/hvac create hygrometer [label]
/hvac create thermohygrometer [label]
```

```text
Hygrometer          cellar
Inside: 58%         In 72.1 F 58%
Outside: 41%        Feels 73.0 F
                    Out 64.0 F 41%
```

## Weather and humidity

**Weather** changes the outdoor air. Rain and thunder cool the day (by 6 °F
and 9 °F by default) and narrow the day/night swing; snow cools a little.
Vanilla weather switches in an instant, so the effect eases in and out over
about two in-game hours. Deserts, savannas, badlands, the Nether and the End
never get weather. Everything that uses the outdoor temperature follows it:
the HUD, idle drift, ETAs, thermometers and pools (natural water reacts only
slightly).

**Humidity** is simulated as moisture (a dew point) and shown as relative
humidity, which is how real thermostats work, and it makes the physics come
out right on its own:

- Outdoors it comes from the biome's vanilla downfall (deserts are dry,
  jungles and swamps humid), rises at night as the air cools, and climbs
  toward 100% in rain.
- Indoors, each air system's room drifts toward the outdoor moisture. Cooling
  dries the air, heating lowers the RH (dry winter air), and water inside a
  room evaporates into it, faster when it is warm: a heated pool hall gets
  muggy.

**Feels like** is the apparent temperature: humid heat feels hotter, dry air
and outdoor wind feel cooler. The HUD's comfort word and bar follow it.

Tuning lives under `weather:` and `humidity:` in `config.yml`.

## Dehumidifiers

Build an **iron block on top of a waxed copper grate**, put a sign on the iron
block (on its side, or standing on top), and while looking at the sign run:

```text
/hvac create dehumidifier [label]
```

It needs no system. It dries the air within its own short reach (12 blocks
of airflow by default): the room of the system whose air surrounds it, or,
anywhere else (a shed, a basement), that space itself, which the HUD then
reads.

```text
EZvac | basement
58% > 45%
Running
Tank 40%
```

- **Click** the sign to step the setpoint: 35, 40, 45, 50, 55, 60%.
- **Sneak-click** to switch it on or off. Off keeps the tank as it is.
- It starts 2% above the setpoint and runs down to it.
- The water it removes fills its **tank**. When the tank is full the grate
  fills with water and the sign shows `Emptying...` for 30 seconds, then it
  carries on. The water never leaves the grate.

Breaking the sign, the iron block or the grate removes it.

## Climate HUD and placeholders

Players can keep the climate where they stand on screen:

```text
/hvac hud [on|off]
```

It shows the local temperature, how warm it feels, a cold-to-hot bar with a
comfort label, the humidity, the outdoor temperature and weather, and the
system conditioning that spot, its mode and target, and whether you are in its
core or its leakage edge. It is off by
default for each player, and EZvac remembers each player's choice.

By default the HUD is a scoreboard **sidebar**; `hud.style` in `config.yml`
switches it to the **action bar** or a **boss bar**. EZvac never takes over a
sidebar the server already uses: if a scoreboard plugin such as TAB is
installed, the HUD uses the action bar instead.

With [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) installed
(optional), the same values are available to scoreboard, TAB, and chat
plugins as placeholders:

| Placeholder | Example |
| --- | --- |
| `%ezvac_temp%` (`_temp_f`, `_temp_c`) | `72.4°F` |
| `%ezvac_feels_like%` (`_feels_like_f`, `_feels_like_c`) | `74.1°F` |
| `%ezvac_humidity%` | `48%` |
| `%ezvac_weather%` | `Rain` (`Clear`, `Thunder`, `Snow`; empty in dry biomes) |
| `%ezvac_outdoor%` (`_outdoor_f`, `_outdoor_c`) | `88.0°F` |
| `%ezvac_bar%` / `%ezvac_bar_plain%` | `▮▮▮▮▮▯▯▯▯▯` (coloured / plain) |
| `%ezvac_comfort%` | `Comfortable` |
| `%ezvac_group%`, `%ezvac_thermostat%` | `house`, `main` |
| `%ezvac_mode%`, `%ezvac_target%` | `Cooling to 70.0°F`, `70.0°F` |
| `%ezvac_zone%`, `%ezvac_influence%`, `%ezvac_conditioned%` | `Core`, `100`, `true` |
| `%ezvac_medium%` | `air` or `water` |

In water the HUD switches to water readings: `Water`, `Pool` and `Circulation`
lines, a pool comfort scale (84 °F is a pleasant swim; above 95 °F is a hot
tub), and `%ezvac_medium%` reports `water`. Water no pool system reaches,
such as lakes and the sea, reads the biome's natural water temperature: steady,
with no day/night swing.

Text, colours, bar length and range, comfort bands, and units (°F or °C) are
all configurable under `hud:` (and `hud.water:`) in `config.yml`.

## Documentation

- [Full feature reference](docs/features.txt)
- [Command reference](docs/commands.txt)
- [Changelog](CHANGELOG.md)

## Permissions

- `ezvac.use` — help, readings, inspection, and settings-panel and
  dehumidifier operation.
- `ezvac.hud` — show the on-screen climate HUD (default: everyone).
- `ezvac.admin` — device creation, changes, removal, linking, and diagnostics.

## Data and safety

Runtime state is stored in `plugins/EZvac/hvac.yml`. Saves use a temporary file,
backup, and atomic replacement when supported. The snapshot is taken on the main
thread and the YAML dump and file replacement run off it, so a large registry no
longer stalls the server tick; shutdown saves stay synchronous. Missing loaded
devices are pruned, unloaded-world records are retained, and pending owned-fluid
cleanup is persisted until its chunk becomes available.

## License

EZvac is released under the [MIT License](LICENSE), starting with ALPHA 1.5.
Copyright (c) 2026 bladestech and EZvac contributors.

Releases through ALPHA 1.1 HOTFIX were dedicated to the public domain under
[The Unlicense](https://unlicense.org/). That dedication is irrevocable: those
versions remain public domain and may still be used on those terms. The MIT
License applies to ALPHA 1.5 and everything after it.

Contributions are accepted under the same MIT terms — see
[CONTRIBUTING.md](CONTRIBUTING.md).
