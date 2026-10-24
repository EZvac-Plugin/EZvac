# Climate

How EZvac works out the temperature and humidity of the outdoors, of rooms and
of water. You don't need any of this to build a system, but it explains the
numbers you see.

## Outdoor temperature

The outdoors follows the time of day and the biome:

| Biome type | Noon | Midnight |
| --- | ---: | ---: |
| Snowy | 25 °F | 5 °F |
| Cold | 45 °F | 28 °F |
| Temperate | 72 °F | 52 °F |
| Warm | 85 °F | 68 °F |
| Hot | 100 °F | 78 °F |
| Nether | 112 °F | 112 °F |
| End | 30 °F | 30 °F |

A room with nothing running drifts toward the outdoor temperature, so a house
in a desert warms up on its own and one in the snow cools down.

## Weather

- Rain and thunder cool the outdoor air, by 6 °F and 9 °F by default, and
  narrow the day/night swing; snow cools by 3 °F.
- Vanilla weather switches in an instant, so the effect eases in and out over
  about two in-game hours.
- Only biomes that get rain or snow react. Deserts, savannas, badlands, the
  Nether and the End never do; snowy biomes get snow.
- Everything that uses the outdoor temperature follows: rooms drifting with
  nothing running, ETAs, thermometers, sensors, the HUD and pools. Natural water
  barely reacts (10% of the air's change).
- Wind (stronger in rain and thunder) makes the outdoors feel colder.

## Humidity

Moisture is simulated as a **dew point** and shown as **relative humidity**
(RH), the way real thermostats do it.

- **Outdoors** it comes from the biome: about 30% in a desert, 54% on plains
  and 84% in a jungle or swamp at the daily mean. It rises at night as the air
  cools, and climbs toward 100% in rain.
- **Indoors**, each air system's room drifts toward the outdoor moisture.
  Cooling dries the air, heating lowers the RH (dry winter air), and water in a
  room evaporates into it, faster when the water is warm: a heated pool hall
  gets muggy.
- **Dehumidifiers and humidifiers** change it on purpose; see
  [Dehumidifiers and humidifiers](dehumidifiers-and-humidifiers.md).
- RH never goes above 100%: anything more condenses out.

## Feels like

"Feels like" is the apparent temperature from temperature, humidity and wind:
humid heat feels hotter, dry air and wind feel cooler. Indoors there is no
wind; at the edge of a system's reach the outdoor wind blends in.

The HUD's comfort word, bar and colour follow "feels like" in air, so a humid
84 °F room reads *Hot*. In water they follow the water temperature.

## Water

Water keeps its own temperature. Water no pool system reaches (lakes, the sea,
an unheated pool) reads the biome's **natural water temperature**: the biome's
daily average pulled toward mild values, with no day/night swing and never
below 33 °F. A heated pool's edge blends toward that, and an idle pool drifts
toward it the way an idle room drifts toward the outdoor air.

## Spot-to-spot variance

Readings differ a little from place to place, so a house isn't one number from
wall to wall:

| Where | Up to |
| --- | --- |
| A heated or cooled room | ±0.5 °F |
| Outdoors | ±1.5 °F |
| A pool | ±0.3 °F |
| Lakes, rivers and the sea | ±0.8 °F |

- Moisture stays nearly even, so a warmer spot reads a little drier.
- The difference builds gradually over a few blocks and drifts over a few
  in-game hours, so readings never flicker.
- Only **readings** vary: thermometers, sensors, the HUD, `/hvac temperature`
  and placeholders. The thermostat and its sign keep the room's own value, so a
  system never chases the variance.

## Tuning

Everything here is adjustable under `weather:`, `humidity:` and `variance:` in
`config.yml`; see [Configuration](configuration.md). The exact formulas are in
the *Outdoor climate* and *Humidity and feels like* sections of
[features.txt](features.txt).
