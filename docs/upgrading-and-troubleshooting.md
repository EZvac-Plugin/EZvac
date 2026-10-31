# Upgrading and troubleshooting

## Installing

You need Paper or Purpur for Minecraft **26.1 to 26.3**, on Java 25 (Paper's
own requirement). Minecraft 1.21.x on Java 21 still works in BETA 1.x, with a
warning, but BETA 2.x will drop it: it needs 26.1 or newer. BETA 1.x is
tested on 1.21.1; 1.21.2 to 1.21.11 should work but aren't tested yet.

1. Put `EzVac-BETA-1.0-P1.jar` in the server's `plugins` folder.
2. Start the server and check the log for `EZvac enabled: ...` with no errors.
3. Run `/hvac stats` and `/hvac list`, then try one heating and cooling cycle.

## Upgrading

Upgrading from any earlier EZvac release is supported:

1. Stop the server and back up `plugins/EZvac/`.
2. Replace the EZvac jar with the new one.
3. Start the server.

Your `plugins/EZvac/hvac.yml` is read as it is: every release's save file is
covered by the test suite.

- **From 3.2 to BETA 1.0,** swap the jar: the save stays **format 8**, on
  1.21.x and on 26.x alike. Your config.yml gains `updates`. Going back to
  3.2 is a jar swap too, as long as the server is still on 1.21.x.

> **2.x to 3.0 is a one-way upgrade.** 3.0 converts `hvac.yml` to **format
> 7** the first time it saves. Your 2.x file is kept as
> `hvac.yml.format6.bak`, and EZvac 2.x will not start on the new file.
> Going back means restoring that backup and losing everything built or
> changed since the upgrade (see [Going back](#going-back)). Back up
> `plugins/EZvac/` before you start 3.0.

- **From 3.1 to 3.2,** swap the jar: the save stays **format 8**. Every
  room's air starts at the outdoor level and settles from there; a room with
  a lit campfire gets smoky within the in-game hour. The wind starts with
  the day you upgrade on. Your config.yml gains `air-quality` and `wind`
  with their comments, and a sidebar with the stock lines gains the air and
  wind lines. Going back to 3.1 is a jar swap too: 3.1 keeps the air
  purifiers, air monitors, air sensors and wind sensors it doesn't know as
  preserved records, with a warning, and 3.2 brings them back.
- **From 3.0 to 3.1,** the save moves to **format 8**, and your 3.0 file is
  kept as `hvac.yml.format7.bak`. Every unit becomes ★★★ variable at full
  capacity: compressors run exactly as before, and furnaces and boilers ease
  off near the target. New units are two-stage unless you name `t3`. Going
  back to 3.0 means restoring that backup.
- **From 2.x to 3.0,** groups become linked systems:
  - every old group becomes a system with the same thermostat, equipment,
    vents, settings panel and RPM monitors, so nothing needs relinking;
  - a group that had no thermostat comes back headless, waiting for a
    thermostat to be linked to it (or for `/hvac remove system`);
  - every label stays as it was, even old group-style names such as
    `2230radcliffedr2stat`. Tidy them with `/hvac rename` whenever you like;
  - an RPM monitor that watched one kind of equipment now shows all of its
    system's;
  - duct and sensor tools in inventories work as link tools;
  - command blocks or scripts that use `/hvac group`, `/hvac thermostat
    <name> ...` or `/hvac equipment group` need changing: those commands now
    act on the device you look at, or are done with the link tool. The old
    create forms still work and ignore the group name;
  - nothing in `config.yml` needs changing: `{group}` and `%ezvac_group%`
    still work and now show the thermostat's label.
- **From 1.x to 2.0,** the save moves to **format 6**, and your 1.x file is
  kept as `hvac.yml.format5.bak`. Each room's humidity starts at the outdoor
  level and settles from there.
- **From 2.1 to 2.2,** nothing changes for your server: 2.2 only adds to
  the API for addons. Swap the jar.
- **From 2.0 to 2.1,** the save stays format 6 and loads as it is. What
  changes when you start it:
  - each world gets a calendar in the new `seasons.yml`, starting on the
    first day of spring;
  - outdoor temperatures follow each biome's own climate, the height and the
    season, so rooms drift to new outdoor levels over the first in-game hours;
  - a sidebar with the stock lines gains the season line and the `{addons}`
    line for addon sections;
  - signs show a screen, and right-clicking a thermostat or (de)humidifier
    sign opens its control menu. Signs can no longer be edited to change a
    target;
  - `units: F` or `C` now applies everywhere. A server that had `hud.units: C`
    starts with `units: C`.
- **Your `config.yml`** gains every new setting with its comments. Nothing you
  set is changed, and the previous file is kept as
  `config.yml.before-<version>.bak`. See
  [Configuration](configuration.md#your-configyml-stays-up-to-date).

### Building size starts off

BETA 1.0 can heat and cool by building size: one ★★★ unit per 1,860 blocks
of air. It would change how existing builds play, so on a server upgrading
from ALPHA it starts off (`sizing.enabled: false` is added to `config.yml`),
and admins are told once. Switch it on in `/hvac quicksettings` › Climate
systems; `/hvac stats sizes` then shows which systems need more units, and
it switches off again there anytime. See [Sizing](equipment.md#sizing).

### Moving to Minecraft 26.x

BETA 1.0 runs on 1.21.x and on 26.1 to 26.3, so EZvac and the server can be
updated in either order. BETA 2.x will drop all of 1.21.x, so plan the move.
`/hvac update` says whether the newest EZvac runs on your server's Minecraft,
and which one does if it doesn't.

1. **Back up the whole server:** the world folders (`world`, `world_nether`,
   `world_the_end`) and `plugins/EZvac/`.
2. **Put EZvac BETA 1.0 in,** if it isn't already.
3. **Switch the server to Paper or Purpur 26.x, on Java 25.**
4. **Start it once and let Minecraft move the worlds.** On the first start,
   Paper warns that the world storage needs migrating and waits 30 seconds
   before doing it. It moves the Nether and the End into
   `world/dimensions/`, which can't be undone.
5. **Check the log** for `EZvac enabled: ...` with the same counts as before.

Every device keeps its world, the Nether and the End included: Minecraft
keeps each world's id through the move, and EZvac finds devices by that id.
Tested on a real 1.21.1 world with 6 systems, upgraded to 26.3 with every
device and airflow path intact.

Addons: EZdebug+ 0.2.1 and LiveExposure 0.2.0 work on 26.x unchanged.

**Going back to 1.21.x** means restoring the world backup. That's a
Minecraft limit, not an EZvac one: a 26.x world can't be opened by 1.21.x.

### Going back

Downgrading isn't supported. From 1.7 on, EZvac refuses to start on a save
written by a newer version and leaves the file untouched, instead of loading it
and quietly dropping what it doesn't understand.

To go back anyway: stop the server, restore `hvac.yml.format<N>.bak` as
`hvac.yml`, and put the old jar back. Anything built since the upgrade is lost.

There is no converter from format 7 back to 6, and there can't be a faithful
one. 2.x needs a unique name for every thermostat and a group for every
settings panel and RPM monitor, and it can't read a system created in 3.0.
The 3.0 file itself doesn't lose your group names: an upgraded system keeps
its old group name as its hidden key.

2.0 and 2.1 share format 6, so 2.0 loads a 2.1 save as it is. It drops the few
details only 2.1 knows (each thermostat's seasonal swing and its rain/snow mix
on a biome border), and 2.1 samples them again if you come back.

## Your data

- **Save file:** everything EZvac knows is in `plugins/EZvac/hvac.yml`. It's
  saved every 30 seconds and on shutdown.
- **Safe writes:** each save writes a temporary file, keeps the previous one
  as `hvac.yml.bak`, then swaps the new one in. A crash mid-save leaves a good
  file behind.
- **Unreadable save:** if `hvac.yml` can't be read, EZvac recovers it from
  `hvac.yml.bak`, else (BETA 1.0) from the newest readable backup in
  `plugins/EZvac/backups/`. The unreadable file is kept as
  `hvac.yml.unreadable-<time>`. If nothing can be read, EZvac **stops**: see
  [When hvac.yml is broken](#when-hvacyml-is-broken).
- **Invalid records:** a single record EZvac can't read is set aside with a
  warning and kept in the file, rather than deleted.
- **Upgrading** never counts as any of this: an older file loads as it is
  (a one-time `hvac.yml.format<N>.bak` copy is made first), with no warning.
  Admins are told it went through (BETA 1.0): "hvac.yml was upgraded from
  format 7 to format 8 at this start: everything carried over".
- **Missing blocks:** devices whose blocks are gone are removed once their
  chunk is loaded. Devices in unloaded chunks or worlds are kept.
- **Player choices** (the HUD on or off) are stored with each player, not in
  `hvac.yml`.
- **Backups:** since BETA 1.0, [quick settings](screens-and-menus.md#backups--registry)
  backs the registry up on demand, or every few hours, to
  `plugins/EZvac/backups/`.

### Restoring a backup

1. **Stop the server.** A running EZvac would save over a restored file.
2. **Keep the current files aside,** in case: copy `hvac.yml` and
   `seasons.yml` somewhere else.
3. **Copy the backup over them:** `backups/hvac-<date>_<time>-manual.yml` (or
   `-auto.yml`) becomes `hvac.yml`, and the `seasons-` file with the same date
   and time becomes `seasons.yml`.
4. **Start the server** and check the log for `EZvac enabled: ...` with the
   counts you expect.

Devices built after the backup was made aren't in it: register them again.

### When hvac.yml is broken

New in BETA 1.0: admins are told in game, not only in the server log, a
moment after they join (once each start).

- **Recovered from a backup:** "hvac.yml was unreadable at this start, so it
  was recovered from ...". Changes made since that copy may be missing; the
  unreadable file is kept beside it, for anything worth copying back by hand.
- **Entries kept aside:** some entries couldn't be loaded and aren't running.
  `/hvac registry` lists them and why. Fix them in `hvac.yml` and restart, or,
  if that's intentional (you removed something by hand, say), run
  `/hvac registry repair --force` to discard them for good. `hvac.yml` is
  copied to `hvac.yml.before-repair-<time>` first.
- **Stopped:** nothing could be read, so EZvac runs nothing and saves
  nothing, but stays loaded: `/hvac` says why, and admins hear it on joining.
  Fix or restore `hvac.yml` and restart, or, if that's intentional, run
  `/hvac registry repair --force` (an admin, or the console). It keeps every
  entry that still reads and drops only the broken ones, keeps the file as it
  was as `hvac.yml.unreadable-<time>`, and asks for a restart.
- **A file from a newer EZvac** stops EZvac too, but is never forced: that
  would lose what the newer version stored. Update EZvac instead, or restore
  an older backup.
- **`/hvac quicksettings` opens recovery while stopped:** why EZvac stopped,
  **Salvage hvac.yml** (an unreadable file only), and the newest backups in
  `plugins/EZvac/backups/`, each one this EZvac can read restorable. Click an
  item, then click it again, to do it. Restoring copies the backup over
  `hvac.yml` (and its seasons file over `seasons.yml`), keeping the files it
  replaces as `.before-restore-<time>`. Then restart.

## Troubleshooting

**Equipment doesn't start.**
- The cell the dispenser faces (above it, in the usual casing) must be
  **empty**: air, or water or lava only flowing through. The unit puts its
  water or lava there. A source block in it, such as one left by a
  bucket or, before BETA 1.0, formed by units' water running together,
  stops it: clear it, and make sure the dispenser faces into its casing. See
  [Equipment](equipment.md#the-casing).
- `/hvac info` on the dispenser shows whether it's enabled and which system
  it's in. "A headless system" means no thermostat controls it: link it to one
  with the link tool.

**A system stays `IDLE` or `OFF`.**
- `OFF` means it was switched off: sneak-right-click the thermostat sign, or
  look at it and run `/hvac thermostat enable`.
- `IDLE` with the room far from the target usually means the system has no
  equipment that can do the job, for example an air conditioner when it needs
  heat. `/hvac thermostat info` on its sign lists what's linked.
- Each change also waits out the minimum run and off times (15 and 10 seconds).

**A room doesn't change temperature.**
- Check the vents are linked: `/hvac info` on an iron trapdoor.
- Stand in the room and run `/hvac temperature`. It names the system reaching
  you, or says none does: a closed door or a wall may be in the way.

**Signs far away don't update.** That's by design: signs are only rewritten
within about 64 blocks of a player, and catch up within a second of someone
coming near. The systems themselves keep running.

**A dehumidifier says `Air too cold`.** The air is already drier than it can
dry (a dew point at or below 35 °F), as in snowy biomes. It's working as
designed. See [Dehumidifiers and humidifiers](dehumidifiers-and-humidifiers.md#air-too-cold).

**A weather sensor says `Covered`.** Something above it (a roof, glass, leaves)
keeps the rain off. Move it under open sky.

**A sensor shows `--`.** Its chunk isn't loaded.

**The log warns about a setting.** A value is out of range. In a 2.0 section
that section runs on its defaults until you fix it; elsewhere EZvac doesn't
start. The warning names the setting.

**The log says `hvac.yml is format 6, written by a newer version`.** The jar is
older than your save. Put the newer jar back; see [Going back](#going-back).

**Sounds are too loud, too quiet or unwanted.** Every sound is configurable, or
can be silenced with `[]`; see [Configuration](configuration.md#sounds).

Still stuck? `/hvac stats` and the server log are the best things to include
when you report a problem on GitHub.
