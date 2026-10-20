# EZvac


<img width="1080" height="1080" alt="ezvac-logo" src="https://github.com/user-attachments/assets/41b316f7-3826-4913-98da-626209b56cb3" />


EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
1.21.1 and its forks, including Purpur. It provides sign thermostats,
variable-speed heat pumps and air conditioners, furnaces, ducted airflow, point
thermometers, RPM monitors, operating-profile panels, outdoor climate
simulation, and persistent room temperatures. Systems condition either **air**
or **water**, so the same simulation heats a room or a swimming pool.

HVAC systems are identified by their world and group name.

> **Release status:** EZvac ALPHA 1.9 adds **pool heat pumps** (heat *and*
> cool a pool, alone or alongside a boiler) and makes water read like water:
> the climate HUD switches to a water layout and pool comfort scale, lakes and
> the sea read the biome's natural water temperature, and idle pools drift
> toward it. No save-file changes; a drop-in upgrade from any earlier release.
> See [Climate HUD and placeholders](#climate-hud-and-placeholders).

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
target/EzVac-ALPHA-1.9.jar
```

Automated tests are included under `src/test/java` and run as part of
`mvn verify`. GitHub Actions runs the same build and test suite on every push
and pull request.

## Installation

1. Build the project or obtain the release JAR.
2. Put `EzVac-ALPHA-1.9.jar` in the server's `plugins` directory.
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
- **RPM monitor:** Type-specific sign showing RPM, capacity, and unit counts.
- **Settings panel:** Sign control for Eco, Normal, and Turbo operation.

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

## Climate HUD and placeholders

Players can keep the climate where they stand on screen:

```text
/hvac hud [on|off]
```

It shows the local temperature, a cold-to-hot bar with a comfort label, the
outdoor temperature, and the system conditioning that spot, its mode and
target, and whether you are in its core or its leakage edge. It is off by
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

- `ezvac.use` — help, readings, inspection, and settings-panel operation.
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
