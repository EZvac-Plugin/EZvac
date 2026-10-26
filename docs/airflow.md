# Vents and airflow

Vents decide which space a system conditions. A system only reaches the blocks
its vents can blow air (or water) into.

## Linking vents

```text
/hvac link-tool        ("duct-tool" and "tool" still work)
```

1. Right-click the system's thermostat to select it.
2. Right-click vent blocks in the same world to link them.
3. Sneak-right-click a vent to unlink it.

Only blocks you link become vents.

| Vent block | Conditions | Fed by |
| --- | --- | --- |
| Iron trapdoor | the room around it | heat pumps, air conditioners, furnaces |
| Waxed copper grate, placed in water | the water around it | boilers, pool heat pumps |

A system is all air or all water, and its equipment must match: linking a grate
to an air system, or a trapdoor to a pool system, is refused.

## How far air reaches

Each vent spreads air in all six directions through open space:

- **0-15 blocks** of airflow get the full thermostat temperature (the **core**).
- **16-30 blocks** blend back toward the outdoor temperature (the **edge**).
- **Beyond 30** the system has no effect.

Distance is counted along the path the air takes, not in a straight line, so
air goes around corners and through doorways but not through walls.

### What air passes through

- Open space and passable blocks carry air.
- Solid blocks and liquids stop it.
- Doors and trapdoors pass air when open and stop it when closed.
- Carpets (wool and moss) pass air, so a carpeted floor is still part of the
  room.

When you open a door or break a wall, the system's airflow updates about a
second later. More vents cover more of a big space.

<!-- screenshot: a room with two iron trapdoor vents and the link tool -->

## Water systems (pools)

A pool works exactly like a room, through water instead of air:

1. Build a **boiler** or **pool heat pump** in a casing (see
   [Equipment](equipment.md#the-casing)).
2. Place a sign and run `/hvac create thermostat <label> [target]`.
3. With the link tool, select the thermostat, then link the boiler and put
   **waxed copper grates** in the pool and link them. The thermostat becomes a
   pool thermostat (75-104 °F) as soon as the first one is linked.

```text
 side view of a pool:

 [edge ][water][water][water][edge ]
 [edge ][water][GRATE][water][edge ]   <- a linked waxed copper grate
 [edge ][edge ][edge ][edge ][edge ]
```

- Water systems travel through water and stop at everything else.
- Only the **waxed** copper grate counts. Waxed copper never oxidises, so a vent
  can't change by itself. Scraping the wax off with an axe unregisters the vent,
  the same as breaking it.
- Water keeps its own temperature: water no pool system reaches (lakes, the
  sea, an unheated pool) reads the biome's natural water temperature, steady,
  with no day/night swing. The edge of a heated pool blends toward that, not
  toward the air.
- The thermostat, timers, multiple units and ETA all behave as they do for a
  room.

## Good to know

- Airflow never loads chunks, and big edits (WorldEdit, FAWE) are handled
  without slowing the server; see *Vents and airflow* in
  [features.txt](features.txt) for the limits.
- Each system covers at most 200,000 blocks of airflow by default.
- A dehumidifier's grate can't also be a water vent.
