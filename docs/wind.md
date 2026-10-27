# Wind

Since 3.2, every day has its own wind. It makes the outdoors feel colder,
makes buildings leakier, and moves the outdoor air around: dust storms in the
desert, thinner smoke, more pollen.

## Each day's wind

At dawn each day draws a level:

| Level | Chance | Mean speed | About |
| --- | ---: | --- | --- |
| Calm | 40% | 0-2 m/s | up to 7 km/h, 4 mph |
| Breezy | 35% | 3-6 m/s | 11-22 km/h, 7-13 mph |
| Windy | 18% | 7-12 m/s | 25-43 km/h, 16-27 mph |
| Gale | 7% | 14-20 m/s | 50-72 km/h, 31-45 mph |

- **It comes from the world seed and the day.** Everyone on the server gets
  the same wind, it's the same after a restart, and nothing is saved.
- **The day is the season calendar's.** Jumping the season with
  `/hvac season set` or `next` brings that day's wind at once, and setting
  the clock back with `/time set` never replays an old day's wind. With
  seasons off, or the calendar paused, the world's own day count is used
  instead, so the wind still changes every day.
- **The seasons shift the odds.** Spring and autumn have 30% more Windy and
  Gale days, summer 30% fewer, and Calm days make up the difference. With
  seasons off, the chances are as in the table.
- **It gusts** a quarter above and below the day's mean, rising and falling
  smoothly through the day.
- **Over the first two hours after dawn** it eases over from the day before,
  so a gale doesn't start at the stroke of six.
- **Rain and thunder add to it:** 1.5 m/s in rain and 3.5 m/s in thunder, by
  default.

## Where it blows

The speeds above are for open, level ground. Terrain changes them:

| Where | Wind |
| --- | --- |
| Mountain peaks, snowy slopes, windswept hills | 1.4x |
| Oceans, beaches, shores, mushroom fields | 1.3x |
| Plains, savanna, desert, badlands, snowy plains, meadows | 1.15x |
| Forests, taiga, flower forests, cherry groves | 0.75x |
| Swamps | 0.7x |
| Dark forests, jungles, old-growth forests | 0.5x |
| Underground, the Nether, the End | none |

- **It gets windier with height:** a third more 40 blocks above sea level,
  and half as much again from 60 blocks up. A mountain top tops out at 1.8x.
- **Biome borders blend,** like temperature.
- **Walls and roofs shelter you.** The wind you feel depends on how much sky
  reaches you. You feel all of it in the open, a little through a doorway,
  and none inside a closed room, with or without a system.

## What it does

- **Outside feels colder.** "Feels like" takes in the wind: a gale on a 50 °F
  day feels below freezing. See [Climate](climate.md#feels-like).
- **Rooms leak more.** Wind pushes outdoor air through every gap, so a room
  drifts toward the outdoor temperature faster and its system has to work
  harder. A room's humidity and air follow the outdoors faster too. In a
  Gale a room is up to twice as leaky, Windy about one and a half times.

  > A house heated to 70 °F with -5 °F outside loses about 1.25 °F an hour
  > with its furnace off on a calm day. In a gale it loses 2.5 °F, and the
  > furnace runs longer to keep up.

- **Dust storms.** A wind over 6 m/s lifts dust where the ground is dry,
  more the stronger it blows. A windy day in the desert stays Moderate; a
  desert gale is Unhealthy, from the dust. Wet biomes give up little dust,
  and rain lays it.
- **Smoke thins.** Outdoors the wind carries fine particles away, up to 40%
  of them.
- **Pollen spreads.** On windy spring days the pollen count rises, up to
  1.8x. In winter there is none to spread.

See [Air quality](air-quality.md) for the air itself.

## Seeing the wind

- **`/hvac wind`** shows today's level and speed, the wind where you stand
  and out in the open, and the next two days' levels.
- **`/hvac temperature`** adds the wind you feel: `Wind: Windy 22 km/h`.
- **The HUD:** `{wind}` shows `Calm` or `Windy 22 km/h`, and `{wind_level}`
  just the word. A stock sidebar gains a wind line under the outdoor line.
  See [HUD and placeholders](hud-and-placeholders.md).
- **A wind sensor** is a lightning rod: look at it and run
  `/hvac create sensor [label]`. On a sensor hub it reads `Roof Gale 61km/h`.
  It reads the wind at its own spot, so put it up high in the open: walls,
  roofs and trees shelter it. Remember that a lightning rod also draws
  lightning. See [Sensors](sensors.md).

Speeds show in km/h with `units: C` and mph with `units: F`; `wind.units`
can pick m/s, km/h or mph instead.

## For admins

- **`/hvac wind set <calm|breezy|windy|gale>`** sets today's wind until the
  day ends, or until `/hvac season` moves the calendar; `/hvac wind set auto`
  lets it blow as it would. Handy for
  testing a build in a gale. It isn't saved.
- **Tune it** under `wind` in config.yml: the chances, each level's speeds,
  the seasons, the gusts, how much leakier wind makes rooms, the dust, smoke
  and pollen, and each biome's exposure. See
  [Configuration](configuration.md#wind).
- **`wind.enabled: false`** goes back to 3.1's fixed light wind
  (`weather.wind-clear-ms`), and wind changes nothing else.
