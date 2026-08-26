# Changelog

All notable changes to EZvac are recorded here.

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

  Thanks to [Cole](https://github.com/colebolebole), whose extensive testing of
  the pilot on his server "Soms" is what surfaced the problem. The air range had
  simply been assumed to fit water; running the pilot in earnest showed it does
  not.

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
