# Changelog

All notable changes to EZvac are recorded here.

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
