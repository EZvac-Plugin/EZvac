# EZvac


<img width="1080" height="540" alt="ezv_D" src="https://github.com/user-attachments/assets/95b2a2b3-4525-4d7d-b38b-214916050f73" />


EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
1.21.1 and its forks, including Purpur. Build heat pumps, air conditioners and
furnaces out of dispensers, link vents to a room, and a sign thermostat keeps
it at the temperature you set, while the outdoors follows the time of day, the
biome and the weather.

- **Systems** for rooms (iron trapdoor vents) or pools (copper grate vents),
  with variable-speed equipment, operating profiles and an ETA on the
  thermostat sign.
- **Climate:** a realistic climate for every biome, seasons, colder air up
  mountains and steady air deep underground, weather, daily wind, humidity,
  a "feels like" temperature, smooth transitions between biomes, and
  readings that vary a little from spot to spot.
- **Dehumidifiers and humidifiers** that dry or moisten a room or a shed.
- **Air quality:** smoke, dust and pollen in every room, scored as the real
  Air Quality Index, and air purifiers whose coral filters fill up.
- **Sensors:** thermometers, hygrometers, air monitors, and button and
  lightning-rod sensors that report to a sensor hub sign.
- **Lit screens on every sign** and click-to-open control menus for setpoints.
- **An on-screen HUD** and PlaceholderAPI placeholders.
- **Fahrenheit or Celsius**, server wide, and configurable sounds. No fuel,
  power or items needed.

> **Release status:** EZvac ALPHA 3.2 adds air quality. Every room tracks
> fine particles, dust and pollen, scored as the US EPA Air Quality Index:
> campfires and fire make smoke, deserts and the Nether bring dust and ash,
> and spring brings pollen. Air purifiers clean it, and their tube coral
> filters fill and die. Every day also has its own wind, from Calm to a
> Gale: it chills the outdoors, makes houses leakier, and raises dust storms
> in the desert. ALPHA 3.1 added equipment tiers, shown as stars:
> ★☆☆ single-stage, ★★☆ two-stage and ★★★ variable-speed.
> ALPHA 3.0, *Linked systems*, dropped group names. You link equipment, vents
> and signs to a thermostat with one link tool, thermostat names can repeat,
> and a system that loses its thermostat waits, headless, for a new one.
> Upgrading from 2.x converts `hvac.yml` to a new format and keeps the old
> file as a backup; there's no going back to 2.x without it. ALPHA 2.2 let
> addons see every device. ALPHA 2.1, *Realism: EXTENDED*,
> gave every biome its own climate, with smooth transitions between them,
> seasons on EZvac's own calendar, colder air up high and steady air
> underground, lit screens and control menus on every sign, and Fahrenheit
> or Celsius server wide. See the [changelog](CHANGELOG.md).

## Quick install

1. Download `EzVac-ALPHA-3.1.jar` from the
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
| [Air quality and air purifiers](docs/air-quality.md) | The AQI, pollen, smoke and dust, and purifiers with coral filters |
| [Sensors and readings](docs/sensors.md) | Thermometers, hygrometers, sensors and sensor hubs |
| [Screens and control menus](docs/screens-and-menus.md) | The screens on every sign, and the setpoint menus |
| [Climate](docs/climate.md) | Outdoor temperature, weather, humidity, feels-like, variance |
| [Wind](docs/wind.md) | Each day's wind, where it blows, and what it does |
| [HUD and placeholders](docs/hud-and-placeholders.md) | `/hvac hud` and every `%ezvac_*%` placeholder |
| [Configuration](docs/configuration.md) | Every `config.yml` setting, sounds |
| [Commands and permissions](docs/commands.md) | Every command |
| [Upgrading and troubleshooting](docs/upgrading-and-troubleshooting.md) | Upgrading, backups, common problems |
| [The EZvac API](docs/api.md) | Extending EZvac: building addons on its climate |

The full technical behaviour is in [docs/features.txt](docs/features.txt), and
every release's changes in the [changelog](CHANGELOG.md).

## Extending EZvac

Addons are ordinary Paper plugins that use EZvac's public API: climate
readings for any spot or player, thermostats, the seasons, and events. Add
`com.github.EZvac-Plugin.EZvac:ezvac-api` from [JitPack](https://jitpack.io/#EZvac-Plugin/EZvac)
and see [The EZvac API](docs/api.md).

## Building from source

You need Java 21 and Maven 3.9 or newer.

```shell
mvn clean package
```

The plugin is written to `ezvac-plugin/target/EzVac-ALPHA-3.1.jar`. `mvn verify` also runs
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
