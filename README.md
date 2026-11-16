# EZvac


<img width="1080" height="540" alt="ezv_D" src="https://github.com/user-attachments/assets/95b2a2b3-4525-4d7d-b38b-214916050f73" />


EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper and
its forks, such as Purpur. Build heat pumps, air conditioners and furnaces out
of dispensers, link vents to a room, and a thermostat keeps it at the
temperature you set, while the outdoors follows the biome, the season, the
time of day and the weather.

- **Systems** for rooms (iron trapdoor vents) or pools (copper grate vents),
  with single-stage, two-stage and variable-speed equipment, and profiles.
- **Climate:** a realistic climate for every biome, seasons, weather, wind,
  humidity and a "feels like" temperature, colder air up mountains and
  steady air underground.
- **Dehumidifiers, humidifiers and air purifiers,** with air quality scored
  as the real AQI: smoke, dust and pollen.
- **Refrigeration:** mini fridges, fridges, refrigerators and deep freezers,
  with frost.
- **Houses keep running while nobody is near:** devices work on in unloaded
  chunks.
- **Sensors and weather stations:** thermometers, hygrometers, sensor hubs,
  and towers that forecast the next seven days.
- **Screens with buttons** on every device, control menus, an on-screen HUD
  and PlaceholderAPI placeholders.
- **Addons** through a public API, and official ones such as
  [EZclimates](https://github.com/EZvac-Plugin/EZclimates).
- **Fahrenheit or Celsius,** server wide. No fuel, power or items needed.

> **Release status:** EZvac is in BETA. The latest pilot,
> [BETA 1.0 Pilot 8](https://github.com/EZvac-Plugin/EZvac/releases/tag/BETA-1.0-P8),
> keeps houses running while nobody is near. Pilots are test builds ahead of
> the full BETA 1.0. Every release is in the [changelog](CHANGELOG.md).

## Minecraft versions

EZvac runs on **Minecraft 26.1 to 26.3** (Java 25), where it is played and
tested first. It still runs on **1.21.1 to 1.21.11** (Java 21) through BETA
1.x, tested automatically but not played. **BETA 2.x will need 26.1 or
newer:** see [Moving to Minecraft 26.x](docs/upgrading-and-troubleshooting.md#moving-to-minecraft-26x).

Which releases a server is told about is its update channel
(`updates.channel`, or quick settings › Updates): Stable for full releases,
Pilot for pilots too.

## Quick install

1. Download the latest `EzVac-*.jar` from the
   [releases](https://github.com/EZvac-Plugin/EZvac/releases) or
   [Modrinth](https://modrinth.com/plugin/ezvac-plugin).
2. Put it in your server's `plugins` folder.
3. Start the server, then follow [Getting started](docs/getting-started.md) to
   build your first system.

## Documentation

| Page | What's in it |
| --- | --- |
| [Getting started](docs/getting-started.md) | Install, then a first working system step by step |
| [Equipment and thermostats](docs/equipment.md) | Thermostats, every kind of equipment and its casing, profiles, RPM monitors |
| [Vents and airflow](docs/airflow.md) | Linking vents, how far air reaches, pools |
| [Dehumidifiers and humidifiers](docs/dehumidifiers-and-humidifiers.md) | Building and using them, "Air too cold" |
| [Refrigeration](docs/fridges-and-freezers.md) | Mini fridges, fridges, refrigerators and deep freezers: building them, how they cool, frost |
| [Air quality and air purifiers](docs/air-quality.md) | The AQI, pollen, smoke and dust, and purifiers with coral filters |
| [Sensors and readings](docs/sensors.md) | Thermometers, hygrometers, sensors and sensor hubs |
| [Weather stations](docs/weather-stations.md) | A tower that forecasts the next seven days; `/hvac forecast` |
| [Screens and control menus](docs/screens-and-menus.md) | The screens on every device, the setpoint menus, and quick settings |
| [Climate](docs/climate.md) | Outdoor temperature, weather, humidity, feels-like, variance |
| [Wind](docs/wind.md) | Each day's wind, where it blows, and what it does |
| [HUD and placeholders](docs/hud-and-placeholders.md) | `/hvac hud` and every `%ezvac_*%` placeholder |
| [Configuration](docs/configuration.md) | Every `config.yml` setting, sounds |
| [Commands and permissions](docs/commands.md) | Every command |
| [Upgrading and troubleshooting](docs/upgrading-and-troubleshooting.md) | Upgrading, backups, common problems |
| [The EZvac API](docs/api.md) | Building addons: readings, devices, controls and events |

The full technical behaviour is in [docs/features.txt](docs/features.txt).

## Extending EZvac

Addons are ordinary Paper plugins using EZvac's public API: climate readings,
every device, controlling them, and events. Add
`com.github.EZvac-Plugin.EZvac:ezvac-api` from [JitPack](https://jitpack.io/#EZvac-Plugin/EZvac)
and see [The EZvac API](docs/api.md). Official and third-party addons are
listed on the [EZvac-Plugin](https://github.com/EZvac-Plugin) page.

## Statistics

[![EZvac on bStats](https://bstats.org/signatures/bukkit/EZvac.svg)](https://bstats.org/plugin/bukkit/EZvac/35377)

Servers running BETA 1.0 Pilot 9 or newer send anonymous statistics through
bStats: counts and versions, nothing that names a server or a player. Quick
settings › Anonymous statistics shows what is sent and switches each part, or
all of it, off; see [metrics](docs/configuration.md#metrics).

## Building from source

You need Java 21 or newer and Maven 3.9 or newer. One jar runs on 1.21.x and
on 26.1 to 26.3.

```shell
mvn clean package
```

The plugin is written to `ezvac-plugin/target/`. `mvn verify` also runs the
tests, as GitHub Actions does on every push and pull request. See
[CONTRIBUTING.md](CONTRIBUTING.md).

## License

EZvac is released under the [MIT License](LICENSE) from ALPHA 1.5 on.
Copyright (c) 2026 bladestech and EZvac contributors. Releases through ALPHA
1.1 HOTFIX were dedicated to the public domain under
[The Unlicense](https://unlicense.org/), irrevocably, and stay so.
Contributions are accepted under the MIT terms; see
[CONTRIBUTING.md](CONTRIBUTING.md).
