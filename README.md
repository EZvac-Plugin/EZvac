<div align="center">

<img width="1080" height="540" alt="EZvac" src="https://github.com/user-attachments/assets/95b2a2b3-4525-4d7d-b38b-214916050f73" />

**Real heating, cooling and indoor climate for Minecraft servers.**

[![Release](https://img.shields.io/github/v/release/EZvac-Plugin/EZvac?include_prereleases&label=release&color=0e7c86&logo=github&style=for-the-badge&labelColor=1f2328)](https://github.com/EZvac-Plugin/EZvac/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1%E2%80%9326.3-0e7c86?style=for-the-badge&labelColor=1f2328&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiIgc2hhcGUtcmVuZGVyaW5nPSJjcmlzcEVkZ2VzIj48cmVjdCB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIGZpbGw9IiM4YTVhM2IiLz48cmVjdCB3aWR0aD0iMTYiIGhlaWdodD0iNSIgZmlsbD0iIzZhYWEzYSIvPjxyZWN0IHg9IjIiIHk9IjUiIHdpZHRoPSIyIiBoZWlnaHQ9IjIiIGZpbGw9IiM2YWFhM2EiLz48cmVjdCB4PSI3IiB5PSI1IiB3aWR0aD0iMiIgaGVpZ2h0PSIzIiBmaWxsPSIjNmFhYTNhIi8+PHJlY3QgeD0iMTIiIHk9IjUiIHdpZHRoPSIyIiBoZWlnaHQ9IjIiIGZpbGw9IiM2YWFhM2EiLz48cmVjdCB4PSI0IiB5PSIxMCIgd2lkdGg9IjIiIGhlaWdodD0iMiIgZmlsbD0iIzZiNDMyOSIvPjxyZWN0IHg9IjEwIiB5PSIxMiIgd2lkdGg9IjIiIGhlaWdodD0iMiIgZmlsbD0iIzZiNDMyOSIvPjxyZWN0IHg9IjEyIiB5PSI4IiB3aWR0aD0iMSIgaGVpZ2h0PSIxIiBmaWxsPSIjYTc3NzRmIi8+PHJlY3QgeD0iMiIgeT0iMTMiIHdpZHRoPSIxIiBoZWlnaHQ9IjEiIGZpbGw9IiNhNzc3NGYiLz48L3N2Zz4=)](#minecraft-versions)
[![Java](https://img.shields.io/badge/Java-25-0e7c86?logo=openjdk&style=for-the-badge&labelColor=1f2328)](#minecraft-versions)
[![Modrinth](https://img.shields.io/modrinth/dt/ezvac-plugin?label=downloads&logo=modrinth&color=0e7c86&style=for-the-badge&labelColor=1f2328)](https://modrinth.com/plugin/ezvac-plugin)
[![Build](https://img.shields.io/github/actions/workflow/status/EZvac-Plugin/EZvac/build.yml?branch=main&label=build&logo=githubactions&style=for-the-badge&labelColor=1f2328)](https://github.com/EZvac-Plugin/EZvac/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/EZvac-Plugin/EZvac?color=0e7c86&logo=opensourceinitiative&style=for-the-badge&labelColor=1f2328)](LICENSE)

[![Download](https://img.shields.io/badge/Download-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHBhdGggZmlsbD0iI2ZmZiIgZD0iTTcgMWgydjdsMi41LTIuNSAxLjQgMS40TDggMTEuOCAzLjEgNi45bDEuNC0xLjRMNyA4eiIvPjxwYXRoIGZpbGw9IiNmZmYiIGQ9Ik0yIDEyaDJ2MS41aDhWMTJoMnYzLjVIMnoiLz48L3N2Zz4=)](https://modrinth.com/plugin/ezvac-plugin)
[![Getting started](https://img.shields.io/badge/Getting%20started-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHBhdGggZmlsbD0iI2ZmZiIgZD0iTTQgMmwxMCA2LTEwIDZ6Ii8+PC9zdmc+)](docs/getting-started.md)
[![Docs](https://img.shields.io/badge/Docs-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHBhdGggZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMyAxaDdsNCA0djEwSDN6TTkuNSAxLjhWNS41aDMuN3pNNSA3LjVoNnYxLjNINXpNNSAxMGg2djEuM0g1ek01IDEyLjVoNHYxLjNINXoiLz48L3N2Zz4=)](#documentation)
[![Addons](https://img.shields.io/badge/Addons-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHJlY3QgZmlsbD0iI2ZmZiIgeD0iMS41IiB5PSIxLjUiIHdpZHRoPSI1LjUiIGhlaWdodD0iNS41IiByeD0iMSIvPjxyZWN0IGZpbGw9IiNmZmYiIHg9IjkiIHk9IjEuNSIgd2lkdGg9IjUuNSIgaGVpZ2h0PSI1LjUiIHJ4PSIxIi8+PHJlY3QgZmlsbD0iI2ZmZiIgeD0iMS41IiB5PSI5IiB3aWR0aD0iNS41IiBoZWlnaHQ9IjUuNSIgcng9IjEiLz48cGF0aCBmaWxsPSIjZmZmIiBkPSJNMTAuOSA5aDEuN3YyLjJoMi4ydjEuN2gtMi4ydjIuMmgtMS43di0yLjJIOC43di0xLjdoMi4yeiIvPjwvc3ZnPg==)](#addons)
[![Changelog](https://img.shields.io/badge/Changelog-f69500?style=for-the-badge&logo=data:image/svg%2bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI+PHJlY3QgZmlsbD0iI2ZmZiIgeD0iMS41IiB5PSIyLjUiIHdpZHRoPSIyIiBoZWlnaHQ9IjIiIHJ4PSIuNSIvPjxyZWN0IGZpbGw9IiNmZmYiIHg9IjUiIHk9IjIuNSIgd2lkdGg9IjkuNSIgaGVpZ2h0PSIyIiByeD0iLjUiLz48cmVjdCBmaWxsPSIjZmZmIiB4PSIxLjUiIHk9IjciIHdpZHRoPSIyIiBoZWlnaHQ9IjIiIHJ4PSIuNSIvPjxyZWN0IGZpbGw9IiNmZmYiIHg9IjUiIHk9IjciIHdpZHRoPSI5LjUiIGhlaWdodD0iMiIgcng9Ii41Ii8+PHJlY3QgZmlsbD0iI2ZmZiIgeD0iMS41IiB5PSIxMS41IiB3aWR0aD0iMiIgaGVpZ2h0PSIyIiByeD0iLjUiLz48cmVjdCBmaWxsPSIjZmZmIiB4PSI1IiB5PSIxMS41IiB3aWR0aD0iNyIgaGVpZ2h0PSIyIiByeD0iLjUiLz48L3N2Zz4=)](CHANGELOG.md)

</div>

EZvac is a standalone HVAC and indoor-climate plugin for Paper and its forks,
such as Purpur. Build heat pumps, air conditioners and furnaces out of
dispensers, link vents to a room, and a thermostat keeps it at the
temperature you set, while the outdoors follows the biome, the season, the
time of day and the weather.

> [!NOTE]
> **EZvac is in BETA.** The latest pilot,
> [BETA 1.0 Pilot 10](https://github.com/EZvac-Plugin/EZvac/releases/tag/BETA-1.0-P10),
> welcomes new servers with a first-start setup and a guided tutorial that
> builds your first system with you. Pilots are test builds ahead of the full
> BETA 1.0.

<!-- A GIF here: a house warming up with the HUD on screen, or the tutorial's glowing ghost blocks. -->

## Features

<table>
<tr>
<td width="50%" valign="top">

**🌡️ Heating and cooling systems**<br>
Rooms (iron trapdoor vents) or pools (copper grate vents), with
single-stage, two-stage and variable-speed equipment, and profiles.

</td>
<td width="50%" valign="top">

**🌦️ A living climate**<br>
A realistic climate for every biome: seasons, weather, wind, humidity,
"feels like", colder air up mountains and steady air underground.

</td>
</tr>
<tr>
<td valign="top">

**💨 Air you can breathe**<br>
Dehumidifiers, humidifiers and air purifiers, with air quality scored as
the real AQI: smoke, dust and pollen.

</td>
<td valign="top">

**🧊 Refrigeration**<br>
Mini fridges, fridges, refrigerators and deep freezers, with frost.

</td>
</tr>
<tr>
<td valign="top">

**🏠 Houses that keep running**<br>
Devices work on in unloaded chunks, while nobody is near.

</td>
<td valign="top">

**📡 Sensors and forecasts**<br>
Thermometers, hygrometers, sensor hubs, and weather station towers that
forecast the next seven days.

</td>
</tr>
<tr>
<td valign="top">

**🖥️ Screens and a HUD**<br>
Screens with buttons on every device, control menus, an on-screen HUD and
PlaceholderAPI placeholders.

</td>
<td valign="top">

**🧭 A guided start**<br>
A first-start setup for admins, and a tutorial that builds your first
system in a world of its own.

</td>
</tr>
</table>

Fahrenheit or Celsius, server wide. No fuel, power or items needed.

## Quick install

1. Download the latest `EzVac-*.jar` from
   [Modrinth](https://modrinth.com/plugin/ezvac-plugin) or the
   [releases](https://github.com/EZvac-Plugin/EZvac/releases).
2. Put it in your server's `plugins` folder and start the server.
3. Join as an operator: EZvac asks, in chat, to be set up, then offers a
   guided tutorial that builds your first system with you.

[Getting started](docs/getting-started.md) has the same steps written out.

## Minecraft versions

| Minecraft | Java | |
| --- | --- | --- |
| **26.1 to 26.3** | 25 | Played and tested first |
| **1.21.1 to 1.21.11** | 21 | Tested automatically, through BETA 1.x |

**BETA 2.x will need 26.1 or newer:** see
[Moving to Minecraft 26.x](docs/upgrading-and-troubleshooting.md#moving-to-minecraft-26x).

## Documentation

**Start here:** [Getting started](docs/getting-started.md) ·
[Commands and permissions](docs/commands.md) ·
[Upgrading and troubleshooting](docs/upgrading-and-troubleshooting.md)

| Building | Climate | Server and addons |
| --- | --- | --- |
| [Equipment and thermostats](docs/equipment.md) | [Climate](docs/climate.md) | [Screens and menus](docs/screens-and-menus.md) |
| [Vents and airflow](docs/airflow.md) | [Wind](docs/wind.md) | [HUD and placeholders](docs/hud-and-placeholders.md) |
| [Dehumidifiers and humidifiers](docs/dehumidifiers-and-humidifiers.md) | [Air quality and purifiers](docs/air-quality.md) | [Configuration](docs/configuration.md) |
| [Refrigeration](docs/fridges-and-freezers.md) | [Weather stations](docs/weather-stations.md) | [The EZvac API](docs/api.md) |
| [Sensors and readings](docs/sensors.md) | | |

The full technical behaviour is in [docs/features.txt](docs/features.txt).

## Addons

| Addon | What it adds |
| --- | --- |
| [EZclimates](https://github.com/EZvac-Plugin/EZclimates) | Official: weather and climate you feel, snow that settles, food that spoils |
| [EZdebug+](https://github.com/EZvac-Plugin/EZdebugPlus) | Official: see every system's air, leaks and links at once |
| [MapPeek](https://github.com/EZvac-Plugin/MapPeek) | Official: weather station coverage and wind on squaremap and BlueMap |
| [EZStats](https://github.com/colebolebole/EZStats) | Third-party: a web dashboard to watch and control your devices |

Addons are ordinary Paper plugins on EZvac's public API: climate readings,
every device, controls and events. Add
`com.github.EZvac-Plugin.EZvac:ezvac-api` from
[JitPack](https://jitpack.io/#EZvac-Plugin/EZvac) and see
[The EZvac API](docs/api.md). More are listed on the
[EZvac-Plugin](https://github.com/EZvac-Plugin) page.

## Statistics

[![EZvac on bStats](https://bstats.org/signatures/bukkit/EZvac.svg)](https://bstats.org/plugin/bukkit/EZvac/35377)

Servers running BETA 1.0 Pilot 9 or newer send anonymous statistics through
bStats: counts and versions, nothing that names a server or a player. Quick
settings › Anonymous statistics shows what is sent and switches each part, or
all of it, off; see [metrics](docs/configuration.md#metrics).

<details>
<summary><b>Building from source</b></summary>

<br>

You need Java 21 or newer and Maven 3.9 or newer. One jar runs on 1.21.x and
on 26.1 to 26.3.

```shell
mvn clean package
```

The plugin is written to `ezvac-plugin/target/`. `mvn verify` also runs the
tests, as GitHub Actions does on every push and pull request. See
[CONTRIBUTING.md](CONTRIBUTING.md).

</details>

## License

EZvac is released under the [MIT License](LICENSE). Copyright (c) 2026
bladestech and EZvac contributors.

<details>
<summary>Older releases</summary>

<br>

The MIT License applies from ALPHA 1.5 on. Releases through ALPHA 1.1 HOTFIX
were dedicated to the public domain under
[The Unlicense](https://unlicense.org/), irrevocably, and stay so.
Contributions are accepted under the MIT terms; see
[CONTRIBUTING.md](CONTRIBUTING.md).

</details>
