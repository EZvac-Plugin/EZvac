# Contributing to EZvac

EZvac is a standalone HVAC and indoor-climate simulation plugin for Paper
1.21.1 and its forks, including Purpur. Contributions are welcome — bug
reports, fixes, documentation, and features alike.

## Licensing of contributions

By submitting a contribution you agree that it is licensed under the
[MIT License](LICENSE), the same terms as the project ("inbound = outbound").
You keep the copyright in your own work; you are simply licensing it under MIT
so the project can distribute it.

There is no CLA and no copyright assignment. Only contribute code you have the
right to license this way — do not paste in code from a project under different
terms.

Releases through ALPHA 1.1 HOTFIX were public domain under The Unlicense.
ALPHA 1.5 and everything after it is MIT.

## Requirements

- Paper 1.21.1 (or a Paper fork such as Purpur)
- Java 21
- Maven 3.9 or newer

## Build and test

```shell
mvn clean package
```

This compiles, runs the test suite, and writes `target/EzVac-ALPHA-1.6.1.jar`.
To run only the tests:

```shell
mvn verify
```

Every push and pull request is built and tested automatically by GitHub
Actions, and the resulting jar is attached to the run so you can download and
try a change without building it yourself.

Tests live under `src/test/java`. The simulation classes (`simulation/`,
`config/`, and the `model/` records) have no Bukkit dependencies and are
directly unit-testable — please add coverage there when you change them.

## Testing against a real server

Anything touching Bukkit APIs is not covered by the unit tests. For those
changes, run the plugin on a local Paper 1.21.1 server (or Purpur) before
opening a pull request, and say in the PR what you exercised.

Two lessons worth repeating, both of which caught real bugs:

- When you replace a Bukkit call with a cheaper equivalent, verify the two are
  actually equivalent rather than assuming. A throwaway plugin that iterates
  `Material.values()` and applies both the old and new predicate to a real
  block will tell you in seconds. `BARREL` implements `Openable`, which is not
  obvious until something reports it.
- Airflow cell counts vary between runs because chunk residency varies. Before
  attributing a difference to your change, re-run the unmodified build.

## Architecture

- `service/HvacController` — the phased controller: measure, integrate, decide,
  actuate. It runs on a timer and re-derives device state from the world, so it
  is safe for it to miss a cycle.
- `service/HvacRegistry` — the sole owner of runtime device state and of
  persistence to `plugins/EZvac/hvac.yml`.
- `service/AirflowService` — tick-budgeted breadth-first airflow maps, rebuilt
  on invalidation and debounced.
- `service/DisplayService` — renders all sign devices, de-duplicated by
  fingerprint so unchanged signs are not rewritten.
- `simulation/` — pure, Bukkit-free thermal, motor, and ETA models.

`docs/features.txt` describes the intended behaviour in detail and is the best
starting point for understanding what the plugin is meant to do.

## Performance

EZvac is tick-sensitive. Two paths run constantly and deserve care:

- `AirflowService.tick()` runs every server tick.
- `HvacController.cycle()` runs on `controller.period-ticks` and its cost scales
  with device count.

If you change either, measure rather than assume. `CHANGELOG.md` records the
methodology and numbers from the ALPHA 1.5 overhaul, including the mistake of
optimising the wrong thing first.

Avoid on hot paths: `Block#getState()` when a `Material` or `Tag` check will do
(it snapshots the whole tile entity), `Block#getBlockData()` in a loop (it
allocates), and boxed collections keyed by coordinates.

## Pull requests

- Keep the change focused; separate refactors from behaviour changes.
- Match the surrounding style — this codebase favours explicit phases, small
  methods, and comments that explain *why*.
- Say what you tested. "Ran on Paper 1.21.1 with N thermostats" is worth more
  than a green checkmark.
- If you change behaviour that players can observe, update `docs/features.txt`
  and add a `CHANGELOG.md` entry.

## Reporting bugs

Include the server software and version (Paper, Purpur, ...), the Java version,
the EZvac version, the relevant part of `plugins/EZvac/hvac.yml`, and any stack
trace from the server log.
Device counts and whether players were online when it happened are useful —
several classes of problem only appear under load.
