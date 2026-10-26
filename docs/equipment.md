# Equipment and thermostats

Everything that makes a system run: the thermostat that decides, the equipment
that heats or cools, and the signs that show what's going on.

## Systems

A **system** is a thermostat and everything linked to it with the link tool:
any number of heat pumps, air conditioners or furnaces, the vents, one
settings panel and any number of RPM monitors. A system has no name of its
own: it goes by its thermostat's.

A system conditions either **air** (a room, through iron trapdoor vents) or
**water** (a pool, through waxed copper grate vents), never both. See
[Vents and airflow](airflow.md).

### The link tool

```text
/hvac link-tool          ("duct-tool", "sensor-tool" and "tool" give it too)
```

1. **Right-click a thermostat** to select it. The tool remembers it, even
   after a relog.
2. **Right-click** a dispenser, an iron trapdoor or waxed copper grate, a
   settings panel or an RPM monitor to link it to that thermostat.
3. **Sneak-right-click** anything to unlink it. Sneak-clicking the thermostat
   itself unlinks it from its system.

The same tool links sensors to a sensor hub: select the hub instead. See
[Sensors](sensors.md).

- **Creating with the tool in hand:** a device you create while holding the
  tool, with a thermostat selected, is linked straight away.
- **Another thermostat's devices are never taken.** Linking something that
  belongs to a system with its own thermostat is refused, and the message says
  where that thermostat is. Unlink it first.
- **Headless systems:** when a thermostat is broken or removed, its system
  stays together, idle: the equipment, vents and signs remain linked to each
  other. Linking a new thermostat to any part of it brings the whole system
  along. A unit created on its own, or unlinked, is a headless system of one.
- **Air or water:** a system can't mix the two. Linking a boiler, a pool heat
  pump or a waxed copper grate makes its thermostat a pool thermostat;
  linking air equipment or an iron trapdoor makes it a room thermostat again.

### Removing a whole system

Hold the link tool, look at any part of the system and run:

```text
/hvac remove system
```

It says what it would remove. Run `/hvac remove system confirm` within 15
seconds to unregister every device of that system. Equipment takes its water
or lava away, as with `/hvac remove`. The blocks themselves stay.

## Thermostats

```text
/hvac create thermostat <label> [target]       look at a sign
/hvac create poolthermostat <label> [target]   look at a sign ("pool" works too)
```

- **Thermostat:** controls an air system. Targets 60-85 °F, 72 °F if you leave
  it out.
- **Pool thermostat:** the same sign for a water system. Targets 75-104 °F,
  82 °F by default. A thermostat becomes one by itself when you link a
  boiler, a pool heat pump or a grate to it; a target still at 72 °F moves to
  82 °F. `poolthermostat` just starts it in the pool range. A room thermostat
  never controls water equipment, so it can't heat a room to 104 °F.
- Labels are 1-32 letters, numbers, `_` or `-`, and may repeat: every house
  can have a Home. `/hvac rename <label>` changes it. A system has one
  thermostat.

### The thermostat sign

```text
EZvac | Home
74.2 F | COOL
Set 72.0 F | 68%
ETA 0:31 | 2/3
```

| Line | Shows |
| --- | --- |
| 1 | The thermostat's name |
| 2 | Room temperature and mode: `COOL`, `HEAT`, `IDLE` or `OFF` |
| 3 | The target, and the system's live capacity while it runs |
| 4 | Time until the target is reached (`Ready` when idle), and how many of its units are running |

- **Change the target** from the menu the sign opens on a right-click (+5, +1
  and +0.1 on the left, the same down on the right), or with
  by looking at the sign and running `/hvac thermostat set <temperature>`.
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

Every piece of equipment is a **dispenser**. Look at it and register it, then
link it to a thermostat with the link tool:

```text
/hvac create heatpump
/hvac create ac
/hvac create furnace
/hvac create boiler
/hvac create poolheatpump       ("poolhp" works too)
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

A **settings panel** switches a system between Eco, Normal and Turbo:

```text
/hvac create settingspanel [label]     look at a sign, then link it
```

Right-click the sign to move forward through the profiles and sneak-right-click
to move back. Without a panel a system always uses Normal, and removing or
unlinking the panel returns it to Normal. A system has one panel.

| Profile | Default top speed | Variable-unit capacity |
| --- | ---: | ---: |
| Eco | 2,400 RPM | 70% |
| Normal | 3,600 RPM | 100% |
| Turbo | 4,200 RPM | 115% |

Profiles affect heat pumps, air conditioners and pool heat pumps. Every one in
the system gets the same speed. Furnaces stay at full heat in every profile.

## RPM monitors

```text
/hvac create rpmmonitor [label]     look at a sign, then link it
```

A sign that shows the equipment of its system, one row per kind:

```text
EZvac | Fans 1/2
Heat pump ×3 2,950
Furnace off
AC ×2 3,100
```

- **A row** is the kind, how many there are when more than one, and the
  average speed of those running (`off` when none is). Green while any runs.
- **Three rows fit on a page.** Right-click turns the page; sneak-right-click
  switches auto-flip, which turns it every 5 seconds.
- A system can have any number of monitors.

## Signs in general

- Signs update only within about 64 blocks of a player. Farther signs keep
  their last text and catch up within a second of someone coming near.
- Every line is fitted to what the sign can show, hanging signs included. A
  header drops its `EZvac | ` prefix when the label needs the room, and a
  reading is never shown cut in half.
- Breaking a device tells you what was removed and from which system.
  Removing the last equipment warns that the thermostat will idle until
  equipment is linked again, and breaking a thermostat says its system is
  headless now; nothing else is deleted.

## Managing systems

```text
/hvac list                                   systems in your world
/hvac info                                   the device you're looking at
/hvac thermostat set|enable|disable|info     look at the thermostat
/hvac equipment enable|disable|info          look at the equipment
/hvac rename <label>                         the sign you're looking at
/hvac remove                                 the device you're looking at
/hvac remove system [confirm]                a whole system, holding the link tool
```

Full details in [Commands](commands.md). For how the controller, speeds and
capacity work exactly, see the *System model*, *Controller order and safety*
and *Thermal and motor performance* sections of [features.txt](features.txt).
