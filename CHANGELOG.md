# Changelog

All notable changes to EZvac are recorded here.

## BETA 1.0 Pilot 5

**EZdebug, built in: see the system you're linking as EZvac does,** its
room's edge and the openings it leaks through included. The save is
unchanged (format 8).

### EZdebug

- **While you hold the link tool with a thermostat's system selected,** you see:
  - its equipment glowing through walls, its signs framed and its vents marked;
  - its vents puffing while they work, bigger than EZdebug+ drew them before, with a column rising out of them;
  - the edge of the space its air fills, dotted in near you;
  - **the openings it leaks through** (doorways, windows, holes), glowing red, through walls.
- **Only you see it,** and nothing is saved with the world.
- **In `/hvac addons`** it's listed first, as EZdebug (built in), with its switch (`ezdebug.enabled`).
- **EZdebug+** shows all this for every system at once, and more. While it's installed and switched on, the built-in one stands down.

### API 1.10

- `systemSpace(world, system)` and `SystemSpace`: the edge of the space a
  system fills, and the openings it leaks through, as EZvac last mapped it.

### Upgrading

- `ezdebug` is added to `config.yml` with its comments.

## BETA 1.0 Pilot 4

**Seasons are 30 days long, and rain turns to snow by EZvac's own air.**
Standing in the sun feels warmer than the shade, and fires burning in the
open smoke up the air downwind. Official addons are switched from a new
Addons menu, quick settings has more in it, and devices show on screens
only. The save is unchanged (format 8).

### 30-day seasons

- **Each season is 30 Minecraft days by default** (a 120-day year), up from
  15. Natural water lags the air by 10 days, up from 5.
- **Every world keeps its season and day.** A server on the stock 15 moves to
  30 on its first Pilot 4 start, and summer day 8 stays summer day 8. A
  length an owner chose is kept.
- **The length can be changed any time now** (restart after): `seasons.yml`
  records each calendar's length, so changing it never sends a world into
  another season. The old file is kept beside it.

### Rain or snow

- **Rain or snow by EZvac's air,** season and time of day included: the
  plains snow in a cold winter, a warm spell rains on the tundra, and a
  mountain top can be white while the valley rains. Vanilla decided by biome
  alone.
- **It snows below 33 °F, and in winter below 40 °F,** since real snow often
  falls a few degrees above freezing, so plains and forests snow through
  most of winter while savannas and jungles still rain.
- **Decided across the land, not by height,** from the air at the ground, so
  rain and snow meet along a wavy line that follows the cold (not in layers
  above you, and not along a grid), and a storm keeps its kind until the
  air is 2 °F past the line.
- **Dry places get rain by the season:** deserts, savannas and badlands get
  some of the rain, mostly in winter (6 spells in 10) and rarely otherwise
  (1 in 10 in spring and autumn, 1 in 20 in summer). Each rain spell is
  decided once as it starts, for the whole world, and rain that reaches them
  cools and dampens the air there like anywhere else.
  `weather.dry-areas` (`seasonal`, `never`, `thunder`, `always`) and
  `weather.dry-rain-chance` set it; `weather.snow-at-or-below-f` sets the
  snow line's temperature.
- **The weather word follows it** (the HUD, sensors, `%ezvac_weather%`), and
  so do weather stations' forecasts.
- This is what EZvac decides falls; on its own, the world still shows
  Minecraft's rain and snow. The new EZclimates addon shows EZvac's in the
  world.

### Feels like in the sun

- **The sun makes it feel warmer than the shade:** most at noon in summer,
  less in winter, little under clouds, none at night, and a breeze carries it
  away. A clear summer noon in a field feels about 8 to 10 °F warmer than the
  forest beside it.
- Only the feels-like of readings at a spot changes (the HUD, `/hvac
  temperature`, thermometers, sensors). The air temperature, rooms and
  systems don't, and weather stations stay in the shade.
- `feels-like.sun` and `feels-like.sun-strength`.

### Smoke

- **Smokers and blast furnaces smoke more than a furnace:** a smoker 6/2 and
  a blast furnace 10/5 µg/m³ an hour (fine/coarse), against a furnace's 4/2;
  before, all three were at the furnace's.
- **Torches and lanterns add a little each,** soul and copper ones too, so a
  room lit by dozens of them gets a bit hazy. Redstone torches never count.
- **Fires in the open smoke up the air downwind.** Lit campfires, furnaces,
  smokers, blast furnaces and fire that no room counts as its own spread
  their smoke with the wind: thickest beside them, stretching downwind and
  thinning as it goes, pooled round them on a calm day, up to 96 blocks.
  Ten campfires in a row make the air 25 blocks downwind Unhealthy for
  Sensitive Groups in a breeze, while upwind stays clean. It shows outdoors,
  on weather stations nearby, on maps, and indoors through each room's
  leaks. `air-quality.outdoor-smoke`.

### Official addons

- **`/hvac addons`** (also a page of quick settings) lists EZvac's official
  addons on the server, each with its version.
- **Switch an addon on and off while the server runs.** Switched off, it
  stays loaded but does nothing; the choice is kept through restarts
  (`addons.disabled`). An addon with parts, such as EZclimates (seasonal
  looks, snow and ice, planned weather, plants, body temperature), has a switch for
  each. An addon released before this says it needs updating to be switched.
- **A store row** lists the official addons the server doesn't have, with
  what each does and needs. A click puts its GitHub link in chat. EZvac
  never downloads or installs anything.
- **Updates:** a newer release of an installed addon shows on its item, and
  admins are told when they join, with EZvac's own update check
  (`updates.check`, `updates.notify-admins`).
- The list ships with EZvac and is refreshed from the EZvac-Plugin
  organisation on GitHub, so a new addon appears without an EZvac update.
  Only links to that organisation are ever shown.

### Quick settings

- **Regrouped into two rows:** Devices, Climate systems, Seasons & wind and
  Display & sound above; Addons, Backups & registry, Health and Updates
  below.
- **Seasons & wind:** move the world you stand in a day back or on, to the
  next season, pause its calendar, and set today's wind, a click at a time.
- **Display & sound:** temperatures in °F or °C, the wind's units, and
  device sounds on or off, all at once and without a restart.
- **Health** (Statistics before): what each part of EZvac costs the server's
  tick over the last minute, in milliseconds, beside the figures it already
  had. `/hvac stats` prints the same.

### Screens only

- **Devices no longer write on their signs.** Every device shows on its
  screen; the sign stays behind it, blank, as the block you make it on and
  click. Plain signs had been unsupported since BETA 1.0.
- `displays.screens` is gone. A server that had it `false` is told once at
  start-up, and gets screens.

### API 1.9

- `precipitationAt(world, x, y, z)` and the `Precipitation` enum: what is
  falling at a block right now.
- `snowLineAt(world, x, z)`: the height above which rain would come down as
  snow at a column, raining or not.
- `dailyMeanAirF(world, x, y, z)`: today's average air at a block, which snow
  on the ground answers to rather than the hour's air.
- `freezingWaterF(world, x, y, z)`: how cold natural water is getting, before
  EZvac holds liquid water at 33 °F, so an addon can freeze lakes.
- `SeasonView.progress()`: how far through the year a world is, 0 to 1.
- `WeatherPlan` and `WeatherEvent`, with `setWeatherPlan(plugin, plan)`: an
  addon can plan the weather. Stations forecast from its days, the outdoor
  air takes on its heat waves and cold snaps, a snowstorm blows a gale and a
  thunderstorm at least windy, and the weather word says Snowstorm, Heat wave
  or Cold snap where they show. Without one, nothing changes.
- `outdoorAirAt` includes the smoke of open fires nearby.
- `registerAddon(plugin, addon)`, with `Addon`, `Addon.Part` and
  `AddonSwitches` (`api.addon`): an addon registers to be switched from the
  Addons menu, with parts of its own if it has them.

### Upgrading

- New settings are added to `config.yml` with their comments: `feels-like`,
  `weather.snow-at-or-below-f` and `dry-areas`, `air-quality.sources.smoker`,
  `blast-furnace` and `torch`, `air-quality.outdoor-smoke`,
  `weather.snow-in-winter-below-f`, `weather.dry-rain-chance` and `addons`.
  A stock
  `seasons.days-per-season: 15` moves to 30 and a stock
  `water-lag-days: 5.0` to 10; the previous files are kept as backups.

## BETA 1.0 Pilot 3

**The wind blows from a direction now, and differs from region to region.**
Weather stations forecast their own area's wind and air, and the season
calendar follows Minecraft's own days. The save is unchanged (format 8), and
nothing new is saved.

### Wind direction

- **Each climate has its prevailing wind,** like the real world's belts: the
  trade winds from the east-northeast in hot climates (deserts, savannas,
  jungles), the westerlies from the west-southwest in temperate ones, and the
  polar easterlies from the northeast in cold ones. Day to day it varies
  around that, the westerlies most.
- **Rain turns it,** as a weather front does: the westerlies back to the
  south ahead of rain, blow from the southwest while it rains, and veer to
  the west-northwest once it clears.
- **The belts follow the land at a large scale.** EZvac reads the biomes
  from the world seed, so it knows places no one has explored and never
  loads a chunk; the wind turns gradually from one climate to the next.
- **It shows everywhere the wind does:** wind sensors (`Roof Gale 61km/h NW`;
  the direction gives way before a label is cut), the HUD's `{wind}`, new
  `{wind_direction}` and `{wind_degrees}` placeholders, `/hvac wind`, weather
  station screens and the forecast menu. A calm day shows no direction.
- **`/hvac wind set <level> [direction]`:** a compass point (`nw`, `wsw`) or
  degrees (`270`).

### Regional wind

- **Areas about 1,500 blocks across** are windier or calmer than the day's
  wind, by up to 40% but never more than about a level: on a Windy day one
  area can be Breezy and another a Gale.
- **They drift downwind** through the day, so a windy patch passes over.
- **`/hvac wind` gives your own area's wind,** and says when it differs from
  the world's day. Rooms, sensors and feels-like use the wind where they are.
- Tune it under `wind.regions`, or switch it off for one wind everywhere as
  before.

### Weather stations

- **Each station forecasts its own area's wind,** with its direction: on a
  rainy day, the southwest the rain brings.
- **Air quality:** the screen shows the area's AQI after the wind
  (`Windy 29km/h W · AQI 42`), and the forecast menu each day's expected AQI
  in its band's colour, with rain washing the air and wind lifting dust.

### The season calendar

- **`/time add`, sleeping or a skip moves the seasons on by every day it
  passes.** Before, only one day counted at a time.
- **A new calendar starts on Minecraft's own day,** so day 0 of a world is
  the first day of spring, and EZvac added to an old world picks up where its
  day count falls. Worlds that already have a calendar keep their season and
  day.
- Setting the clock back still never resets the year: vanilla `/time set day`
  restarts Minecraft's day count.

### A clock on the HUD

- **The time of day** joins the season on the sidebar: `Summer, day 3 · 14:30`,
  Minecraft's clock with sunrise at 06:00. With seasons off it shows alone.
  A sidebar still on the stock BETA 1.0 lines gains it on upgrade; one you've
  made your own is left as it is.
- New `{time}` token and `%ezvac_time%` placeholder (`none` in the Nether and
  the End).

### Config

- New `wind.direction` (`variation`, `fronts`) and `wind.regions`
  (`enabled`, `size-blocks`, `strength`, `drift-blocks-per-hour`), added to
  config.yml on upgrade.

### For addons: API 1.8

- `Wind` and `Wind.Day` gain `fromDegrees()`, with `compass()`,
  `towardDegrees()` and `hasDirection()`.
- `WeatherStationView` gains its area's `wind()` and `airQuality()` right
  now, and each `ForecastDay` its `aqi()`.
- New `windAt(world, x, z)` and `outdoorAirAt(world, x, z)`: the open wind and
  air anywhere, without loading a chunk, for maps. `windForecast(location,
  days)` gives a region's days.
- Every older constructor is kept, so addons built for 1.7 keep working.
- **MapPeek 0.2.0** uses it, on squaremap and now BlueMap too: wind arrows
  and icons on weather stations, Wind and Air quality layers across the
  explored map, the time and season, an optional animated wind, and a
  `/mappeek` menu.

### Tested

Live on Purpur 26.1.2 and 1.21.1, and on 26.3 with squaremap 1.4.3 and
BlueMap 5.29 on a freshly generated world. 1.21.x is still supported, but
not play-tested on every version.

## BETA 1.0 Pilot 2

**The thermostat's menu is where you run a system now,** and a thermostat can
be small: a **mini thermostat** on a wall button. The save is unchanged
(format 8), so everything carries over from Pilot 1.

### Mini thermostats

- **A thermostat on a wall button,** with a small display a little bigger than
  the button: the room's temperature, the target, and a gauge of how far the
  room has come toward it, where a full thermostat shows its ETA. Nothing a
  real thermostat leaves out: no outdoor readings or season.
- **Made like any thermostat:** look at a button on a wall and run
  `/hvac create thermostat <label> [target]`. A button on a floor or ceiling
  is refused. It's the same thermostat in every other way: its menu, linking,
  sizing, the HUD and the API.
- **Press it for its menu;** the press never powers the button. Sneak-press
  switches the system off or on.
- **A button is a mini thermostat or a sensor, never both.**
- It shows on a screen, so it needs `displays.screens` on.

### The thermostat's menu

- **Profile** (top middle): Eco, Normal or Turbo, without a settings panel.
  Click for the next, right-click for the previous. A system with a settings
  panel shares it with the panel: either one sets it, and both show it.
  Settings panels work as before.
- **What needs attention** (top left): each thing the system needs, and how to
  fix it.
- **The system** (top right): what's linked, and with building size on, its
  size, openings, and what it has of the units it needs. Undersized moved here
  from the screen.

### What needs attention

- **A system needs a vent to start.** Its conditioned air or water has
  nowhere to go without one; a pool needs a water vent. **On upgrading,
  admins are told how many thermostats have units but no vent,** since those
  no longer start.
- **The screen counts what needs attention** in amber beside its glyph (`⚠2`):
  no units, no vent, units switched off server-wide, a system that can't keep
  up, or too few units for the building's size. What keeps it from starting is
  said on its own line ("Needs a vent"); the menu says what each is.
- **Can't keep up:** when even flat out a system loses more than it makes (a
  wide opening, a gale), its menu says so, with what to do: close openings, or
  add units.
- **Undersized counts the profile.** Eco caps compressors at 2,400 RPM, so a
  heat pump or AC counts for 70% of a unit on Eco (and more on Turbo). The
  menu says when a faster profile would keep up: "Undersized for heating on
  Eco… Normal would keep up".

### Reaching the target

- **Units ease down near the target only as far as the room allows.** They
  used to slow to their minimum close to the target, and a leaky room could
  then lose as much as they made and sit just short of it for good. Now they
  never run below what the room is losing, plus enough to keep closing, so it
  gets there. In a sealed room nothing changes. A two-stage unit goes to its
  high stage when its low one isn't enough. The ETA follows the same rule.

### Update channels

- **Choose which releases you hear about:** `updates.channel` is `stable`
  (full releases and their hotfixes), `pilot` (pilots too), or `auto`, the
  default, which follows the EZvac you run. **Quick settings › Updates** has
  a Channel button.
- **What `/hvac update` and the join notice say follows the channel:** "the
  latest stable release", or a pilot on Stable waiting for the next full
  release.

### Also

- **Automatic backups are on by default,** every 24 hours, and a Pilot 1
  server's go on with this update. Admins are told once. Change or stop them
  in quick settings › Backups & registry.
- **A heating pool's water vents bubble,** where a player can see them.
- **The humidifier's bubbles are deeper** (pitch 0.5). A sound you changed in
  `config.yml` is kept.

### API 1.7

- `WeatherStationView.areaRadius()` and `areaHeight()`: a station's coverage,
  for a map. The new MapPeek addon draws it.
- `ThermostatView.profile()`, `mini()`, `attention()` (including
  `CANT_KEEP_UP`) and `blocked()`.
- Nothing an addon built for 1.6 uses has changed.

## BETA 1.0 Pilot 1

**The first BETA: EZvac on Minecraft 26.x.** One jar runs on Paper and
Purpur **26.1 to 26.3**, and still on **1.21.x**. The save is unchanged
(format 8), so your devices carry over from ALPHA 3.2 on either version.

**Heating and cooling can now go by building size.** On a new server it's on:
a big build needs more units, or more vents to reach all of it. **On a server
upgrading from ALPHA it starts off,** so existing builds heat and cool as before;
admins are told once, and switch it on with **Building size** in quick settings.
See [Size-based heating and cooling](#size-based-heating-and-cooling).

**New: weather stations,** towers that forecast the next seven days for
players nearby. See [Weather stations](#weather-stations).

### Minecraft 26.x

- **Runs on 26.1, 26.2 and 26.3,** on the Java 25 that Paper 26.x needs.
  Biomes are no longer a fixed list in 26.x. EZvac now looks them up in a
  way that works on both versions, and a data pack's biome gets its own
  climate on 26.x as it did on 1.21.1.
- **1.21.x is supported, but deprecated,** and says so at start-up. EZvac
  is played and tested on 26.x first. On 1.21.x, this release passes the
  automated tests on 1.21.1, 1.21.4, 1.21.5, 1.21.8 and 1.21.11; nobody
  plays on it for testing, and the versions in between aren't checked on
  their own. **BETA 2.x will drop all of 1.21.x**: it needs Minecraft 26.1
  or newer, so update your server before then. See
  [Moving to Minecraft 26.x](docs/upgrading-and-troubleshooting.md#moving-to-minecraft-26x).
- **New biomes:**
  - **pale garden:** a dark forest drained of colour, a touch cooler
    (62 °F at noon, 48 °F at midnight), sheltered from the wind, with little
    pollen;
  - **dappled forest:** cooler and drier than a forest (66 °F, 46 °F), with
    forest air;
  - **sulfur caves:** a steady 64 °F, warmed by lava and springs, with hazy
    air (about AQI 137, Unhealthy for Sensitive Groups, read as smoke), so
    a purifier earns its keep down there.
- **Pale oak and poplar** signs, hanging signs and trapdoors work like
  every other wood.

### Size-based heating and cooling

- **A bigger building needs more equipment.** One ★★★ unit heats or cools
  1,860 blocks of air at the normal pace: a floor of about 465 blocks, 4
  blocks high. A ★★ unit counts as 0.85 of one, a ★ as 0.70, and units add
  up. A building of 11,160 blocks of air needs six. Before, one unit cooled
  any building with enough vents.
- **Too few units is slower,** and the thermostat's screen says
  **Undersized 1/3**. A small room is quicker, up to twice the pace.
  `/hvac info` on a thermostat shows the size, the openings and what a
  system has of what it needs. `/hvac stats sizes`, and **Sizes** on the
  Statistics page of quick settings, list every system, the worst sized
  first. See [Sizing](docs/equipment.md#sizing).
- **Pools** need one ★★★ heater per 1,500 blocks of water.
- **Openings leak:** an open door, a window or a hole to the sky lets the
  outdoors in. Air also goes only 3 blocks past a roof's edge, so a vent in
  the open makes a small bubble, not a field. See
  [Roofs and the outdoors](docs/airflow.md#roofs-and-the-outdoors).
- **Neighbouring systems** whose air touches even out with each other; a
  wall or a closed door keeps them apart.
- **`/hvac sync`** also maps every system's air again, for builds made with
  `/fill` or WorldEdit.
- **Upgrading:** it starts off on a server upgrading from ALPHA, so nothing
  changes until the owner switches **Building size** on in quick settings
  (`sizing.enabled`). Admins are told so on joining, once. Switched on, a
  big build may need more units, or more vents to reach all of it;
  `/hvac stats sizes` shows which. A new server starts with it on.

### Weather stations

- **A new device: a weather station.** An admin builds a tower of copper and
  iron with three lightning rods and puts a sign on it
  (`/hvac create weatherstation`). It measures the outdoor climate 100
  blocks around it and 20 up, and forecasts the next seven days. It never
  reads a room's temperature. See [Weather stations](docs/weather-stations.md).
- **Honest forecasts:** each day's high and low, wind and pollen are exact,
  from the area's climate, the season calendar and the world's seed. Rain
  and thunder are what's certain today or tomorrow ("Rain from 15:00"), then
  chances, since Minecraft's next spell of weather is random.
- **`/hvac forecast`** (everyone) shows the stations within 400 blocks,
  nearest first, and the chosen one's seven days. Clicking a station's screen
  opens it too; an admin gets its menu, with an on/off switch.
- **Sparks and lightning:** a working station's rods spark when someone is
  near, and it counts every lightning strike on its tower.
- **It measures** at sunrise and sunset and forecasts again when the weather
  changes. A damaged tower stops it until it's rebuilt. Weather stations are
  a kind on quick settings' Devices page, and `weather-stations` in
  `config.yml` sets their range, area and sparks.

### Quick settings

- **`/hvac quicksettings`** (or `/hvac qs`, `ezvac.admin`) opens one menu for
  the settings that shape the whole server. Changes apply at once, are
  written to `config.yml`, and are logged. Every page has Back and Close.
  See [Quick settings](docs/screens-and-menus.md#quick-settings).
- **Devices:** switch a kind off server-wide (heat pumps, air conditioners,
  furnaces, boilers, pool heat pumps, dehumidifiers, humidifiers, air
  purifiers, weather stations). They stop, their signs read "Off server-wide", and no new one
  can be made. Switched back on, each runs again as it was set. A thermostat
  left with no unit to heat or cool says "Units off" rather than heating
  with no ETA.
- **Climate systems:** switch air quality, pollen, wind, the seasons and
  building size off and on without a restart. EZvac keeps heating and
  cooling; the effect just stops shaping the climate. Pollen can now be
  switched off on its own (`air-quality.pollen`).
- **Backups & registry:** back the registry up now, or automatically every 4, 8, 12,
  24 or 48 hours or every week of real time, to `plugins/EZvac/backups/`.
  The newest 10 automatic ones are kept. See
  [Restoring a backup](docs/upgrading-and-troubleshooting.md#restoring-a-backup).
- **Statistics** and **Updates** pages: what `/hvac stats` (with every
  system's size) and `/hvac update` say, and the update check's two
  switches.

### Screens

- **Every screen is redesigned as a status strip.** A header with the
  device's name is coloured by what it's doing: blue and ❄ cooling, orange
  and ♨ heating, green holding, grey off, amber when it needs you, and the
  AQI's colour on air monitors and purifiers. Under it, an opaque panel shows
  the main reading big and centred.
- **The sign behind a screen is blank,** so only the screen shows. On 26.x
  the sign's text showed through the screen.
- **Plain signs are no longer supported.** `displays.screens: false` still
  shows the old four-line signs, but they get none of the new lines (such as
  **Undersized**) and may be removed in a later release. Bedrock players
  through Geyser can't see screens.
- **RPM monitors show the tier as `T1` to `T3`** instead of stars
  (`HP T3 ×2 2,950`): the same tier in half the room.

### HUD

- **The sidebar is organised in sections:** Here (temperature, feels-like,
  comfort, humidity, air), Outside (temperature and weather, wind, season)
  and System (the system and what it's doing, airflow), each under its
  heading. The pool sidebar has Water, Outside and Pool. The look is the
  same.
- **A stock sidebar moves to the sections** when you upgrade; one you
  changed is left as it is.
- **A fancier sidebar:** each section has an accent heading with a rule,
  the comfort bar is a gauge running blue through green to red with a ◆
  where it feels, and the system's line starts with an icon for what it's
  doing (♨ heating, ❄ cooling, ● holding). The new `{mode_icon}` token gives
  the icon; `hud.bar.style: blocks` brings back the old bar, which addon
  meters keep.
- **An idle system says what it holds:** `{mode}` and `%ezvac_mode%` read
  `Holding 72.0°F` instead of `Idle`, as its screen does. The new
  `{system_mode}` token gives `Home: Holding 72.0°F`.
- **Airflow shows its strength:** `Airflow: Strength 100%` in green in a
  vent's core, less towards the edge of its reach, gold under 50%, and
  `Outside` beyond it. It used to say `Core` or `62% leakage`, which read as
  the opposite of what it meant. Pools say `Circulation: Strength 62%`;
  `%ezvac_zone%` and `/hvac temperature` say the same.
- **Pollen levels are shorter** on the HUD and in `%ezvac_pollen%`: `Med`
  and `V.High` instead of Moderate and Very high, so no level widens the
  sidebar. Screens and commands keep the full words.

### Update notifications

- **EZvac tells you when a newer version is on GitHub.** It checks in the
  background at start-up and every 6 hours, logs a newer release with a
  link, and tells players with `ezvac.admin` when they join.
- **A server on a pilot** hears about newer pilots and full releases. **One
  on a full release** only hears about full releases and their hotfixes.
- **`/hvac update` checks now** (`ezvac.admin`, chat or console). It says
  you're on the latest, or names the newer release and whether it runs on
  this server's Minecraft. A 1.21.x server is told that BETA 2.x needs
  Minecraft 26.1 or newer, with a link to the upgrade guide, and which
  newer release still runs on 1.21.x. See
  [Commands](docs/commands.md#hvac-update).
- **The notifier says the same,** so a 1.21.x server isn't just told to
  install a BETA 2.x it can't run.
- `updates.check: false` in `config.yml` turns the background checks off;
  `/hvac update` still works. See
  [Configuration](docs/configuration.md#updates).

### Fixed

- **Equipment water and lava stay in their cells.** A unit's water could
  flow into the output cell of a unit beside it in the same casing, and
  stop it from starting; and a unit linked before its casing was finished
  flooded the build. Now a unit's water or lava never flows, a furnace's
  lava lights no fires and doesn't harden into obsidian beside water, and a
  unit starts over water or lava that is only flowing through its cell. A
  source block left over from before still has to be cleared once.
- **A stock sidebar from 1.8 to 2.2 never got newer lines.** 3.0 renamed
  its `{group}` to `{system}`, after which those sidebars weren't recognised
  as stock, so they missed the season, addons, air and wind lines. Every
  release's stock sidebar now moves to the sections. A test now upgrades
  every released `config.yml` to today's: nothing is missing, nothing a
  server set changes, and nothing warns.
- **RPM monitors no longer cut off the speed.** A long row, such as ten or
  more pool heat pumps, or almost any row on a hanging sign, lost its speed
  at the edge of the sign, and a furnace's `on` became `o`. A row now gives
  up its count and tier before its speed, and only the kind is ever cut.

### A broken hvac.yml

- **Admins are told in game,** a moment after joining, when `hvac.yml` was
  recovered from a backup at this start or has entries kept aside, not only
  in the server log. Good news too: an upgrade to a newer save format says
  it went through, and where the original was kept.
- **One more fallback:** with `hvac.yml.bak` unreadable too, EZvac recovers
  from the newest readable backup in `plugins/EZvac/backups/`.
- **Stopped, not gone:** with nothing readable, EZvac stays loaded but runs
  and saves nothing; `/hvac` and the join message say why, instead of it
  looking uninstalled. `/hvac quicksettings` then opens a recovery menu:
  salvage the file, or restore a backup, each with a click, then a second.
- **Quick settings' Backups page is Backups & registry** and shows `hvac.yml`'s health: when it was
  last saved (moved from Statistics), how it read at this start, and what was
  kept aside, which a click, then a second click, discards.
- **`/hvac registry`** lists what was kept aside, and
  **`/hvac registry repair --force`** discards it on purpose, or salvages an
  unreadable file, keeping every entry that still reads. A file from a newer
  EZvac is never forced. Upgrading never triggers any of this. See
  [When hvac.yml is broken](docs/upgrading-and-troubleshooting.md#when-hvacyml-is-broken).

### API 1.6

- **Everything new in BETA 1.0 can be read by addons:** a system's size and
  what it needs (`systemSize`), the quick settings switches
  (`serverSettings`), weather stations and their forecasts
  (`weatherStations`, `weatherStationAt`, `weatherStationsNear`), a
  reading's `airflowStrength()`, and a thermostat's `unitsOff()`.
- **Nothing an addon built for 1.5 uses has changed.** Weather stations are
  kept out of `HvacDevice` and the device events, so a `switch` over every
  device kind stays complete. See [The EZvac API](docs/api.md).

### Also

- **`/hvac help` is shorter:** players see only their own commands, and
  admins get two more pages, `/hvac help build` and `/hvac help server`,
  each one chat screen. Tab completion only offers commands you can run.
  No command changed. See [Commands](docs/commands.md).
- **The HUD's version line** shows BETA versions short: `B1.0_P1`.
- **Your config.yml** gains the `updates`, `devices`, `backups`, `sizing`
  and `weather-stations` sections and `air-quality.pollen`, with their
  comments.

## ALPHA 3.2 HOTFIX 1

A bug fix only. The save is unchanged (format 8), so this is a drop-in
upgrade, and going back to 3.2 is a jar swap.

### Fixed

- **An air purifier was hard to build.** It could only be made on a living
  tube coral block, and tube coral dies within seconds away from water, so
  the coral was often dead before `/hvac create purifier` ran. Now:
  - a purifier can be made with nothing under its iron block, or on dead
    coral, and waits with `No filter` or `Filter spent`;
  - the first tube coral block placed under it is a fresh filter, and it
    stays alive there, as every filter does.

## ALPHA 3.2

**Air quality.** Every room now knows what is in its air: fine particles
(PM2.5), dust (PM10) and pollen. EZvac scores the particles as the US EPA Air
Quality Index (AQI). Campfires and fire fill a room with smoke, deserts and
the Nether bring dust and ash, and spring brings pollen. **Air purifiers**
clean it, and their tube coral filters fill up and die.

**Wind.** Every day now has its own wind, from Calm to a Gale. It makes the
outdoors feel colder, makes houses leakier so systems work harder, and whips
up dust storms in the desert.

### Added

- **Air quality in every room and standalone space.**
  - **The AQI** is the higher of the PM2.5 and PM10 scores, from Good to
    Hazardous. When the air isn't Good, EZvac says why: smoke or dust.
  - **Pollen is not part of the AQI;** it has its own level, from Low to Very
    high.
  - **A room's air drifts toward the outdoors** through the gaps in the
    building, as its moisture does.
  - **Particles settle out of still air,** the big ones fastest.
  - **A system's own filter** cleans its room while its equipment runs. It
    catches pollen and dust well, smoke poorly.
- **What burns makes smoke:** lit campfires, fire, lava, lit furnaces,
  smokers and blast furnaces, and lit candles. A doused campfire adds nothing,
  torches don't count, and EZvac's own furnaces and boilers are sealed.
- **Outdoor air by biome.**
  - **Pollen** is highest in flower forests, meadows and cherry groves.
  - **Dust** is highest in deserts and badlands.
  - **The Nether** brings smoke, spores and ash; the basalt deltas are
    Hazardous.
  - **The End** is clean.
  - **Pollen follows the seasons:** its peak is in spring, there is less in
    summer and autumn, and none in winter.
  - **Rain and snow** wash the air.
- **Air purifiers:** an iron block on a tube coral block, with a sign;
  `/hvac create purifier`.
  - **Fan speeds, like a real one:** Low, Medium, High, and Auto, which
    idles on Low and ramps up as the AQI or the pollen gets worse. New
    purifiers start in Auto.
  - **The coral is the filter.** It fills faster the dirtier the air. A full
    filter's coral dies; break it and place fresh tube coral.
  - **Breaking the filter** leaves the purifier waiting for a new one.
  - **The coral never dies on its own** while it is a filter.
- **Air monitors and air sensors.**
  - `/hvac create airmonitor` turns a sign into an air monitor, showing the
    AQI, its cause and the pollen.
  - **A cherry button** is an air sensor for sensor hubs.
- **Daily wind.**
  - **Each day draws a level:** Calm, Breezy, Windy or Gale, from the world
    seed and the day. Everyone gets the same wind, and nothing is saved.
  - **Spring and autumn are windier,** summer calmer.
  - **It gusts** through the day, eases over from one day to the next, and
    storms add to it.
  - **Terrain changes it.** Mountain tops, the sea and open country are
    windier; forests shelter you, and jungles most of all. There is no wind
    underground, indoors, or in the Nether and the End.
- **What wind does.**
  - **Outside feels colder:** "feels like" takes in the real wind.
  - **Rooms leak more:** in a gale a room drifts toward the outdoors up to
    twice as fast, so its system works harder. Its humidity and air follow
    the outdoors faster too.
  - **Dust storms:** a strong wind lifts dust where the ground is dry. A
    desert gale pushes the AQI into Unhealthy.
  - **Smoke thins and pollen spreads** on windy days.
- **`/hvac wind`** shows today's wind, the wind where you stand and the next
  days'. Admins can set today's with `/hvac wind set <calm|breezy|windy|gale>`
  until the day ends, and `auto` lets it blow as it would.
- **A bamboo button is a wind sensor** for sensor hubs.
- **Air in commands:** `/hvac temperature` and `/hvac info` show the air.
  `/hvac info` gives PM2.5, PM10 and pollen in detail. Thermostat signs and
  screens don't: a real thermostat measures no air.
- **HUD tokens and placeholders.**
  - `{aqi}`, `{aqi_band}`, `{pollen}`, `{wind}` and `{wind_level}` work in
    HUD templates, and a stock sidebar gains an air line and a wind line.
    `/hvac temperature` also shows the wind.
  - `%ezvac_aqi%`, `%ezvac_aqi_colored%`, `%ezvac_aqi_band%`, `%ezvac_pm25%`,
    `%ezvac_pm10%`, `%ezvac_pollen%`, `%ezvac_pollen_count%`, `%ezvac_wind%`,
    `%ezvac_wind_level%`, `%ezvac_wind_speed%`, `%ezvac_wind_ms%`,
    `%ezvac_wind_kmh%` and `%ezvac_wind_mph%` work in PlaceholderAPI.
  - Wind speeds show in km/h with °C and mph with °F.
- **`air-quality` and `wind` in config.yml:** turn either off, or tune the
  sources, filters, purifiers, the wind's chances and speeds, and each
  biome's air and exposure to the wind. Your config.yml gains them with
  their comments.
- **API 1.5:**
  - `AirQuality`, on `ClimateReading` and `ThermostatView`;
  - air purifiers in `climateUnits()`, with their filter;
  - air monitors and air sensors in `instruments()`;
  - `Wind`, on `ClimateReading` and `OutdoorClimate`, and
    `windForecast(world, days)`;
  - wind sensors in `instruments()`.
  - Older constructors still work. A `switch` over an EZvac enum needs a
    `default` branch: 1.5 adds new kinds.

### Upgrading from 3.1

Stop the server, replace the jar and start it.
- **The save stays format 8.** Every room's air starts at the outdoor level.
- **Going back to 3.1 is a jar swap.** 3.1 keeps the purifiers, air monitors,
  air sensors and wind sensors it doesn't know, with a warning, and 3.2 brings
  them back.
- **Without wind, the Nether and the End now feel a little warmer:** their
  "feels like" used to take in a light breeze.
- **EZdebug+ users need 0.2.1.** 0.2.0's debug vision fails near an air
  monitor, air sensor or wind sensor. LiveExposure works unchanged, and feels
  the wind through "feels like".

## ALPHA 3.1

**Equipment tiers.** Every heat pump, air conditioner, furnace, boiler and
pool heat pump now has a tier, shown as stars, after the three kinds of real
equipment: ★☆☆ single-stage, ★★☆ two-stage and ★★★ variable-speed. Every
existing unit becomes ★★★ at the full capacity it always had, so heat pumps,
ACs and pool heat pumps run exactly as before.

### Added

- **Tiers.**
  - **Single-stage** runs flat out or not at all, so near its target it
    overshoots, stops and starts again.
  - **Two-stage** drops to a 65% low stage for the last stretch.
  - **Variable-speed** eases down smoothly, to 40%, as every compressor did
    until now. A variable furnace or boiler modulates.
  - **The cheaper kinds are the budget models:** they deliver 70% and 85% of
    a variable unit.

  So the better the tier, the faster a room gets there and the steadier it
  stays. Tiers mix freely in one system.
- **`/hvac create heatpump|ac|furnace|boiler|poolheatpump [t1|t2|t3]`** sets
  the tier (`1`-`3`, `single`, `two-stage`, `variable` and `modulating` work
  too). **New units are two-stage** unless you name one.
- **`/hvac equipment tier <t1|t2|t3>`** changes the tier of the unit you look
  at.
- **Stars everywhere a unit is shown:** `/hvac info`, RPM monitors, and the
  messages when you create or change one.
  - RPM monitors give each kind and tier its own row (`HP ★★★ ×2 2,950`),
    shortened step by step so the stars always fit.
  - `displays.stars.full` and `.empty` change the glyphs, for a resource pack
    or Bedrock players.
- **`performance.tiers` in config.yml** sets the budget tiers' capacity and
  the two-stage low stage. Variable is the `variable-speed` curve, unchanged.
  Your config.yml gains the new settings with their comments.
- **API 1.4:** `EquipmentView.tier()`. The old constructor still works and
  means variable, so addons build and run unchanged.

### Changed

- **Existing furnaces modulate.** As ★★★ units they give the same full heat,
  then ease off near the target instead of overshooting it.
- **Boilers are burners, like furnaces.** They were always documented as the
  furnace's counterpart, but ran on the compressors' variable-speed curve.
  They now burn, staged by their tier like a furnace. An existing boiler
  becomes ★★★ modulating, so pools heat much as they did.
- **The thermostat's meter shows load:** how hard the system is working,
  0-100%, on the sign (`Set 72.0 F | 68%`) and as the screen's bar. It's what
  the system delivers as a share of what its units could deliver flat out in
  its current profile. It used to be raw capacity, which read 141% for two
  units at full speed and up to 200% for more.
- **RPM monitors** show `on` for a running furnace or boiler instead of its
  fixed blower speed.

### Upgrading from 3.0

Stop the server, replace the jar and start it.
- **The save moves to format 8.** Your 3.0 file is kept as
  `hvac.yml.format7.bak`.
- **Every unit becomes ★★★ variable, at full capacity.** 3.0 saved every
  unit as tier 2, which now means two-stage.
- **Compressors run exactly as before.** Furnaces now ease off near the
  target, and boilers burn at full heat, then modulate.
- **New units default to two-stage;** name `t3` for variable.
- **Going back to 3.0** means restoring the format 7 backup: 3.0 refuses a
  format 8 file.

## ALPHA 3.0

**Linked systems.** Group names are gone. A system is now its thermostat
and whatever you link to it with the new link tool, and thermostat names
can repeat, so every house can have a Home. It changes how systems are
built, and nothing about how they run: heating, cooling, airflow and
humidity behave exactly as in 2.2.

> **Upgrading is one way.** The first save after upgrading converts
> `hvac.yml` to a new format (7) and keeps the 2.x file as
> `hvac.yml.format6.bak`. Every old group carries over by itself, with
> nothing to relink. EZvac 2.x will not start on a format 7 file. Going
> back means restoring that backup, which loses everything built or changed
> since the upgrade. Back up `plugins/EZvac` before you upgrade. See
> [Upgrading](docs/upgrading-and-troubleshooting.md).

### Added

- **The link tool** (`/hvac link-tool`) replaces the duct tool and the
  sensor tool.
  - Right-click a thermostat to select it, then right-click equipment, vent
    blocks, a settings panel or RPM monitors to link them to it.
    Sneak-click anything to unlink it.
  - Select a sensor hub instead to link sensors, as before.
  - `/hvac duct-tool`, `/hvac sensor-tool` and `/hvac tool` still give it,
    and tools already in inventories keep working.
  - Creating a device while holding the tool, with a thermostat selected,
    links it straight away.
- **Headless systems.** Breaking a thermostat no longer scatters its
  system: the equipment, vents and signs stay linked together, idle. Link a
  new thermostat to any part of it and the whole system comes along.
- **A system with its own thermostat is never taken.** Linking a device
  that belongs to another thermostat is refused, with where that
  thermostat is.
- **Thermostats follow what is linked to them.** Linking a boiler, a pool
  heat pump or a waxed copper grate makes a thermostat a pool thermostat
  (75-104 °F), and a target still at the room default moves to the pool
  default. `/hvac create poolthermostat` still starts one in the pool range.
- **`/hvac remove system`**, holding the link tool and looking at any part
  of a system, says what it would remove. Run it again with `confirm` to
  unregister every device of the system. The blocks stay.
- **`/hvac rename <label>`** renames the sign you're looking at: a
  thermostat, RPM monitor, settings panel, sensor hub, thermometer, sensor,
  or dehumidifier or humidifier sign.
- **API 1.3:**
  - `thermostatAt(position)` and `thermostatOf(world, system)`;
  - `linkTool()`, so addons can see the link tool and what it has selected.
    EZdebug+ 0.2.0 uses this to show the system being linked.

### Changed

- **RPM monitors show their whole system:** one row per kind of equipment,
  with how many there are and the average speed of those running. Like a
  sensor hub, a click turns the page and a sneak-click switches auto-flip.
  A monitor that watched one kind of equipment now shows all of it.
- **Commands that took a group name no longer do:**
  - `create thermostat <label> [target]`;
  - `create heatpump|ac|furnace|boiler|poolheatpump`;
  - `create rpmmonitor [label]` and `create settingspanel [label]`.

  The old forms still work, with a note that the group name was ignored.
- **`/hvac thermostat set|enable|disable|info` acts on the thermostat you
  look at,** since names can repeat. `/hvac group` and `/hvac equipment
  group` are gone; the link tool does their job.
- **The thermostat sign's last line** counts the units at work (`Ready | 2/3`)
  where the group name used to be.
- **The HUD's "System:" line** shows the label of the thermostat whose air
  you're in, or `Headless`.
  - The new `{system}` token and `%ezvac_system%` placeholder give the same
    value.
  - `{group}` and `%ezvac_group%` still work.
- **`/hvac list`** shows each system as its thermostat and where it is,
  including headless systems.
- **API:** a view's `system()` is now an opaque key: an upgraded system
  keeps its old group name, a new one is `#` and a number. Show players
  `ThermostatView.id()` or `ClimateReading.thermostat()` instead.
  `thermostat(world, id)` is deprecated, since labels can repeat.

### Removed

These no longer exist. Each one says what to use instead when it's tried.

| Removed | Use instead |
| --- | --- |
| Group names | Link devices to a thermostat with the link tool |
| `/hvac group <group> enable\|disable\|info` | Sneak-click the thermostat to switch its system on or off; `/hvac thermostat info` while looking at it |
| `/hvac thermostat <id> set\|enable\|disable\|info` (by name) | The same, while looking at the thermostat: `/hvac thermostat set 70` |
| `/hvac thermostat <id> group` and `/hvac equipment group` | Sneak-click with the link tool to unlink, then link to another thermostat |
| An RPM monitor watching one kind of equipment | A monitor shows all of its system's equipment |
| Unique thermostat ids | Labels may repeat; `/hvac rename` changes one |

### Deprecated

These still work in 3.0, but are on their way out and may be removed in a
later release. The changelog will say so first.

| Deprecated | Use instead |
| --- | --- |
| A group name in `create` commands (`create thermostat main house 72`, `create heatpump house`, `create rpmmonitor house heatpump fan`) | The forms without it. For now the group is ignored, with a note |
| The `{group}` HUD token and `%ezvac_group%` | `{system}` and `%ezvac_system%`, which show the same thermostat label |
| `/hvac duct-tool`, `/hvac sensor-tool`, `/hvac tool`, and the duct and sensor tool items | `/hvac link-tool` and the link tool |
| API: `EzvacApi.thermostat(world, id)` | `thermostatAt(position)` or `thermostatOf(world, system)`; labels can repeat, so it returns the lowest-placed match |

**For addons,** the API's `system()` strings are now opaque keys (see API
1.3 above). They aren't deprecated, but they mean something different: show
players the thermostat's label instead.

### Upgrading from 2.2

- **The save:**
  - `hvac.yml` moves to format 7, and the 2.x file is kept as
    `hvac.yml.format6.bak`;
  - each old group becomes a system with the same devices and the same
    thermostat;
  - a group that had no thermostat comes back headless;
  - equipment is saved with a tier (every unit is tier 2), ready for
    equipment tiers in 3.1.
- **Labels:** every label stays as it was. Old group-style names such as
  `2230radcliffedr2stat` can be tidied with `/hvac rename`.
- **Config:** nothing needs changing. New installs show `{system}` on the
  HUD; existing configs keep `{group}`, which still works.
- **Addons:** LiveExposure and EZdebug+ work unchanged.

## ALPHA 2.2

An API release: addons can now see every EZvac device and where it is.
Nothing changes for players or servers. The save, the config and gameplay
are the same as ALPHA 2.1, so upgrading is a jar swap.
[EZdebug+](https://github.com/EZvac-Plugin/EZdebugPlus), a new official
addon, is built on it.

### Added

- **API 1.2: every device and where it is.** Addons can list
  `equipment()`, `vents()` and `instruments()` (thermometers and hygrometers,
  sensors, sensor hubs, RPM monitors, settings panels), each for the whole
  server or one world, plus `thermostats(world)`, `climateUnits(world)` and
  `devices(world)` for everything in a world. Every view is an `HvacDevice`
  with its position:
  - equipment shows its kind, system, whether it's on and running, its mode
    and motor RPM;
  - a vent shows its system and whether it blows air or water;
  - a hub lists the sensors it shows.

  Sensor readings aren't part of the views.
- **Device events:** `DeviceAddedEvent` and `DeviceRemovedEvent` (broken,
  removed by command, or found missing), so a map addon stays in step
  without re-reading the lists. Like the other events, they cost nothing
  without a listener.

## ALPHA 2.1

**Realism: EXTENDED.** Every biome has its own climate, with smooth
transitions between them; there are seasons on EZvac's own calendar, colder
air up high and steady air underground. Every sign gets a lit screen and a
control menu, temperatures can be shown in °F or °C server wide, and other
plugins can build on EZvac through its new public API. The save format stays
the same as 2.0 (format 6), so upgrading from 2.0 is a drop-in. 2.1 was tried
out in four prereleases (ALPHA-2.1-P1 to P4, notes on GitHub); this entry
covers everything since 2.0.

### Added

- **A climate for every biome.** The seven broad classes (Snowy, Cold,
  Temperate, Warm, Hot, Nether, End) are replaced by each vanilla biome's own
  climate, modelled on real places:
  - clear desert skies let the day heat up and the night cool hard (98 °F
    noon, 64 °F midnight);
  - a jungle (88/74) or the sea (66/60) barely moves;
  - a data pack's biome gets a climate worked out from its Minecraft
    temperature and rainfall;
  - any biome can be given its own under the new `biomes:` section.
- **Biome transitions.** The outdoor climate at a spot is the average of the
  biomes around it, instead of the biome at that one block:
  - 24 blocks by default, nearer ones counting more;
  - walking from plains into a desert at noon ramps up over about 48 blocks
    instead of jumping in a step;
  - temperature, humidity, weather and natural water all blend, and rain at a
    desert's edge cools it partway;
  - it feeds the simulation, so a house on a border lives in the in-between
    climate;
  - only loaded chunks count, and each spot is worked out once and cached.
    Tuning: `biome-blend.radius-blocks` (0 turns it off).
- **Seasons.** Each world with a sun has a year of four seasons, 15 days each
  by default:
  - temperatures follow a smooth curve, warmest in mid-summer and coldest in
    mid-winter, by each biome's own amount: plains noon goes from 90 °F in
    mid-summer to 54 °F in mid-winter, a taiga from 76 °F to 28 °F, and a
    jungle hardly changes;
  - lakes and the sea follow a few days behind;
  - only temperatures change: biomes, rain and snow stay as Minecraft makes
    them. Tuning: `seasons:` (`days-per-season`, `strength`,
    `water-lag-days`).
- **EZvac's own calendar,** per world, in the new `seasons.yml`. It counts the
  world's days (sleeping moves it on), but setting the clock back never sends
  the year back to spring.
- **`/hvac season`** shows the season, its day, and how much it warms or cools
  the outdoors where you stand. Admins can change it at any time with
  `/hvac season set <season> [day]`, `next`, `pause` and `resume`, also from
  the console with a world name at the end.
- **Altitude.** The air is 0.1 °F colder for every block above y=64, about
  14 °F colder at y=200. High on mountains rain turns to snow where Minecraft
  itself snows. Tuning: `altitude:`.
- **Underground.** Deep down, the air holds the ground's steady temperature,
  with no day/night swing, no seasons and no weather. It sets in 4 blocks
  below the surface (or sea level, if lower) and is complete at 20. Tuning:
  `underground:`.
- **Screens.** Every EZvac sign gets a floating screen in front of it, a dark,
  lit panel with room for more than a sign's four lines:
  - a thermostat's shows the room temperature, what it's doing (`❄ Cooling to
    70.0°F`), a capacity bar, the ETA and humidity, and the outdoor
    temperature and season;
  - sensors, units, hubs, RPM monitors and settings panels have their own;
  - all screens are the same size and readable at night;
  - the sign stays behind it, so clicking works as before, and clients
    without screens (such as Bedrock players through Geyser) still read it;
  - screens are never saved with the world, and come back by themselves when
    their chunk loads. On by default; `displays.screens: false` turns them
    off.
- **Control menus.** Right-clicking a thermostat sign, room or pool, opens a
  menu:
  - black/white panes +5 / -5, green/red +1 / -1 and lime/orange +0.1 / -0.1,
    in the server's units, never past the allowed range;
  - a lever for on/off;
  - dehumidifiers and humidifiers get +5% / -5% and a lever;
  - a menu works only within a few blocks of its sign.
- **Fahrenheit or Celsius, server wide.** The new `units: F|C` setting decides
  how every temperature is shown (signs, screens, chat, the HUD, placeholders)
  and typed (`/hvac thermostat <id> set`, the target of
  `/hvac create thermostat`). `72F` or `22C` always works, whatever the
  setting. `config.yml` itself stays in °F.
- **The HUD shows the season,** on a new sidebar line that is left out where
  there are none. It comes with the `{season}` and `{season_day}` tokens and
  the `%ezvac_season%` and `%ezvac_season_day%` placeholders. `/hvac
  temperature` shows it too.
- **A public API for addons (API 1.1).** Addons are ordinary Paper plugins
  that `depend: [EZvac]` and call `EzvacApi.get()`:
  - **reading:** the climate at any spot and around every player
    (temperature, feels like, humidity, the outdoors, weather, season, and
    which system conditions it), thermostats and their systems, dehumidifiers
    and humidifiers, and the seasons. Everything handed out is an immutable
    snapshot, and the API changes nothing;
  - **events:** a player's climate updating (on the HUD's cadence), a system
    changing mode, a target changing, equipment starting or stopping, and a
    season turning. EZvac only builds them while something listens, so
    servers without addons see no difference;
  - **HUD sections:** an addon can add its own lines to the climate HUD,
    drawn with EZvac's own bar and units.
    [LiveExposure](https://github.com/EZvac-Plugin/LiveExposure) uses this to
    show players' body temperature under the season line;
  - **on JitPack:** `com.github.EZvac-Plugin.EZvac:ezvac-api`, built from each
    release's tag. The guide, with a complete example addon, is in
    [docs/api.md](docs/api.md).
- **Addon sections on the HUD, for servers:**
  - a sidebar line that is just `{addons}` shows every addon's section;
    `{section:<id>}` places one on its own, in the sidebar or in the action
    bar and boss bar formats;
  - `hud.sections.hidden` switches sections off;
  - `/hvac hud sections` (admins) lists them;
  - the server's own lines always fit, and a section that would go past the
    sidebar's 15 lines is left out.

### Changed

- **Every outdoor climate changes** after the update:
  - biomes have their own numbers, and builds near a border blend with the
    neighbouring biome;
  - mountains are colder higher up, and deep spots are steady;
  - the seasons begin on the first day of spring the first time the server
    starts with 2.1.

  Rooms drift to the new climate gradually.
- **EZvac signs can no longer be edited.** A thermostat's target is set from
  its menu instead of line 3 of the sign.
- **Everyone with `ezvac.use` can change thermostat targets** from the menu,
  like dehumidifier setpoints. Editing line 3 used to need `ezvac.admin`;
  `/hvac thermostat <id> set` still does.
- **Dehumidifier and humidifier signs open their menu** instead of stepping
  through the presets. `presets` now sets the range the menu reaches, from
  the lowest preset to the highest, in steps of 5.
- Sneak-right-click still switches a thermostat's system, or a unit, off or
  on.
- **The HUD's units:** a config with `hud.units: C` starts with `units: C`, and
  `hud.units` is no longer read.
- **A stock HUD sidebar** gains the season line and the `{addons}` line when
  the update adds the new settings to `config.yml`. A sidebar you changed is
  left as it is: add `"<gray>Season: <white>{season} <gray>day {season_day}"`
  and `"{addons}"` to it yourself.
- **What thermostats save:**
  - their seasonal swing (`outdoorSeasonalF`), once they sample their
    surroundings;
  - on a biome border, their mix of rain and snow (`outdoorRainShare`,
    `outdoorSnowShare`).

  Everything else saves exactly as before.
- **For contributors:** the build is now two Maven modules, `ezvac-api` and
  `ezvac-plugin`. The plugin jar is built to `ezvac-plugin/target/` and
  carries the API's classes.

## ALPHA 2.0

The realism update: weather, humidity and "feels like", dehumidifiers and
humidifiers, remote sensors, readings that vary from spot to spot, and
configurable sounds. It moves `hvac.yml` to **format 6**. Upgrading from any
1.x release is supported and needs no conversion: the first save keeps your
1.x file as `hvac.yml.format5.bak`, and `config.yml` gains the new settings on
startup. Releases 1.7 to 1.9 refuse a format 6 file (they leave it untouched),
so going back means restoring that backup. 2.0 was tried out in five
prereleases (ALPHA-2.0-P1 to P5, notes on GitHub); this entry covers
everything since 1.9.

Thanks to Cole ([@colebolebole](https://github.com/colebolebole)), who ran
every pilot on his server, for his huge help hunting bugs and for the ideas
that shaped 2.0.

### Added

- **Weather.** Rain and thunder cool the outdoor air (by 6 °F and 9 °F by
  default) and narrow the day/night swing; snow cools a little. The change
  eases in over about two in-game hours instead of switching instantly. Only
  biomes with precipitation react (not deserts, savannas, badlands, the Nether
  or the End). The HUD, idle drift, ETAs, thermometers and pools all follow;
  natural water barely reacts. Tuning under `weather:`.
- **Humidity.** Moisture is simulated as a dew point and shown as relative
  humidity. Outdoors it comes from the biome (deserts dry, jungles and swamps
  humid), rises at night, and climbs toward 100% in rain. Indoors each air
  system's room drifts toward the outdoor moisture, cooling dries it, heating
  lowers its RH, and water inside a room evaporates into it, faster when it is
  warm. Tuning under `humidity:`.
- **Feels like.** The apparent temperature from temperature, humidity and
  outdoor wind, on the HUD, in `/hvac temperature` and `/hvac info`.
- **Dehumidifiers.** An iron block on a waxed copper grate with a sign,
  registered with `/hvac create dehumidifier [label]`. It needs no system: it
  dries the room of the system around it, or its own zone anywhere else (a
  shed, a basement), 30 blocks of airflow by default. Click the sign to step
  the setpoint (35-60%), sneak-click to switch it on or off. The water it
  removes fills a tank; a full tank waterlogs the grate while it empties. In
  air already drier than its coil can dry (a dew point at or below 35 °F, as
  in snowy biomes) its sign shows `Air too cold`. Tuning under
  `dehumidifier:`.
- **Humidifiers.** An iron block with a waxed cut copper slab (bottom half) on
  top and a sign on the iron block, registered with
  `/hvac create humidifier [label]`. It works like a dehumidifier the other
  way round (setpoints 30-50%), is plumbed so it never runs dry, and its slab
  fills with water and bubbles while it runs. Tuning under `humidifier:`.
- **Units share a space.** Dehumidifiers and humidifiers outside any system
  whose zones share air have one humidity and work on it together; inside a
  system's room they share the room's.
- **Hygrometers and thermo-hygrometers.** `/hvac create hygrometer [label]`
  and `/hvac create thermohygrometer [label]` register sign sensors that work
  like thermometers: humidity inside and outside, or temperature, humidity and
  feels-like together.
- **Sensors and sensor hubs.** A button is a sensor, and the button decides
  what it measures: stone (temperature), polished blackstone (humidity),
  mangrove (both) or birch (weather, which needs open sky and shows `Covered`
  under a roof). Register one with `/hvac create sensor [label]`, a sign with
  `/hvac create sensor-hub [label]`, and link them with `/hvac sensor-tool`. A
  hub shows up to nine sensors, three to a page: click to turn the page,
  sneak-click for auto-flip every 5 seconds. Pressing a sensor shows its
  reading in chat and never sends redstone.
- **Spot-to-spot variance.** Readings differ a little from place to place
  instead of one value for a whole house: up to ±0.5 °F in a conditioned room,
  ±1.5 °F outdoors, ±0.3 °F in pools and ±0.8 °F in natural water, with
  humidity following. It changes gradually over a few blocks and drifts over
  in-game hours. Readings only: thermostats and the simulation keep the room's
  value. Tuning under `variance:`.
- **Configurable sounds.** Every sound EZvac plays is a `"sound volume pitch"`
  line under `audio.sounds`; `[]` silences an event, and resource-pack sounds
  work. The defaults are the sounds 1.9 played, plus a new **vent airflow**
  sound: air vents of running systems near a player softly play the conduit's
  hum, one vent per cluster. Pools and water vents stay silent.
- **Thermostat power switch.** Sneak-right-click a thermostat sign to switch
  its system off, and again to switch it back on, like
  `/hvac thermostat <id> disable|enable` (needs `ezvac.use`).
- **config.yml keeps up.** Settings added since a server's `config.yml` was
  written are added to it on startup, with their comments. Existing settings
  are never changed; the previous file is kept as
  `config.yml.before-<version>.bak`. A value out of range in one of the new
  sections doesn't stop EZvac: that section uses its defaults and the log
  names the setting.
- Placeholders `%ezvac_feels_like%` (`_f`, `_c`), `%ezvac_humidity%` and
  `%ezvac_weather%`; HUD tokens `{feels}`, `{humidity}` and `{weather}`.

### Changed

- **The HUD's comfort word, bar and boss-bar colour follow "feels like"** in
  air, so a humid 84 °F room reads Hot and a windy storm reads colder. Water is
  still judged by its temperature. The default HUD gains `Feels like` and
  `Humidity` lines and shows the weather next to `Outside`; configs that set
  their own `hud.sidebar.lines` keep them.
- **Signs are only rewritten near players** (within about 64 blocks). Farther
  signs keep their last text and catch up within a second of someone coming
  near; the simulation, saving and readings are unaffected. At 1,000 running
  rooms this takes EZvac from about 5.5 ms to about 2 ms per tick.
- **Sign lines always fit.** Lines are measured in pixels as the client draws
  them (narrower on hanging signs), headers drop their `EZvac | ` prefix when
  the label needs the room, and a reading is never shown cut.
- **The duct tool is now `/hvac duct-tool`**, next to `/hvac sensor-tool`.
  `/hvac tool` still works, and duct tools already handed out keep working.
- `audio.hum-volume` and `audio.hum-pitch` are replaced by `audio.sounds`. A
  hum tuned with them carries over when `config.yml` is updated; afterwards
  they are no longer read.
- `hvac.yml` format 6: thermostats store their room's moisture and their
  biome's downfall and precipitation, with new `dehumidifiers`,
  `humidifiers`, `sensors` and `sensorHubs` sections.

## ALPHA 1.9

Pools get a heat pump of their own, and water now reads like water. No
save-file changes (still format 5), so this is a drop-in upgrade.

### Added

- **Pool heat pump.** `/hvac create poolheatpump <group>` registers a
  water-loop heat pump: variable speed, ECO/NORMAL/TURBO profiles, and it heats
  *and* cools, so a pool can now be cooled as well as heated. It uses the pool
  target range, links only to waxed copper grates and pool thermostats, and can
  share a system with a boiler (the boiler adds steady heat, the heat pump
  modulates). RPM monitors accept `poolheatpump`.
- **Water-aware HUD.** In water the HUD switches to its own layout (`Water`,
  `Air`, `Pool`, `Circulation`) and its own comfort scale: 78-86 F is a
  comfortable swim and above 95 F is a hot tub, where the room scale would call
  84 F warm. It reads at your eyes when submerged and at your feet otherwise,
  so surface swimming and wading count. Configurable under `hud.water`; new
  placeholder `%ezvac_medium%` (`air` or `water`).
- **Natural water temperature.** Water no pool system reaches - lakes, the sea,
  an unheated pool - reads the biome's natural water temperature instead of the
  air's: the biome's daily mean pulled toward mild values, steady through the
  day, and never below 33 F.

### Changed

- **Pools drift toward natural water, not the air.** An idle pool on a pool
  thermostat now cools (or warms) toward its biome's natural water temperature,
  and readings at a pool's edge blend toward it, instead of toward the outdoor
  air. A heated desert pool left alone cools toward about 84 F rather than
  staying at the 100 F noon air. Pools still on a room thermostat (from
  ALPHA 1.6 Pilot 1) keep the air behaviour.
- `/hvac temperature` reports water and air separately when you are in water.

### Fixed

- **Carpets let air through.** A carpet has a sliver of collision, so EZvac
  treated it as solid: a carpeted floor left a hole in the airflow exactly where
  players stand, and the HUD showed the outdoor temperature at your feet while
  your head was in the conditioned room. Wool and moss carpets now pass air.
- **Player readings use head height.** Standing on a bottom slab, stairs, or a
  snow layer had the same effect, because your feet are inside that block. The
  HUD, placeholders and `/hvac temperature` now read at head height in air and
  fall back to your feet.
- Messages that named only boilers now name all water-loop equipment.

## ALPHA 1.8

The climate where you stand, always on screen. No changes to the save file
(still format 5), so this is a drop-in upgrade.

### Added

- **Climate HUD.** `/hvac hud [on|off]` shows the local temperature, a
  cold-to-hot bar with a comfort label, the outdoor temperature, and the
  system conditioning that spot with its mode, target and core/leakage zone.
  Off by default for each player; each player's choice is remembered.
- **Three styles:** a Hypixel-style scoreboard sidebar (default, with the score
  numbers hidden), the action bar, or a boss bar whose fill and colour follow
  the temperature. Set with `hud.style`.
- **Never steals a scoreboard.** The sidebar attaches only while nothing else
  uses the player's sidebar, and steps back if another plugin takes over.
  Packet-based scoreboard plugins such as TAB cannot be seen through the
  server's scoreboard API, so when one is installed the HUD uses the action
  bar instead (`hud.sidebar.yield-to-plugins`).
- **PlaceholderAPI placeholders** (optional): `%ezvac_temp%`, `%ezvac_bar%`,
  `%ezvac_comfort%`, `%ezvac_group%`, `%ezvac_mode%`, `%ezvac_zone%` and more,
  for scoreboard, TAB and chat plugins. Without PlaceholderAPI EZvac logs one
  line saying what installing it would add.
- **Configurable** text (MiniMessage), colours, bar glyphs, length and range,
  comfort bands, and °F or °C under `hud:` in `config.yml`. Configs from
  earlier releases load unchanged and use the defaults.
- New permission `ezvac.hud` (default: everyone).

### Performance

- Values come from one snapshot per player, refreshed on the main thread every
  half second. Placeholder requests, which HUD plugins often make from other
  threads and many times a second, only read that snapshot.
- Temperature lookups now check only systems with vents near the spot instead
  of every system in the world, using the vent grid from 1.7. This also speeds
  up thermometer signs and `/hvac temperature`. A test compares it against the
  old full scan, ties included.

## ALPHA 1.7 HOTFIX 1

A data-safety fix, found by stress-testing 1.7. No other changes; the save
file is unchanged (format 5), so this is a drop-in upgrade.

### Fixed

- **An unreadable save file could be replaced by an empty one.** If
  `hvac.yml` could not be parsed (for example after a typo while hand-editing
  it) and there was no readable `hvac.yml.bak`, EZvac correctly refused to
  start, but the shutdown that followed still saved. With nothing loaded, that
  save wrote an empty file over `hvac.yml` and moved the original into
  `hvac.yml.bak`. The next start then loaded the empty file without complaint,
  and its first save rotated the original out of `hvac.yml.bak` as well, so
  every device was lost after two restarts. This affected every release since
  ALPHA 1.0.

  Any failed load now leaves EZvac read-only for that run, the same way 1.7
  already treated a save file from a newer version. Nothing is written,
  rotated or backed up until the file loads, and the log says so instead of
  claiming state was saved. The error message says the file was left
  untouched and what to do next.

- **Recovering from the backup no longer discards the unreadable file.** When
  `hvac.yml` is unreadable but `hvac.yml.bak` is fine, EZvac still loads the
  backup, but it now first keeps the unreadable file as
  `hvac.yml.unreadable-<timestamp>`, because it may hold changes newer than the
  backup, or hand edits worth repairing.

Verified on Purpur 1.21.1: a broken `hvac.yml` with no backup now survives two
restarts byte-for-byte, where 1.7 had replaced it with an empty file.

## ALPHA 1.7

A foundation release. No new devices, commands, or configuration options, and
the save file is unchanged (format 5), so this is a drop-in upgrade from any
earlier release.

### Performance

EZvac used to answer questions like "which thermostat controls this group?"
or "which unit owns this block?" by scanning and sorting every device on the
server, and the controller asked them once per unit per cycle, so the cost grew
with units × thermostats. Both earlier watchdog stalls (snow in 1.1, FAWE edits
in 1.6) came from scans of this kind. The registry now keeps lookup tables by
group, by thermostat id, by location (the vent grid from 1.6 HOTFIX 1, moved
into the registry) and by owned output, so those answers are direct lookups.
Releasing output ownership on a block change, the last per-event scan over all
equipment, is now a single lookup.

Measured on Purpur 1.21.1 build 2329, idle server with 200 thermostats, 400
equipment units and 2,008 vents:

| | 1.6 HOTFIX 1 | 1.7 |
| --- | ---: | ---: |
| Average tick | 0.6-0.7 ms | 0.3 ms |
| Worst tick (the once-per-second controller cycle) | 8.5-10.1 ms | 2.2-2.4 ms |

Large FAWE edits, mass leaf decay and falling sand perform the same as 1.6
HOTFIX 1, which already handled them.

### Changed

- **Upgrade-only save files.** Upgrading from any earlier release is
  supported; downgrading is not. A save file written by a newer EZvac (a higher
  `meta.format`) is now refused: EZvac logs why, does not start, and leaves the
  file exactly as it was. Previously it loaded the file with a warning and then
  saved it back in its own format, which could silently discard whatever the
  newer version had stored. When a future release changes the format, it backs
  up the original first as `hvac.yml.format<N>.bak`.

  Nothing about the save file itself changes in this release: it is still
  format 5, and every earlier release's files load unchanged.

### Testing

- **Upgrade compatibility suite.** Real save files from every release since
  ALPHA 1.0 are part of the test suite, each produced by that release on a
  Purpur 1.21.1 server with one of every device it supports, both after a clean
  shutdown and mid-run (as a crash would leave them). The tests check that every
  device loads and that saving produces exactly what 1.6 HOTFIX 1 wrote.
- The registry rules other code depends on, such as which duplicate wins and
  what gets preserved, are now pinned by tests that run without a server.
- The new lookup tables are checked by 10,000 random registry changes, each
  followed by a comparison against a from-scratch rebuild and against the old
  full-scan answers.
- Verified live on Purpur 1.21.1: a world built on each release from 1.0 to
  1.6 HOTFIX 1, upgraded to 1.7 after both a clean shutdown and a simulated
  crash, came back with every device and every unit running again, with no
  warnings. A format-6 save file was refused and left byte-for-byte unchanged.

## ALPHA 1.6 HOTFIX 1

A performance fix. No new devices, commands, or configuration options, and
`hvac.yml` is unchanged (format 5), so this is a drop-in upgrade over 1.6.

### Fixed

- **Server stall after large WorldEdit/FAWE edits.** A `//set grass_block` over
  a large area drove the server into the hundreds of milliseconds per tick and
  then into watchdog thread dumps. FAWE itself was not the cause: once the
  edit finished, every buried grass block decayed to dirt on random ticks,
  firing a block-fade event per block, and EZvac answered each one by scanning
  every vent on the server. Thousands of events per tick against thousands of
  vents stalled the main thread. A high `randomTickSpeed` multiplies the number
  of events and makes it considerably worse, but the stall happened at the
  default too.

  Vents are now indexed on a coarse grid at least as wide as the airflow range,
  so a block change only looks at vents in the few grid cells around it. Chunk
  load and unload invalidation uses the same index. Natural fade and form
  changes between two full solid blocks, such as grass decaying to dirt or
  concrete powder setting, cannot open or close an airflow path or touch an
  owned water or lava output, so they are now ignored before any lookup.

- **Spurious rebuilds of distant vent systems.** The vent distance check used
  32-bit arithmetic, which overflowed for vents more than about 46,000 blocks
  from a block change, so those systems were sometimes treated as in range and
  rebuilt for no reason. The grid lookup only ever compares nearby vents.

Thanks to [@colebolebole](https://github.com/colebolebole), who spotted the bug:
he noticed the stall after large FAWE edits on his server "Soms", worked out
what triggered it, and sent the log that pinned it down.

### Performance

Measured on Purpur 1.21.1 build 2329 with FastAsyncWorldEdit 2.15.4 and 2,008
registered vents, after `//set grass_block` over a 128 x 40 x 128 region:

| Scenario | 1.6 | 1.6 HOTFIX 1 |
| --- | ---: | ---: |
| `randomTickSpeed` 3, average tick while grass decays | 20-35 ms | 2-4 ms |
| `randomTickSpeed` 100, average tick while grass decays | up to 313 ms | about 3 ms |
| `randomTickSpeed` 100, worst single tick | 1,086 ms | 159 ms |

The remaining worst tick with the hotfix is the FAWE operation itself. Airflow
behaviour is unchanged: breaking a block next to a vent still triggers exactly
one rebuild of that system, and breaking one out of range triggers none.

Grass was not the only trigger. Any natural block change EZvac listens to went
through the same scan, so other mass changes were affected too. These were
measured on the same server with 2,008 vents and 400 equipment units, against
a run with no EZvac installed so the game's own cost is visible:

| Scenario | No EZvac | 1.6 | 1.6 HOTFIX 1 |
| --- | ---: | ---: | ---: |
| Leaves decay, 128 x 40 x 128 with no logs, `randomTickSpeed` 3, average tick | 19-36 ms | 48-52 ms | 19-40 ms |
| Leaves decay, `randomTickSpeed` 100, peak 5 s average | 131 ms | 479 ms | 149 ms |
| Leaves decay, `randomTickSpeed` 100, worst single tick | 564 ms | 1,964 ms | 667 ms |
| 65,536 falling sand blocks, worst single tick | 616 ms | 1,550 ms | 641 ms |
| Snowfall over 320 x 320 in a thunderstorm, average tick | 2-3 ms | 2-6 ms | 2-4 ms |

With the hotfix, leaves decay and falling sand cost about what the game itself
spends on them. Snowfall was never a problem: snow forms slowly enough that it
does not produce a flood of events.

## ALPHA 1.6

Water systems, promoted from ALPHA 1.6 Pilot 1 and including everything that
was in it. The pilot ran on real servers for a week; the pool thermostat below
is the change that came out of it.

### Added

- **Pool thermostats.** `/hvac create poolthermostat <id> <group>` registers a
  sign that behaves exactly like a thermostat but clamps to the water target
  range — 75-104 F by default, against 60-85 F for rooms. Water that would make
  a pleasant room is cold to swim in, and a hot tub would be unliveable as a
  room, so neither range contains the other.

  The range is a property of the thermostat rather than something inferred from
  whatever the system is wired to, which means it is known the moment the sign
  is placed, before any equipment or vent exists.

  A pool thermostat may only control a boiler system, and a room thermostat may
  only control air-loop equipment. Without that restriction a pool thermostat
  pointed at heat pumps would be a way to heat a room to 104 F. The rule is
  enforced when registering either the thermostat or the equipment, matching how
  sources and vents already validate against each other.

  Existing pilot pools keep their room thermostat and its 60-85 F range; break
  and recreate the sign as a pool thermostat to widen it.

  Thanks to [@colebolebole](https://github.com/colebolebole), who proposed pool
  heating in the first place and then put the pilot through extensive testing on
  his server "Soms". That testing is what surfaced this: the air range had simply
  been assumed to fit water, and running the pilot in earnest showed it does not.
  Water systems exist because of that suggestion, and pool thermostats because of
  that testing.

## ALPHA 1.6 Pilot 1

Superseded by ALPHA 1.6. Published provisionally so water systems could be
tried on real servers before the design was fixed, which is exactly what
happened: pool thermostats exist because the pilot showed the air target range
does not fit water.

**You cannot roll back to ALPHA 1.5 once you build a water system.** 1.5 shipped
before the forward-compatibility work below, so it reinterprets a boiler as a
heat pump - which also cools - and deletes every waxed copper grate vent. From
1.6 onward a downgrade degrades gracefully instead. If you need to fall back,
fall back to this pilot, not to 1.5.

### Added

- **Boilers and water systems.** A system now conditions either air or water,
  decided by the vent blocks it uses. Iron trapdoors remain air vents; waxed
  copper grates placed in a body of water are water vents. `BOILER` is the
  water-loop heat source — heating only, water output, the counterpart of the
  furnace.

  A water body behaves exactly like a room: same thermostat, same hysteresis
  and timers, same capacity from multiple units, same distance falloff, same
  ETA. More equipment heats faster; more vents cover more of the body. The only
  difference is what the airflow walk may travel through.

- **Breaking a registered device now says so.** The removal names the device
  and the system it left, and removing a system's last equipment warns that its
  thermostat will stay idle until equipment returns. Nothing is deleted — the
  thermostat and every linked vent survive, so replacing the equipment restores
  the system. Thermometers are silent, having no system to lose, as are
  removals with no player behind them.

  A system must be all air or all water, and sources belong to a loop too:
  boilers are the water loop, while heat pumps, air conditioners, and furnaces
  are the air loop. Mixing either is refused when registering equipment or
  linking a vent, so a boiler never affects room climate and a heat pump never
  conditions water.

### Changed

- Records written by a newer EZvac are preserved rather than dropped or
  reinterpreted. `save()` rebuilds `hvac.yml` from scratch, so a section this
  build did not read was previously deleted the first time it saved — meaning
  anyone who tried a newer build and rolled back lost those devices silently.
  Unrecognised sections are now kept verbatim, an unrecognised equipment type
  is preserved instead of defaulting to a heat pump (which changed what the
  device did), and `meta.format` is read and warned about instead of ignored.

- Continuous integration builds and tests every push and pull request, and
  attaches the built jar to each run.

## ALPHA 1.5

A performance overhaul. No new devices, commands, or configuration options —
every change is to *when* and *how often* work happens, not to what the
simulation does.

### Performance

Measured on Paper 1.21.1 against ALPHA 1.1 HOTFIX, same worlds and same
workloads:

| Scenario | 1.1 HOTFIX | 1.5 | Change |
| --- | ---: | ---: | ---: |
| 555 devices / 150 groups, average tick work | 1.552 ms | 0.515 ms | −67% |
| 555 devices, worst single tick | 118 ms | 64 ms | −45% |
| 5 devices with continuous airflow churn | 0.863 ms | 0.575 ms | −33% |

At 555 devices the controller previously spent roughly 23 ms inside a single
tick once per second. That is now roughly 2 ms.

Three discrete stalls were removed:

- **Registry saves** blocked the main thread for 12 ms (100 devices) to 65 ms
  (2000 devices) every save period. The snapshot is still built on the main
  thread — about 1 ms — and the YAML dump and file replacement now run off it.
- **Airflow rebuild completion** copied the whole distance map, costing up to
  42 ms in one tick at the configured cell cap. The map is now handed over.
- **Chunk streaming** drove the controller at up to 20 Hz instead of its
  configured period, multiplying every per-cycle cost while players explored.

Other improvements:

- Sign existence checks use a material tag instead of taking a full sign
  BlockState snapshot. At high device counts this was the single largest
  controller cost — roughly 1300 tile-entity snapshots per cycle.
- Airflow distance maps use a primitive long-keyed map: about 2.3x less memory
  per vent system and about half the insert cost.
- The airflow walk answers plain air without allocating block data, and caches
  the current chunk across the breadth-first frontier.
- Biome classification is memoised instead of re-matching strings per
  thermostat per cycle.
- The thermostat ETA forecast, which simulates up to 1800 thermal steps, is
  refreshed on the display cadence rather than every controller cycle, and no
  longer boxes a value per unit per step. Mode changes still recompute it
  immediately.

### Changed behaviour

- **Airflow rebuilds are debounced.** A vent system now waits 20 ticks after its
  last invalidation before rebuilding, with a 200-tick ceiling so a system that
  is changed constantly still refreshes. An in-flight rebuild is no longer
  discarded when the system is invalidated again. Previously a door on a
  redstone clock could consume the entire node budget every tick and never
  produce a usable airflow map at all.
- **Chunk-load reconciliation is deferred** by up to one controller period
  instead of running immediately. The controller re-derives device state from
  the world, so the only effect is that a newly loaded machine reconciles on
  the next cycle.

### Unchanged on purpose

- An open barrel still counts as an airflow path. Bukkit models a barrel lid as
  openable, and 1.1 treated it as passable; that behaviour is preserved rather
  than silently changed inside a performance release.

### License

EZvac moves from The Unlicense to the **MIT License**, effective with this
release. Copyright (c) 2026 bladestech and EZvac contributors.

Releases through ALPHA 1.1 HOTFIX remain dedicated to the public domain; that
dedication cannot be revoked and those versions stay usable on those terms.
MIT adds only an attribution and license-notice requirement, and applies to
ALPHA 1.5 onward. Contributions are accepted inbound under the same terms.

### Compatibility

Drop-in over ALPHA 1.1 HOTFIX. Verified on a real server for both a clean
restart and a crash restart (server killed with machines running and fluid
outputs owned) — devices, ownership, and outputs all survived intact.

- `hvac.yml` format version is unchanged (5), and the file EZvac writes is
  byte-identical in structure to what 1.1 produced.
- `config.yml` and all tuning options are unchanged.
- Every class governing persistence, thermal simulation, and equipment
  actuation is byte-identical to 1.1.
- Rolling back to 1.1 works; the state file stays readable by it.
- No new bundled dependencies. The primitive-collection library used by the
  airflow cache is supplied by the server itself.
- Verified on Paper 1.21.1 and on Purpur 1.21.1 (build 2329) with an identical
  555-device registry: same airflow result (109,170 cells) and no errors on
  either.

## ALPHA 1.1 HOTFIX

- Performance fixes for server watchdog stability.

## ALPHA 1.0

- Initial release: sign thermostats, variable-speed heat pumps and air
  conditioners, furnaces, ducted airflow, thermometers, RPM monitors,
  operating-profile panels, outdoor climate, and persistent room temperatures.
