# Climate

How EZvac works out the temperature and humidity of the outdoors, of rooms and
of water. You don't need any of this to build a system, but it explains the
numbers you see.

## Outdoor temperature

The outdoors follows the time of day, the biome, the height and the season.
Every biome has its own climate, modelled on real places (2.1): clear desert
skies let the day heat and the night cool hard, while a jungle or the sea
barely moves. These are year-round averages at sea level, with the seasons
shown alongside:

| Biome | Noon | Midnight | Seasons ± | Mid-summer noon | Mid-winter noon |
| --- | ---: | ---: | ---: | ---: | ---: |
| Desert | 98 °F | 64 °F | 14 °F | 112 °F | 84 °F |
| Badlands | 92 °F | 60 °F | 18 °F | 110 °F | 74 °F |
| Savanna | 92 °F | 70 °F | 4 °F | 96 °F | 88 °F |
| Jungle | 88 °F | 74 °F | 2 °F | 90 °F | 86 °F |
| Swamp | 80 °F | 60 °F | 14 °F | 94 °F | 66 °F |
| Plains | 72 °F | 50 °F | 18 °F | 90 °F | 54 °F |
| Forest | 68 °F | 48 °F | 18 °F | 86 °F | 50 °F |
| Ocean | 66 °F | 60 °F | 10 °F | 76 °F | 56 °F |
| Taiga | 52 °F | 32 °F | 24 °F | 76 °F | 28 °F |
| Snowy plains | 28 °F | 8 °F | 26 °F | 54 °F | 2 °F |
| Caves (lush, dripstone, deep dark) | 64, 58, 52 °F | same | none | | |
| Nether | 96-120 °F | same | none | | |
| End | 30 °F | 30 °F | none | | |

Mountain biomes are given at sea level too; the height cools them where they
stand (see [Altitude](#altitude)), so jagged peaks at y=220 come out around
18 °F at noon and 0 °F at midnight. A data pack's biome gets a climate worked
out from what Minecraft says about it (its temperature and rainfall), and any
biome can be given its own under `biomes:` in `config.yml`.

A room with nothing running drifts toward the outdoor temperature, so a house
in a desert warms up on its own and one in the snow cools down.

## Altitude

The air gets colder going up: 0.1 °F for every block above y=64, so about
6 °F colder at y=128 and 14 °F colder at y=200. Real air cools far less per
metre, but Minecraft's mountains are short, so the effect is stronger to be
felt. Mountain lakes are colder too. High on a mountain, rain turns to snow
where Minecraft itself snows.

## Underground

Deep underground the air holds the ground's steady temperature, as in a real
mine: no day/night swing, no seasons and no weather, just the daily average of
the climate above. It sets in 4 blocks below the surface and is complete at
20. Depth is measured from the surface or from sea level, whichever is lower,
so a hill or a tall house never counts as underground. A basement is partly
tempered. Caves inside a mountain, above sea level, keep the outdoor swing.

## Seasons

Each world with a sun has a year of four seasons, 15 Minecraft days each by
default (a 60-day year, about 20 hours of play). Temperatures follow a smooth
curve: coldest in mid-winter, warmest in mid-summer, and average in
mid-spring and mid-autumn.

- Seasons only move temperatures. Biomes, rain and snow stay as Minecraft makes
  them, so a savanna never snows.
- How far a biome moves is its own (the *Seasons ±* column): strong in the cold
  north, weak in the tropics. `seasons.strength` scales them all, such as 0.5
  for mild seasons.
- Lakes and the sea follow a few days behind the air and move a little less.
- EZvac keeps its own calendar for each world, in `seasons.yml`. It counts the
  world's days, so sleeping through the night moves it on, but setting the
  clock back (`/time set day`) never sends the year back to spring. With
  `doDaylightCycle` off, the season stands still.
- The first time a world runs 2.1 Pilot 2, its calendar starts on the first
  day of spring.
- `/hvac season` shows the season and how much it warms or cools outside
  here; admins can change it any time (see [Commands](commands.md#seasons)).
- The HUD shows the season, and `%ezvac_season%` and `%ezvac_season_day%`
  give it to other plugins.

## Biome transitions

Walking from one biome into another doesn't change the climate in a single
step. The outdoors at a spot is the average of the biomes around it, within
24 blocks by default, with nearer ones counting more, the way Minecraft's
*Biome Blend* setting fades grass colours.

- Deep inside a biome nothing changes: every biome around is the same one.
- Near a border the climate ramps over about 48 blocks. Walking from plains
  into a desert at noon in spring goes from 72 °F to 98 °F over about 11
  seconds instead of in one step.
- Everything that comes from the biome blends: temperature, humidity, weather
  and natural water. At a desert's edge, rain cools the air partway.
- It is the real outdoors, not just a reading: a house built on a border lives
  in the in-between climate, and its thermostat, idle drift and ETA follow it.
- Weather sensors still report the real sky above them.
- Only loaded chunks count, so blending never loads the world around you.

`biome-blend.radius-blocks` sets the width; 0 turns blending off.

## Weather

- Rain and thunder cool the outdoor air, by 6 °F and 9 °F by default, and
  narrow the day/night swing; snow cools by 3 °F.
- Vanilla weather switches in an instant, so the effect eases in and out over
  about two in-game hours.
- Only biomes that get rain or snow react. Deserts, savannas, badlands, the
  Nether and the End never do; snowy biomes, and high mountains, get snow.
- Underground, weather doesn't reach.
- Everything that uses the outdoor temperature follows: rooms drifting with
  nothing running, ETAs, thermometers, sensors, the HUD and pools. Natural water
  barely reacts (10% of the air's change).
- Wind (stronger in rain and thunder) makes the outdoors feel colder.

## Humidity

Moisture is simulated as a **dew point** and shown as **relative humidity**
(RH), the way real thermostats do it.

- **Outdoors** it comes from the biome: about 30% in a desert, 54% on plains
  and 84% in a jungle or swamp at the daily mean. It rises at night as the air
  cools, and climbs toward 100% in rain.
- **Indoors**, each air system's room drifts toward the outdoor moisture.
  Cooling dries the air, heating lowers the RH (dry winter air), and water in a
  room evaporates into it, faster when the water is warm: a heated pool hall
  gets muggy.
- **Dehumidifiers and humidifiers** change it on purpose; see
  [Dehumidifiers and humidifiers](dehumidifiers-and-humidifiers.md).
- RH never goes above 100%: anything more condenses out.

## Feels like

"Feels like" is the apparent temperature from temperature, humidity and wind:
humid heat feels hotter, dry air and wind feel cooler. Indoors there is no
wind; at the edge of a system's reach the outdoor wind blends in.

The HUD's comfort word, bar and colour follow "feels like" in air, so a humid
84 °F room reads *Hot*. In water they follow the water temperature.

## Water

Water keeps its own temperature. Water no pool system reaches (lakes, the sea,
an unheated pool) reads the biome's **natural water temperature**: the biome's
daily average pulled toward mild values, with no day/night swing and never
below 33 °F. It follows the seasons a few days late, so lakes are coldest
late in winter. A heated pool's edge blends toward that, and an idle pool drifts
toward it the way an idle room drifts toward the outdoor air.

## Spot-to-spot variance

Readings differ a little from place to place, so a house isn't one number from
wall to wall:

| Where | Up to |
| --- | --- |
| A heated or cooled room | ±0.5 °F |
| Outdoors | ±1.5 °F |
| A pool | ±0.3 °F |
| Lakes, rivers and the sea | ±0.8 °F |

- Moisture stays nearly even, so a warmer spot reads a little drier.
- The difference builds gradually over a few blocks and drifts over a few
  in-game hours, so readings never flicker.
- Only **readings** vary: thermometers, sensors, the HUD, `/hvac temperature`
  and placeholders. The thermostat and its sign keep the room's own value, so a
  system never chases the variance.

## Tuning

Everything here is adjustable under `biomes:`, `biome-blend:`, `seasons:`,
`altitude:`, `underground:`, `weather:`, `humidity:` and `variance:` in
`config.yml`; see [Configuration](configuration.md). The exact formulas are in
the *Outdoor climate* and *Humidity and feels like* sections of
[features.txt](features.txt).
