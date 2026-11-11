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
| Dappled forest | 66 °F | 46 °F | 18 °F | 84 °F | 48 °F |
| Pale garden | 62 °F | 48 °F | 16 °F | 78 °F | 46 °F |
| Ocean | 66 °F | 60 °F | 10 °F | 76 °F | 56 °F |
| Taiga | 52 °F | 32 °F | 24 °F | 76 °F | 28 °F |
| Snowy plains | 28 °F | 8 °F | 26 °F | 54 °F | 2 °F |
| Caves (lush, sulfur, dripstone, deep dark) | 64, 64, 58, 52 °F | same | none | | |
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
once the air there is cold enough (BETA 1.0 Pilot 4; see [Weather](#weather)).

## Underground

Deep underground the air holds the ground's steady temperature, as in a real
mine: no day/night swing, no seasons and no weather, just the daily average of
the climate above. It sets in 4 blocks below the surface and is complete at
20. Depth is measured from the surface or from sea level, whichever is lower,
so a hill or a tall house never counts as underground. A basement is partly
tempered. Caves inside a mountain, above sea level, keep the outdoor swing.

## Seasons

Each world with a sun has a year of four seasons, 30 Minecraft days each by
default (a 120-day year, about 40 hours of play; 15 days before BETA 1.0
Pilot 4). Temperatures follow a smooth curve: coldest in mid-winter, warmest
in mid-summer, and average in mid-spring and mid-autumn.

- Seasons move temperatures, and so whether rain falls as snow (BETA 1.0
  Pilot 4): the plains snow in a cold winter. Biomes stay as Minecraft makes
  them.
- `seasons.days-per-season` can be changed at any time (restart after): every
  world keeps its season and day of season, so summer day 8 stays summer
  day 8. A server on the old stock 15 moved to 30 on its first Pilot 4 start,
  the same way; a length an owner chose was kept.
- How far a biome moves is its own (the *Seasons ±* column): strong in the cold
  north, weak in the tropics. `seasons.strength` scales them all, such as 0.5
  for mild seasons.
- Lakes and the sea follow behind the air, 10 days by default (a third of a
  season), and move a little less.
- EZvac keeps its own calendar for each world, in `seasons.yml`. It follows
  the world's days, so sleeping through the night or `/time add` moves it on
  by every day they pass (BETA 1.0 Pilot 3: before, one day at most at a
  time), but setting the clock back (`/time set day`, which restarts
  Minecraft's day count) never sends the year back to spring. With
  `doDaylightCycle` off, the season stands still.
- A new calendar starts on Minecraft's own day count (BETA 1.0 Pilot 3), so
  day 0 of a world is the first day of spring and a world on day 500 starts
  where day 500 falls. A world that already has a calendar keeps its season
  and day. (From 2.1 to Pilot 2, a new calendar started on the first day of
  spring whatever the world's day.)
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
- **Rain or snow is EZvac's own call** (BETA 1.0 Pilot 4), by its air, so
  the season and the time of day count: a cold night can turn evening rain
  to snow, and a warm spell rains on the tundra. Vanilla decides by biome
  alone.
  - **Decided across the land, not by height:** each 64-block area works out
    how far the air at its ground is from the line, every spot blends that
    from the four nearest areas and a little noise is added, so rain and
    snow meet along a wavy line on the land that follows the cold, never in
    layers above you and never along a grid.
  - **It snows where that air is at or below 33 °F, and in winter below
    40 °F** (`weather.snow-at-or-below-f`, `snow-in-winter-below-f`): real
    snow often falls a few degrees above freezing. So the plains and forests
    snow through most of winter and rain on a mild day, while savannas and
    jungles still rain. Set the winter one to 200 for snow all winter
    wherever weather reaches.
  - **Peaks:** ground 48 blocks or more above its area's snows by its own
    air, so a mountain top can be white while the valley rains.
  - **A storm keeps its kind:** an area that is snowing keeps snowing until
    its air is 2 °F above the line (and rain the same the other way), and
    changes at most once an in-game hour, so it doesn't flicker at dawn.
- **EZvac only says what players see.** The world itself shows Minecraft's
  own rain and snow, by biome, unless an addon such as EZclimates shows
  EZvac's. So:
  - **where an addon shows EZvac's rain and snow,** the weather word on the
    HUD, sensors and `%ezvac_weather%`, and weather stations' forecasts (by
    the day's air, with winter's line on winter days), follow EZvac's call,
    and rain reaches dry places as below;
  - **everywhere else,** they follow Minecraft's: rain or snow by biome
    (snow high on a mountain), and dry places stay dry, whatever
    `weather.dry-areas` says. The temperatures still follow EZvac's air.
- **Dry places** (deserts, savannas, badlands), where an addon shows EZvac's
  weather, follow `weather.dry-areas`:
  `seasonal` by default, so some rain spells reach them, mostly in winter
  (6 in 10) and rarely in the other seasons (`weather.dry-rain-chance`).
  Each spell is decided once as it starts, for the whole world, and rain
  that reaches them cools and dampens the air there. `never` keeps them dry
  as in vanilla, `thunder` rains on them only in thunderstorms, `always`
  like anywhere else. The Nether and the End never get weather.
- Underground, weather doesn't reach.
- Everything that uses the outdoor temperature follows: rooms drifting with
  nothing running, ETAs, thermometers, sensors, the HUD and pools. Natural water
  barely reacts (10% of the air's change).
- Wind makes the outdoors feel colder. Since 3.2 every day has its own wind,
  from Calm to a Gale, and rain and thunder add to it: see [Wind](wind.md).

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
wind; at the edge of a system's reach the outdoor wind blends in. Walls and
roofs shelter a spot from it too (3.2): you feel the wind by how much sky
reaches you. See [Wind](wind.md).

**In the sun** (BETA 1.0 Pilot 4) it feels warmer than in the shade: the sun
adds Steadman's radiation term. It is strongest at noon in summer, less in
winter's lower sun, little under clouds or rain, none at night, and a breeze
carries it away. Full sun is where nothing is above you; under leaves or a
roof you get a share of it by how much sky reaches you, and indoors none. A
clear summer noon in a field feels about 8 to 10 °F warmer than the forest's
shade beside it. Only the feels-like changes: the air temperature, rooms and
systems don't, and weather stations stay in the shade, as real forecasts
do. `feels-like.sun: false` turns it off; `feels-like.sun-strength` scales
it.

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
