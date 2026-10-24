# Equipment and thermostats

Everything that makes a system run: the thermostat that decides, the equipment
that heats or cools, and the signs that show what's going on.

## Systems and groups

A **system** is identified by its world and **group** name. Each group has one
thermostat, any number of heat pumps, air conditioners or furnaces, and the
vents linked to it. A group can also have one settings panel and any number of
RPM monitors.

A system conditions either **air** (a room, through iron trapdoor vents) or
**water** (a pool, through waxed copper grate vents), never both. See
[Vents and airflow](airflow.md).

## Thermostats

```text
/hvac create thermostat <id> <group> [targetF]       look at a sign
/hvac create poolthermostat <id> <group> [targetF]   look at a sign ("pool" works too)
```

- **Thermostat:** controls an air system. Targets 60-85 °F, 72 °F if you leave
  it out.
- **Pool thermostat:** the same sign for a water system. Targets 75-104 °F,
  82 °F by default. It only controls boilers and pool heat pumps, so it can't
  be used to heat a room to 104 °F.
- IDs and group names are 1-32 letters, numbers, `_` or `-`. A group has one
  thermostat.

### The thermostat sign

```text
EZvac | main
74.2 F | COOL
Set 72.0 F | 68%
ETA 0:31 | house
```

| Line | Shows |
| --- | --- |
| 1 | The thermostat's name |
| 2 | Room temperature and mode: `COOL`, `HEAT`, `IDLE` or `OFF` |
| 3 | The target, and the system's live capacity while it runs |
| 4 | Time until the target is reached (`Ready` when idle), and the group |

- **Change the target** by editing line 3 of the sign, or with
  `/hvac thermostat <id> set <temperatureF>`.
- **Sneak-right-click** the sign to switch the whole system off (the sign
  shows `System off`) and again to switch it back on. Units you switched off on
  their own stay off.
- The ETA uses the same equipment, speeds, profile and outdoor load as the real
  controller.

### How it decides

- Cooling starts 1 °F above the target and stops 0.3 °F below it; heating
  starts 1 °F below and stops 0.3 °F above.
- Once on, equipment runs at least 15 seconds; once off, it waits at least 10.
- With nothing running, a room drifts toward the outdoor temperature.
- A thermostat in an unloaded chunk, switched off, or with no equipment simply
  idles, and its room keeps drifting without loading the chunk.

All of these are in [Configuration](configuration.md).

## Equipment

Every piece of equipment is a **dispenser**. Look at it and register it in a
group:

```text
/hvac create heatpump <group>
/hvac create ac <group>
/hvac create furnace <group>
/hvac create boiler <group>
/hvac create poolheatpump <group>       ("poolhp" works too)
```

| Equipment | Cools | Heats | Speed | Output while running | System |
| --- | :-: | :-: | --- | --- | --- |
| Heat pump | yes | yes | variable | water | air |
| Air conditioner | yes | | variable | water | air |
| Furnace | | yes | fixed, full | lava | air |
| Boiler | | yes | fixed, full | water | water (pool) |
| Pool heat pump | yes | yes | variable | water | water (pool) |

- Equipment needs no fuel, water, power or items. The dispenser's contents
  and redstone are ignored.
- More units heat or cool faster, with diminishing returns (at most twice one
  unit).
- Heat pumps and air conditioners slow down as the room nears its target.
  Furnaces always run at full heat.
- A pool heat pump can share a pool system with a boiler.

<!-- screenshot: images/equipment-running.webp - an air conditioner (water) and a furnace (lava) running in their casings -->

### The casing

While a unit runs, its dispenser puts a block of water (lava for a furnace) in
the cell in front of itself, and takes it away when it stops. Equipment is
meant to be built inside a closed casing, so that cell is boxed in on every
open side and nothing spills.

```text
 side view, looking at the dispenser's front:

 [casing][casing][casing]
 [casing][output][casing]    <- water, or lava for a furnace, while it runs
 [casing][casing][casing]

 top view:

         [casing]
 [casing][output][casing]
 [casing][ disp ][casing]
         [casing]
```

![A casing with a glass window](images/equipment-casing.webp)

- Enclose the output cell above, below, left, right and in front. Glass works
  as a window.
- The output cell must be **empty** (air) for the unit to start.
- Use non-flammable blocks around a furnace: its output is lava.
- The casing can be anywhere: the room is reached through the vents you link,
  not through the equipment.
- EZvac only ever removes the water or lava it placed itself. Fluid you put in
  front of a dispenser is never taken over.
- Two units facing the same cell take turns: one wins and the other waits.

<!-- screenshot: a furnace casing, a pool heat pump and boiler beside a pool -->

## Operating profiles

A **settings panel** switches a group between Eco, Normal and Turbo:

```text
/hvac create settingspanel <group> [label]     look at a sign
```

Right-click the sign to move forward through the profiles and sneak-right-click
to move back. Without a panel a group always uses Normal, and removing the
panel returns it to Normal.

| Profile | Default top speed | Variable-unit capacity |
| --- | ---: | ---: |
| Eco | 2,400 RPM | 70% |
| Normal | 3,600 RPM | 100% |
| Turbo | 4,200 RPM | 115% |

Profiles affect heat pumps, air conditioners and pool heat pumps. Every one in
the group gets the same speed. Furnaces stay at full heat in every profile.

## RPM monitors

```text
/hvac create rpmmonitor <group> <heatpump|ac|furnace|boiler|poolheatpump> [label]
```

A sign that shows the average speed, capacity and how many units of that type
are running. The group must already have that kind of equipment.

## Signs in general

- Signs update only within about 64 blocks of a player. Farther signs keep
  their last text and catch up within a second of someone coming near.
- Every line is fitted to what the sign can show, hanging signs included. A
  header drops its `EZvac | ` prefix when the label needs the room, and a
  reading is never shown cut in half.
- Breaking a device tells you what was removed and from which system.
  Removing the last equipment warns that the thermostat will idle until
  equipment is added again; nothing else is deleted.

## Managing systems

```text
/hvac list                                   systems in your world
/hvac info                                   the device you're looking at
/hvac thermostat <id> set|enable|disable|group|info
/hvac equipment enable|disable|group|info    look at the equipment
/hvac group <group> enable|disable|info
/hvac remove                                 the device you're looking at
```

Full details in [Commands](commands.md). For how the controller, speeds and
capacity work exactly, see the *System model*, *Controller order and safety*
and *Thermal and motor performance* sections of [features.txt](features.txt).
