# EZvac


<img width="1080" height="1080" alt="ezvac" src="https://github.com/user-attachments/assets/833ee011-10f5-4a2c-8a26-4ebac1387a31" />


[![Build](https://github.com/EZvac-Plugin/EZvac/actions/workflows/build.yml/badge.svg)](https://github.com/EZvac-Plugin/EZvac/actions/workflows/build.yml)

EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
1.21.1 and its forks, including Purpur. It provides sign thermostats,
variable-speed heat pumps and air conditioners, furnaces, ducted airflow, point
thermometers, RPM monitors, operating-profile panels, outdoor climate
simulation, and persistent room temperatures.

HVAC systems are identified by their world and group name.

> **Release status:** EZvac ALPHA 1.6 Pilot 1 adds **water systems** — boilers
> and waxed copper grate vents that condition a body of water exactly the way
> vents condition a room. It is a *pilot*: published to be tried and reported
> on, with a design that may still change before ALPHA 1.6 proper. Do not run
> it on a world you care about.
>
> **You cannot roll back to ALPHA 1.5 once you build a water system** — 1.5
> predates the forward-compatibility work and would reinterpret boilers as heat
> pumps and delete grate vents. See [CHANGELOG.md](CHANGELOG.md).
>
> The stable release remains
> [ALPHA 1.5](https://github.com/EZvac-Plugin/EZvac/releases/tag/ALPHA-1.5), a
> performance overhaul that cut average tick cost by roughly 67% at high device
> counts.

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
target/EzVac-ALPHA-1.6-P1.jar
```

Automated tests are included under `src/test/java` and run as part of
`mvn verify`. GitHub Actions runs the same build and test suite on every push
and pull request.

## Installation

1. Build the project or obtain the release JAR.
2. Put `EzVac-ALPHA-1.6-P1.jar` in the server's `plugins` directory.
3. Start Paper (or Purpur) with Java 21.
4. Use `/hvac help`, `/hvac list`, and `/hvac stats` to confirm operation.

## Core devices

- **Thermostat:** One sign controller per world-scoped HVAC group.
- **Heat pump:** Dispenser-backed variable-speed cooling and heating.
- **Air conditioner:** Dispenser-backed variable-speed cooling.
- **Furnace:** Dispenser-backed fixed-capacity heating.
- **Boiler:** Dispenser-backed fixed-capacity heating for water systems.
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

## Documentation

- [Full feature reference](docs/features.txt)
- [Command reference](docs/commands.txt)
- [Changelog](CHANGELOG.md)

## Permissions

- `ezvac.use` — help, readings, inspection, and settings-panel operation.
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
