# EZvac


<img width="1080" height="1080" alt="ezvac-logo" src="https://github.com/user-attachments/assets/41b316f7-3826-4913-98da-626209b56cb3" />


EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
1.21.1 and its forks, including Purpur. Build heat pumps, air conditioners and
furnaces out of dispensers, link vents to a room, and a sign thermostat keeps
it at the temperature you set, while the outdoors follows the time of day, the
biome and the weather.

- **Systems** for rooms (iron trapdoor vents) or pools (copper grate vents),
  with variable-speed equipment, operating profiles and an ETA on the
  thermostat sign.
- **Climate:** weather, humidity, a "feels like" temperature, and readings that
  vary a little from spot to spot.
- **Dehumidifiers and humidifiers** that dry or moisten a room or a shed.
- **Sensors:** thermometers, hygrometers, and button sensors that report to a
  sensor hub sign.
- **An on-screen HUD** and PlaceholderAPI placeholders.
- **Configurable sounds**, and no fuel, power or items needed.

> **Release status:** EZvac ALPHA 2.0 is the realism update: weather,
> humidity and feels-like, dehumidifiers and humidifiers, sensors and sensor
> hubs, and spot-to-spot variance. Upgrading from 1.x moves `hvac.yml` to
> format 6 and keeps your 1.x save as `hvac.yml.format5.bak`. See the
> [changelog](CHANGELOG.md).

## Quick install

1. Download `EzVac-ALPHA-2.0.jar` from the
   [releases](https://github.com/EZvac-Plugin/EZvac/releases) or
   [Modrinth](https://modrinth.com/plugin/ezvac-plugin).
2. Put it in your server's `plugins` folder. You need Paper 1.21.1 or a fork
   such as Purpur, on Java 21.
3. Start the server, then follow [Getting started](docs/getting-started.md) to
   build your first system.

## Documentation

| Page | What's in it |
| --- | --- |
| [Getting started](docs/getting-started.md) | Install, then a first working system step by step |
| [Equipment and thermostats](docs/equipment.md) | Thermostats, every kind of equipment and its casing, profiles, RPM monitors |
| [Vents and airflow](docs/airflow.md) | Linking vents, how far air reaches, pools |
| [Dehumidifiers and humidifiers](docs/dehumidifiers-and-humidifiers.md) | Building and using them, "Air too cold" |
| [Sensors and readings](docs/sensors.md) | Thermometers, hygrometers, sensors and sensor hubs |
| [Climate](docs/climate.md) | Outdoor temperature, weather, humidity, feels-like, variance |
| [HUD and placeholders](docs/hud-and-placeholders.md) | `/hvac hud` and every `%ezvac_*%` placeholder |
| [Configuration](docs/configuration.md) | Every `config.yml` setting, sounds |
| [Commands and permissions](docs/commands.md) | Every command |
| [Upgrading and troubleshooting](docs/upgrading-and-troubleshooting.md) | Upgrading, backups, common problems |

The full technical behaviour is in [docs/features.txt](docs/features.txt), and
every release's changes in the [changelog](CHANGELOG.md).

## Building from source

You need Java 21 and Maven 3.9 or newer.

```shell
mvn clean package
```

The plugin is written to `target/EzVac-ALPHA-2.0.jar`. `mvn verify` also runs
the test suite, as GitHub Actions does on every push and pull request. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

EZvac is released under the [MIT License](LICENSE), starting with ALPHA 1.5.
Copyright (c) 2026 bladestech and EZvac contributors.

Releases through ALPHA 1.1 HOTFIX were dedicated to the public domain under
[The Unlicense](https://unlicense.org/). That dedication is irrevocable: those
versions remain public domain and may still be used on those terms. The MIT
License applies to ALPHA 1.5 and everything after it.

Contributions are accepted under the same MIT terms — see
[CONTRIBUTING.md](CONTRIBUTING.md).
