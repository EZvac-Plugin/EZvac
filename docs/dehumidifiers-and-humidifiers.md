# Dehumidifiers and humidifiers

Standalone units that dry or moisten the air. They need no system: inside a
system's room they work on that room, and anywhere else (a shed, a basement)
they work on the space around them, up to 30 blocks of airflow away by default,
the same reach as a vent.

## Dehumidifier

### Build

An **iron block on top of a waxed copper grate**, with a sign on the iron block,
either on its side or standing on top.

```text
 side view:

 [ iron  ][sign]      or        [sign]
 [ grate ]                      [iron ]
                                [grate]
```

Look at the sign and run:

```text
/hvac create dehumidifier [label]          ("dehum" works too)
```

The grate must not already be linked as a water vent.

<!-- screenshot: a dehumidifier with its sign, and one emptying a full tank -->

### Using it

```text
EZvac | basement
58% > 45%
Running
Tank 40%
```

Line 2 is the humidity of the space it works on and its setpoint. Line 3 is its
state: `Running`, `Idle`, `OFF`, `Emptying...` or `Air too cold`.

- **Right-click** the sign to open its control menu: the black pane raises the
  setpoint by 5%, the white one lowers it, from 35% to 60% (the lowest and
  highest of `dehumidifier.presets`; 30-50% for a humidifier). A lever in the
  menu switches it on or off.
- **Sneak-right-click** to switch it on or off. Off keeps the tank as it is.
- It starts 2% above the setpoint and runs until the air is down to it.
- The water it removes fills its **tank**. A full tank waterlogs the grate and
  the sign shows `Emptying...` for 30 seconds, then it carries on. The water
  never leaves the grate.
- Breaking the sign, the iron block or the grate removes it.

### "Air too cold"

`Air too cold` in place of `Running` means the air is already drier than its
coil can dry it: the dew point is at or below 35 °F, as in snowy biomes. Like a
real refrigerant dehumidifier it keeps running but removes nothing, and the
tank doesn't fill. `/hvac info` on it explains the same. It clears once the air
holds a little more moisture.

## Humidifier

### Build

A **waxed cut copper slab** (bottom half) on top of an **iron block**, with a
sign on the side of the iron block.

```text
 side view:

 [ slab ]
 [ iron ][sign]
```

Look at the sign and run:

```text
/hvac create humidifier [label]            ("hum" works too)
```

<!-- screenshot: a running humidifier with its bubbling slab -->

### Using it

```text
EZvac | nursery
32% > 40%
Running
Humidifier
```

- It works like a dehumidifier the other way round: it starts 2% **below** its
  setpoint and runs up to it. Setpoints step through 30, 35, 40, 45, 50%.
- Right-click and sneak-right-click the sign as for a dehumidifier.
- It is **plumbed**: it never runs dry and needs no refilling.
- While it runs, the slab fills with water and bubbles like water over soul
  sand. The water never leaves the slab.
- It adds moisture more slowly as the air nears saturation, and stops short of
  it.
- Breaking the sign, the iron block or the slab removes it.

## Several units in one space

Units outside a system's room that share air (one's reach covers the air around
the other) are one space with **one humidity**. A dehumidifier and a humidifier
in the same shed show the same value on both signs, the HUD and sensors, and
work on it together: what they do adds up, and the water the dehumidifiers
remove is shared between their tanks. Inside a system's room they already share
the room's humidity.

## Good to know

- One unit works at full strength in a space of up to 1,500 blocks, and
  proportionally slower in bigger ones. Add units for a big hall.
- `/hvac info` on a unit shows the space it works on, its humidity, setpoint
  and state, and a dehumidifier's tank.
- Using the sign needs `ezvac.use`; building and removing need `ezvac.admin`.
- Tuning is under `dehumidifier:` and `humidifier:`; see
  [Configuration](configuration.md).
- How humidity itself works: [Climate](climate.md#humidity).
