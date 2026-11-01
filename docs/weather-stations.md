# Weather stations

New in BETA 1.0. A weather station is a tower of copper and iron, built by an
admin, that measures the outdoor climate around it and forecasts the next
seven days. Players near one can see its forecast on its screen, by clicking
it, or with `/hvac forecast`.

## Building one

From the bottom up:

| Layer | Blocks |
| --- | --- |
| 1 | A cross: four **waxed copper blocks** round an **iron block** |
| 2 | An **iron block** |
| 3 | **Waxed cut copper**, with a **wall sign** on its front |
| 4 | An **iron block**, with a **waxed lightning rod** on its right |
| 5 | An **iron block**, with a waxed lightning rod on its left |
| 6 | An **iron block**, with a waxed lightning rod on its right |

```text
        side view, facing the sign

              [iron]=  rod on the right
           =[iron]     rod on the left
              [iron]=  rod on the right
             [ sign ]  on the waxed cut copper
              [iron]
     [copper][iron][copper]   the cross: copper all round the iron
```

- **Left and right** are as you see them standing in front of the sign. The
  mirror image (left, right, left) works too.
- **The rods point out sideways** from the iron, as a rod does when you place
  it on the side of a block.
- **Waxed copper of any colour** counts: plain, exposed, weathered or
  oxidized, as long as it's waxed, so the tower never changes on its own.
- **Before 1.21.9** (the copper age) lightning rods have no waxed kind, so a
  plain one does.

Then look at the sign and run:

```text
/hvac create weatherstation Harbour
```

The label is optional (`Station` if you leave it out) and may repeat. If the
tower isn't whole, EZvac says which block it still needs and where. Stations
only work where there's weather: in the Overworld.

## What it measures

A station only ever reads EZvac's **outdoor** climate: the biomes, the height,
the season and the weather. It never reads a room's temperature, so a heated
or cooled house beside it can't skew it.

It measures an area **100 blocks each way and 20 blocks up**
(`weather-stations.area-radius` and `area-height`). It samples a spot every
16 blocks across it, at three heights. Each spot is already EZvac's climate
blended from the biomes around it, so the whole area counts. Only loaded
chunks are sampled, and none is loaded for it.

It measures again at **sunrise and sunset**, and forecasts again when the
weather changes. It remembers what it measured, so its forecast works while
its chunk is unloaded.

## The forecast

Seven days, today first, each with its weather, high and low (whole
degrees), chance of rain or snow, chance of thunder, wind and pollen. A
station is honest about what EZvac knows:

- **Temperatures are exact.** Each day's high and low come from the area's
  climate and that day's season, with the cooling its chance of rain brings.
- **The wind is exact.** EZvac draws each day's wind from the world's seed,
  so the forecast shows the very wind that will blow.
- **Pollen** follows the season, so it's predictable too.
- **Rain and thunder are chances.** Minecraft keeps a countdown to when the
  current spell of weather ends, so a station knows that for certain: "Rain
  from 15:00", "Rain until 09:00", "Clear all day". How long the next spell
  lasts is random, so later days give a chance instead, worked out by playing
  Minecraft's weather forward hundreds of times. The chances stay the same
  each time you look; they change when the weather does.
- **Dry biomes** (deserts, badlands) never get rain. Cold ones get snow.

## Using it

- **The screen** shows the outdoor temperature big and today's weather,
  high and low, and wind. Below that comes what's certain about today, or
  else tomorrow's weather. Its header is coloured by today's sky: ☀, ☂, ❄ or ⚡.
- **Clicking it** (anyone with `ezvac.use`) opens its forecast.
- **`/hvac forecast`** opens the forecast of the stations within **400
  blocks** (`weather-stations.range`) in your world. Up to five are listed
  along the top, nearest first; click one to see its forecast.
- **An admin clicking it** gets its menu instead: switch it on or off, see
  its record (whether its tower is whole, and how many times lightning has
  struck it), and open its forecast.
- **`/hvac info`** on it: on or off, the outdoor temperature around it,
  today's weather, and its lightning strikes.

## Sparks and lightning

- **Sparks:** while a station is on, its rods give off electric sparks when
  someone is within 32 blocks. `weather-stations.sparks: false` turns them
  off.
- **Lightning:** its rods catch lightning like any lightning rod, and the
  station counts every strike on its tower. The count is saved, and shows in
  its menu and `/hvac info`.

## Damage, removal and switching off

- **A damaged tower** (a block broken, blown up, or changed with `/fill` or
  WorldEdit) stops the station. Its screen says **Damaged: rebuild its
  tower**. Rebuild it and it works again on its own. `/hvac sync` checks
  every station at once.
- **Breaking the sign** or `/hvac remove` removes the station. The tower
  stays.
- **Switched off** in its menu, it shows **Off** and isn't listed by
  `/hvac forecast`.
- **Server-wide:** weather stations are a kind on the Devices page of
  [quick settings](screens-and-menus.md#devices). Switched off, every station
  shows **Off server-wide**, `/hvac forecast` says forecasts are off, and no
  new one can be made.
