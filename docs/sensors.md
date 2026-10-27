# Sensors and readings

Ways to see the climate somewhere without standing there: signs that measure
their own spot, and remote sensors that report to a sensor hub.

Every reading is taken the same way as `/hvac temperature` at that spot. A spot
deep in a system's airflow reads the room, one near the edge a blend, and one
outside every system the outdoors.

## Thermometers, hygrometers, thermo-hygrometers and air monitors

Look at a sign and run one of:

```text
/hvac create thermometer [label]
/hvac create hygrometer [label]
/hvac create thermohygrometer [label]      ("thermohygro" works too)
/hvac create airmonitor [label]
```

```text
Thermometer         Hygrometer          T. Hygrometer
Thermometer         Hygrometer          In 72.1 F 58%
Inside: 72.4 F      Inside: 58%         Feels 73.0 F
Outside: 64.0 F     Outside: 41%        Out 64.0 F 41%
```

- **Thermometer:** the temperature where the sign is, and outdoors.
- **Hygrometer:** the humidity where the sign is, and outdoors.
- **Thermo-hygrometer:** temperature, humidity and "feels like" together.
- **Air monitor (3.2):** the Air Quality Index, coloured by its band, with
  what drives it when it isn't Good (`AQI 88 Smoke`), and the pollen. See
  [Air quality](air-quality.md).

They belong to no system and can go anywhere, outdoors too. Breaking the sign
removes them.

## Sensors and sensor hubs

A **sensor hub** is a sign that shows up to nine **sensors**, three to a page.
A sensor is a **button**, and the button decides what it measures:

| Button | Measures |
| --- | --- |
| Stone button | Temperature |
| Polished blackstone button | Humidity |
| Mangrove button | Temperature and humidity |
| Birch button | Weather: Clear, Rain, Thunder or Snow |
| Cherry button | Air quality: the AQI (3.2) |
| Lightning rod | Wind: `Gale 61km/h` (3.2). Not a button, but it works the same way |

### Build and link

```text
/hvac create sensor [label]        look at the button
/hvac create sensor-hub [label]    look at a sign ("sensorhub" works too)
/hvac link-tool                    gives the link tool ("sensor-tool" works too)
```

With the link tool:

1. Right-click a sensor hub to select it.
2. Right-click sensors to link them: at most nine, in the same world.
3. Sneak-right-click a sensor to unlink it.

One sensor can be on several hubs.

<!-- screenshot: a sensor hub sign and a few button sensors around a house -->

### Using a hub

```text
Home 1/3
Kitchen 72F 45%
Cellar 58%
Garden Rain
```

- **Right-click** the hub to turn the page.
- **Sneak-right-click** it to switch auto-flip on or off: it turns the page
  every 5 seconds and remembers that across restarts.
- **Right-click a sensor** to see its full reading in chat. Sensors never send a
  redstone signal, even when an arrow hits a wooden one.
- A sensor in an unloaded chunk shows `--`.
- `/hvac info` on a hub lists every sensor with its reading.

### Weather sensors

A birch button works like a rain gauge: it needs **open sky** above it. Under a
roof, glass or leaves it shows `Covered`. Where weather never reaches, such as a
desert, it shows `Clear`. It reports the sky even if EZvac's weather effects
are switched off.

### Removing

Breaking a sensor, or the block it hangs on, takes it off every hub. Breaking a
hub's sign removes the hub. `/hvac remove` works on both.

## Good to know

- Readings vary a little from spot to spot, the way a real house does; see
  [Climate](climate.md#spot-to-spot-variance).
- Signs update only near players; see [Equipment](equipment.md#signs-in-general).
- Reading and paging need `ezvac.use`; creating and linking need `ezvac.admin`.
