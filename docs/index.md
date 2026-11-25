---
title: Real heating, cooling and indoor climate for Minecraft
hide:
  - navigation
  - toc
---

<div class="ez-hero" markdown>

![EZvac](https://github.com/user-attachments/assets/95b2a2b3-4525-4d7d-b38b-214916050f73){ .ez-banner }

# Real heating, cooling and indoor climate for Minecraft servers

Thermostats, heat pumps and vents that condition real rooms and pools, built
from blocks and simulated like the real thing, while the outdoors follows the
biome, the season, the time of day and the weather. No client mod: it runs on
Paper and Purpur.

[:material-download: Download](https://modrinth.com/plugin/ezvac-plugin){ .md-button .md-button--primary }
[:material-play: Getting started](getting-started.md){ .md-button }

</div>

<div class="ez-downloads">
  <a class="ez-release" id="ez-stable" href="https://github.com/EZvac-Plugin/EZvac/releases">
    <span class="ez-kind">Latest release</span>
    <span class="ez-tag">Loading…</span>
    <span class="ez-date"></span>
  </a>
  <a class="ez-release ez-pilot" id="ez-pilot" href="https://github.com/EZvac-Plugin/EZvac/releases">
    <span class="ez-kind">Latest pilot</span>
    <span class="ez-tag">Loading…</span>
    <span class="ez-date"></span>
  </a>
</div>

<p class="ez-versions">Minecraft 26.1 to 26.3 on Java 25 · 1.21.x on Java 21 through BETA 1.x</p>

## What it does

<div class="grid cards" markdown>

-   :material-thermometer:{ .lg } **Heating and cooling**

    ---

    Rooms with iron trapdoor vents, pools with copper grates. Single-stage,
    two-stage and variable-speed equipment, and profiles.

    [:octicons-arrow-right-24: Equipment](equipment.md)

-   :material-weather-partly-snowy-rainy:{ .lg } **A living climate**

    ---

    Every biome has its own weather, with seasons, wind, humidity and a
    "feels like" temperature.

    [:octicons-arrow-right-24: Climate](climate.md)

-   :material-air-filter:{ .lg } **Air you can breathe**

    ---

    Dehumidifiers, humidifiers and air purifiers, with air quality scored as
    the real AQI: smoke, dust and pollen.

    [:octicons-arrow-right-24: Air quality](air-quality.md)

-   :material-fridge-outline:{ .lg } **Refrigeration**

    ---

    Mini fridges, fridges, refrigerators and deep freezers, with frost.

    [:octicons-arrow-right-24: Refrigeration](fridges-and-freezers.md)

-   :material-home-clock-outline:{ .lg } **Houses that keep running**

    ---

    Devices work on in unloaded chunks, while nobody is near.

    [:octicons-arrow-right-24: Upgrading and troubleshooting](upgrading-and-troubleshooting.md)

-   :material-radar:{ .lg } **Sensors and forecasts**

    ---

    Thermometers, hygrometers, sensor hubs, and weather station towers that
    forecast the next seven days.

    [:octicons-arrow-right-24: Weather stations](weather-stations.md)

-   :material-monitor-dashboard:{ .lg } **Screens and a HUD**

    ---

    Screens with buttons on every device, control menus, an on-screen HUD and
    PlaceholderAPI placeholders.

    [:octicons-arrow-right-24: Screens and menus](screens-and-menus.md)

-   :material-compass-outline:{ .lg } **A guided start**

    ---

    A first-start setup for admins, and a tutorial that builds your first
    system in a world of its own.

    [:octicons-arrow-right-24: Getting started](getting-started.md)

</div>

## Addons

<div class="grid cards ez-two" markdown>

-   **[EZclimates](https://github.com/EZvac-Plugin/EZclimates)** · official

    Weather and climate you feel: body temperature, snow that settles, food
    that spoils.

-   **[EZdebug+](https://github.com/EZvac-Plugin/EZdebugPlus)** · official

    See every system's air, leaks and links at once.

-   **[MapPeek](https://github.com/EZvac-Plugin/MapPeek)** · official

    Weather station coverage and wind on squaremap and BlueMap.

-   **[EZStats](https://github.com/colebolebole/EZStats)** · third-party

    A web dashboard to watch and control your devices.

</div>

Addons are ordinary Paper plugins on EZvac's open API, and anyone can make
their own: see [The EZvac API](api.md).

## Translations

Since BETA 1.1, EZvac can speak your players' language: set `language` in
`config.yml`. Only English is finished so far, and translators are welcome:
**EZvac is translated on [Weblate](https://hosted.weblate.org/engage/ezvac/)**, in your browser, no files or
code needed. Questions are welcome on our [Discord](https://discord.gg/tCedQgFFDU).
Server owners can also change single words. See [Translations](translating.md).

How far each language has come (it updates as people translate):

[![Translation status](https://hosted.weblate.org/widget/ezvac/plugin/multi-auto.svg)](https://hosted.weblate.org/engage/ezvac/)

## Built to be safe

Every release is tested against save files from every earlier release,
upgrades back up your data first, and EZvac never touches a save it can't
read. Free and open source under the MIT License.
