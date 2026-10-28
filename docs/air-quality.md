# Air quality and air purifiers

Since 3.2, every room and every standalone unit's space knows what is in its
air. EZvac tracks three things:

| | What it is | Comes from |
| --- | --- | --- |
| **PM2.5** | fine particles, in µg/m³ | smoke: campfires, fire, lava, lit furnaces and candles; the Nether's ash |
| **PM10** | every particle up to 10 µm, fine ones included | the above, plus dust: deserts, badlands, sea salt, the Nether's spores |
| **Pollen** | grains/m³ | plants outdoors, through the seasons |

## The AQI

The **Air Quality Index** is the US EPA's, from 0 to 500. PM2.5 and PM10 each
get a score, and the AQI is the higher of the two.

| AQI | Band | Colour |
| --- | --- | --- |
| 0-50 | Good | green |
| 51-100 | Moderate | yellow |
| 101-150 | Unhealthy for Sensitive Groups | orange |
| 151-200 | Unhealthy | red |
| 201-300 | Very Unhealthy | purple |
| 301-500 | Hazardous | dark red |

- **When the air isn't Good, EZvac names the cause:** `Smoke` when PM2.5
  gives the score, `Dust` when PM10 does.
- **The EPA scores a 24-hour average.** EZvac scores the air as it is right
  now, so a campfire shows up within the hour.

**Pollen is not part of the AQI.** A meadow in spring can have clean air and a
terrible pollen count, as in real life. It has its own level instead: Low,
Moderate, High or Very high. The default thresholds are 20, 80 and 200
grains/m³.

## Outdoors

Each biome has its own air. Biome borders blend, like temperature.

| Biomes | Particles | Pollen at its spring peak |
| --- | --- | --- |
| Flower forest, meadow, cherry grove, sunflower plains | clean | very high |
| Plains, forests, savanna, jungle, swamp | clean | high |
| Taiga, hills | clean | moderate to high |
| Desert, badlands | dusty (Moderate AQI) | very low |
| Snowy biomes, peaks, oceans | very clean | none to low |
| Nether wastes, crimson and warped forests | smoke and spores (Unhealthy) | none |
| Soul sand valley, basalt deltas | ash (Very Unhealthy to Hazardous) | none |
| The End | almost nothing | none |

- **Pollen follows the seasons.** It peaks in mid-spring. Summer's grasses
  bring it to 60% of the peak, autumn's weeds to 25%, and there is none in
  mid-winter. It changes gradually from one season to the next. With seasons
  switched off, it stays at the summer level.
- **Rain and snow wash the air.** Full rain takes out 60% of the particles and
  85% of the pollen.
- **Underground,** pollen fades with the sky, but particles stay.
- **Wind moves the air (3.2).** A strong wind lifts dust where the ground is
  dry, so a desert gale is a dust storm, Unhealthy from the PM10. It thins
  the outdoor smoke and spreads pollen, and indoors it makes rooms follow the
  outdoor air faster. See [Wind](wind.md).

## Indoors

A room's air drifts toward the outdoor air through the gaps in the building,
at the same rate its moisture does. On top of that:

- **What burns adds smoke.** Lit campfires, fire, lava, lit furnaces, smokers,
  blast furnaces and lit candles, inside the room or in its walls.
  - **A bigger room dilutes it;** a small one concentrates it, up to four
    times.
  - **A doused campfire or an unlit furnace adds nothing.**
  - **Torches don't count,** and neither do EZvac's own furnaces and boilers,
    which are sealed.
- **Particles settle out of still air,** the big ones fastest. A closed room
  gets rid of dust and pollen on its own, but smoke lingers.
- **A system's own filter** cleans its room while its equipment runs. It
  catches most pollen and a fair share of dust, but little smoke: smoke is a
  purifier's job.

A campfire in a room with nothing cleaning it reaches an Unhealthy AQI. One
purifier keeps a normal-sized room with a campfire Good.

A space with no system and no standalone unit isn't tracked: it reads the
outdoor air, as its humidity does.

## The air purifier

### Build

An **iron block on top of a tube coral block**, with a sign on the iron block,
either on its side or standing on top. It's built like a dehumidifier, with
coral where the grate goes.

```text
 side view:

 [ iron  ][sign]      or        [ sign ]
 [ coral ]                      [ iron ]
                                [ coral]
```

Look at the sign and run:

```text
/hvac create purifier [label]          ("airpurifier" works too)
```

Tube coral dies within seconds away from water, so you can also build it
without the coral and make the purifier first: it waits with `No filter`.
Then place the tube coral block under the iron block; from then on it stays
alive (3.2 HOTFIX 1).

Like the other standalone units it needs no system:
- inside a system's room, it cleans that room;
- anywhere else, it cleans the space around it, up to 30 blocks of airflow
  away.

### Using it

```text
EZvac | Hall
AQI 88 Smoke
Auto · High
Filter 62%
```

Line 2 is the AQI of the space it cleans, coloured by its band. Line 3 is its
fan.

Like a real purifier it has **fan speeds** and an **Auto** mode, and no
target to set:

| Mode | Cleans at |
| --- | --- |
| Low | 35% of full strength: quiet, gentle |
| Medium | 65% |
| High | full strength |
| Auto | picks the speed from the air it reads |

**Auto** idles on Low in clean air. It never switches off, so it's already
working when something starts to smoke. Then it steps up:

| The air | Auto runs at |
| --- | --- |
| Good AQI, and pollen Low | Low |
| Moderate AQI (above 50), or Moderate pollen | Medium |
| AQI above 100, or High pollen | High |

It steps back down only once the air is clearly better: the AQI 20 under the
step, and the pollen a tenth under its level. That way it doesn't flicker
between two speeds.

- **Right-click** the sign for its control menu: one button per mode (the
  one it's on glints) and a lever to switch it on or off.
- **Sneak-right-click** switches it on or off.
- **New purifiers start in Auto.**
- **The speeds and Auto's steps** are set under `air-quality.purifier` in
  the config.

### The filter

The tube coral is the filter. It fills with what the purifier catches:
- slowly in clean air;
- much faster in smoke, dust or a spring full of pollen;
- faster in a big space than in a small one with the same air.

As a guide, a filter lasts a few in-game days beside a campfire. In clean
air it lasts about two months on High, and longer at a slower speed.
A faster fan catches more, so it fills its filter faster, and Auto in clean
air barely touches it.

- **The sign's last line** shows how much of the filter is left.
- **When the filter is full,** the coral dies and the purifier stops with
  `Filter spent`. Break the dead coral and place a fresh tube coral block:
  the purifier starts again with a new filter.
- **Taking the filter out** leaves the purifier waiting with `No filter`.
  The purifier itself stays registered; breaking the sign or the iron block
  removes it.
- **A part-used filter keeps its level.** If you take it out and put it back,
  it keeps what it had left. Only a spent filter is replaced by a fresh one.
- **The coral doesn't die on its own.** Tube coral normally dies away from
  water; under a purifier it stays alive until its filter is full.

> **Silk Touch:** breaking live coral without Silk Touch drops dead coral, so
> collecting fresh filters takes a Silk Touch pickaxe, or a trip to a coral
> reef.

## Reading the air

- **`/hvac temperature`** shows the air where you stand.
- **`/hvac info`** on a thermostat or a purifier gives the AQI and the raw
  PM2.5, PM10 and pollen values.
- **Air monitors:** `/hvac create airmonitor [label]` on a sign shows the AQI,
  the cause and the pollen where it stands.
- **Air sensors:** a **cherry button** registered with `/hvac create sensor`
  reads the air for a sensor hub, as `AQI 42`.
- **The HUD and placeholders:**
  - `{aqi}`, `{aqi_band}` and `{pollen}` in HUD templates;
  - `%ezvac_aqi%`, `%ezvac_aqi_colored%`, `%ezvac_aqi_band%`, `%ezvac_pm25%`,
    `%ezvac_pm10%`, `%ezvac_pollen%` and `%ezvac_pollen_count%` for
    PlaceholderAPI;
  - see [HUD and placeholders](hud-and-placeholders.md).
- **Addons** get it through the API, since 1.5.

**What bad air does to players is up to addons:** EZvac measures it and
leaves the effects to them.

## Turning it off

`air-quality.enabled: false` hides every air reading, line, token and
placeholder value, and purifiers sit idle. Every setting is described under
`air-quality` in [Configuration](configuration.md).
