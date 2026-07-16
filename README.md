# EZvac

EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
1.21.1. It provides sign thermostats, variable-speed heat pumps and air
conditioners, furnaces, ducted airflow, point thermometers, RPM monitors,
operating-profile panels, outdoor climate simulation, and persistent room
temperatures.

HVAC systems are identified by their world and group name.

> **Release status:** EZvac ALPHA 1.0 is an early testing release. Back up your
> server and EZvac data before upgrading or testing it on a production world.

## Requirements

- Paper 1.21.1
- Java 21
- Maven 3.9 or newer when building from source

## Build

```shell
mvn clean package
```

The production plugin will be written to:

```text
target/EZvac-ALPHA-1.0.jar
```

Automated tests are included under `src/test/java`. GitHub Actions runs
`mvn verify` for every push and pull request.

## Installation

1. Build the project or obtain the release JAR.
2. Put `EZvac-ALPHA-1.0.jar` in the Paper server's `plugins` directory.
3. Start Paper with Java 21.
4. Use `/hvac help`, `/hvac list`, and `/hvac stats` to confirm operation.

## Core devices

- **Thermostat:** One sign controller per world-scoped HVAC group.
- **Heat pump:** Dispenser-backed variable-speed cooling and heating.
- **Air conditioner:** Dispenser-backed variable-speed cooling.
- **Furnace:** Dispenser-backed fixed-capacity heating.
- **Vent:** Explicitly linked iron trapdoor that seeds duct airflow.
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

## Permissions

- `ezvac.use` — help, readings, inspection, and settings-panel operation.
- `ezvac.admin` — device creation, changes, removal, linking, and diagnostics.

## Data and safety

Runtime state is stored in `plugins/EZvac/hvac.yml`. Saves use a temporary file,
backup, and atomic replacement when supported. Missing loaded devices are
pruned, unloaded-world records are retained, and pending owned-fluid cleanup is
persisted until its chunk becomes available.

## License

EZvac is released under [The Unlicense](LICENSE). It is free and unencumbered
software dedicated to the public domain, with a permissive fallback for
jurisdictions where a complete public-domain dedication is not recognized.
