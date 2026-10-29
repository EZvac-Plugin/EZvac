# EZvac


<img width="1080" height="540" alt="ezv_D" src="https://github.com/user-attachments/assets/95b2a2b3-4525-4d7d-b38b-214916050f73" />


EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
26.1 to 26.3 and its forks, including Purpur. It still runs on 1.21.x until
BETA 2.x. Build heat pumps, air conditioners and
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
- **Sensors:** thermometers, hygrometers, air monitors, and button sensors
  that report to a sensor hub sign.
- **Lit screens on every sign** and click-to-open control menus for setpoints.
- **An on-screen HUD** and PlaceholderAPI placeholders.
- **Fahrenheit or Celsius**, server wide, and configurable sounds. No fuel,
  power or items needed.

> **Release status:** EZvac **BETA 1.0 Pilot 1** is the first BETA. It
> runs on Minecraft 26.1 to 26.3 (Paper or Purpur), and still on 1.21.x
> until BETA 2.x. Every screen is redesigned as a status strip coloured by
> what the device is doing. The pale garden, the dappled forest and the
> sulfur caves get their own climate and air, and admins are told when a
> newer EZvac is out. `/hvac quicksettings` gives owners one menu to switch
> device kinds, air quality, pollen, wind and the seasons on or off, and to
> back the registry up. Heating and cooling now go by building size: one
> three-star unit per 5,000 sq ft, so big builds need more equipment. Your
> save carries over unchanged.
>
> ALPHA 3.2 added air quality. Every room tracks
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

> **BETA 1.0 pilots:** pilots are test builds of BETA 1.0, published as
> prereleases (betas on Modrinth) before the full release. A server running
> a pilot is told about newer pilots; one running a full release only about
> full releases.
>
> **1.21.x is on its way out.** BETA 1.x still runs on Minecraft 1.21.x
> (tested on 1.21.1; 1.21.2 to 1.21.11 should work but aren't tested yet) and
> says so at start-up, but **BETA 2.x will drop 1.21.x entirely**: it needs
> Minecraft 26.1 or newer. Update your server before then; see
> [Upgrading](docs/upgrading-and-troubleshooting.md#moving-to-minecraft-26x).

## Quick install

1. Download `EzVac-BETA-1.0-P1.jar` from the
   [releases](https://github.com/EZvac-Plugin/EZvac/releases) or
   [Modrinth](https://modrinth.com/plugin/ezvac-plugin).
2. Put it in your server's `plugins` folder. You need Paper 26.1 to 26.3 or
   a fork such as Purpur, on Java 25. 1.21.x on Java 21 still works until
   BETA 2.x.
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
| [Screens and control menus](docs/screens-and-menus.md) | The screens on every sign, the setpoint menus, and quick settings |
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

You need Java 21 or newer and Maven 3.9 or newer. EZvac builds against the
Paper 1.21.1 API, so one jar runs on 1.21.1 and on 26.1 to 26.3.

```shell
mvn clean package
```

The plugin is written to `ezvac-plugin/target/EzVac-BETA-1.0-P1.jar`. `mvn verify` also runs
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
