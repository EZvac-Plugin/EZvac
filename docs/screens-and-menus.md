# Screens and control menus

From 2.1 Pilot 3, EZvac's signs get a floating **screen**, and thermostats,
dehumidifiers and humidifiers open a **control menu** when clicked.

## Screens

Every EZvac sign has a screen in front of it: a dark, lit panel with more room
than a sign's four lines. All screens are the same size, so a wall of devices
lines up.

| Device | Shows |
| --- | --- |
| Thermostat | Its name and group, the room temperature, what it's doing (`❄ Cooling to 70.0°F`, `♨ Heating to`, `● Holding`), a capacity bar, the ETA and the room's humidity, the outdoor temperature and season |
| Thermometer, hygrometer, thermo-hygrometer | The reading, feels-like, the outdoors, the weather and season |
| Dehumidifier, humidifier | The humidity, the setpoint, its state, and a dehumidifier's tank |
| Sensor hub | Up to five sensors at once; with more, it pages along with the sign |
| RPM monitor | The RPM, a capacity bar and how many units are running |
| Settings panel | The profile, and how to change it |

- **The sign stays behind the screen.** Clicking works exactly as before, and
  players whose client can't show screens (such as Bedrock players coming in
  through Geyser) still read the sign.
- Screens light themselves, so they're readable at night.
- Like signs, screens update when someone is nearby, and only when what they
  show has changed.
- Screens are never saved with the world. They disappear when their chunk
  unloads or the server stops, and come back when it loads again. Removing
  EZvac leaves nothing floating behind.
- Placed on wall signs, standing signs and hanging signs alike.

Turn them off with `displays.screens: false` in `config.yml`; the signs alone
then show everything, as before.

## Control menus

Right-click a **thermostat** sign (room or pool) to open its control menu:

| | Raise | Lower |
| --- | --- | --- |
| Top row | Black pane: +5 | White pane: -5 |
| Middle row | Green pane: +1 | Red pane: -1 |
| Bottom row | Lime pane: +0.1 | Orange pane: -0.1 |

- Steps are in the server's `units`: degrees Fahrenheit, or degrees Celsius
  with `units: C`. The target is kept to a tenth of a degree, so ten +0.1
  presses are exactly one degree.
- The target never goes past the allowed range: 60-85 °F for a room, 75-104 °F
  for a pool (`temperature:` in `config.yml`). A button that can't go further
  plays a low note.
- In the middle: the target, the room's temperature and humidity, the allowed
  range, and a **lever** that switches the system off or on.

Right-click a **dehumidifier** or **humidifier** sign for its menu: the black
pane raises the setpoint by 5% and the white one lowers it, from the lowest to
the highest of its `presets` (35-60% for a dehumidifier, 30-50% for a
humidifier). The lever switches it off or on.

- Anyone with `ezvac.use` (everyone, by default) can use the menus. A menu
  works only within a few blocks of its sign: walk away and it closes.
- Sneak-right-click still switches a thermostat's system, or a unit, off or on
  without opening anything.
- EZvac signs can't be edited. Before 2.1 Pilot 3 a thermostat's target was
  typed on line 3 of its sign; the menu replaces that.
- Nothing can be taken out of or put into a menu.
