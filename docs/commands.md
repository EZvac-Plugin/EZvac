# Commands and permissions

Every command starts with `/hvac`; `/climate` works too. Commands that act on a
block use the block you're looking at, within six blocks.

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `ezvac.use` | everyone | help, readings, `/hvac list` and `info`, and using signs: thermostat targets and on/off, settings panels, dehumidifiers, humidifiers and air purifiers, sensor hubs, pressing sensors |
| `ezvac.hud` | everyone | `/hvac hud` |
| `ezvac.admin` | operators | creating, changing, linking and removing devices, changing the season and the wind, `stats` and `sync` |

## For everyone (`ezvac.use`)

| Command | Does |
| --- | --- |
| `/hvac help` | Lists the commands you can use |
| `/hvac temperature` (`temp`) | The temperature where you stand, the outdoor temperature and weather, the humidity and "feels like" in air, and which system reaches you (core or edge, and how far along its airflow). In water: the water temperature and the air above |
| `/hvac hud [on\|off]` | Shows or hides the on-screen HUD for yourself (`ezvac.hud`). With no argument it toggles; your choice is remembered |
| `/hvac list` | The systems in your world, with mode, room and target temperature, and how many units are running |
| `/hvac season` | The season and its day ("Summer, day 3 of 15"), and how much it warms or cools the outdoors where you stand |
| `/hvac wind` | Today's wind ("Windy, about 22 km/h"), the wind where you stand and out in the open, and the next two days' (3.2) |
| `/hvac info` | Details of the device you're looking at: a thermostat (with its room's humidity, feels-like and air), equipment, a vent, a thermometer, an RPM monitor, a settings panel, a dehumidifier, humidifier or air purifier, a sensor or a sensor hub |

### Using signs and sensors

- **Thermostat:** right-click opens its control menu to change the target;
  sneak-right-click switches its system off or on.
- **Settings panel:** right-click steps Normal → Turbo → Eco → Normal;
  sneak-right-click goes the other way.
- **Dehumidifier / humidifier:** right-click opens its control menu (setpoint
  +5% / -5%, and on/off); sneak-right-click switches it on or off.
- **Air purifier:** right-click opens its control menu (Low, Medium, High or
  Auto, and on/off); sneak-right-click switches it on or off.
- **Sensor hub:** right-click turns the page; sneak-right-click switches
  auto-flip on or off.
- **Sensor:** right-click shows its reading in chat.

## Creating devices (`ezvac.admin`)

| Command | Look at | Notes |
| --- | --- | --- |
| `/hvac create thermostat <label> [target]` | a sign | Target 60-85 °F, default 72. Labels may repeat |
| `/hvac create poolthermostat <label> [target]` | a sign | Also `pool`. Starts in the pool range: 75-104 °F, default 82 |
| `/hvac create heatpump [t1\|t2\|t3]` | a dispenser | Cools and heats |
| `/hvac create ac [t1\|t2\|t3]` | a dispenser | Cools |
| `/hvac create furnace [t1\|t2\|t3]` | a dispenser | Heats, at full power |
| `/hvac create boiler [t1\|t2\|t3]` | a dispenser | Heats a pool, at full power |
| `/hvac create poolheatpump [t1\|t2\|t3]` | a dispenser | Also `poolhp`. Heats and cools a pool |
| `/hvac create thermometer [label]` | a sign | |
| `/hvac create hygrometer [label]` | a sign | |
| `/hvac create thermohygrometer [label]` | a sign | Also `thermohygro` |
| `/hvac create airmonitor [label]` | a sign | Shows the AQI and pollen where it stands (3.2) |
| `/hvac create rpmmonitor [label]` | a sign | Shows its system's equipment once linked |
| `/hvac create settingspanel [label]` | a sign | One per system, once linked |
| `/hvac create dehumidifier [label]` | its sign | Also `dehum`. A sign on an iron block that sits on a waxed copper grate |
| `/hvac create humidifier [label]` | its sign | Also `hum`. A sign on the side of an iron block under a waxed cut copper slab |
| `/hvac create purifier [label]` | its sign | Also `airpurifier`. A sign on an iron block that sits on a tube coral block, its filter (3.2) |
| `/hvac create sensor [label]` | a button, or a lightning rod | Stone, polished blackstone, mangrove, birch or cherry (air quality); a lightning rod reads the wind (3.2) |
| `/hvac create sensor-hub [label]` | a sign | Also `sensorhub` |

An equipment tier (`t1`, `t2` or `t3`) is optional: two-stage unless given.
Equipment, settings panels and RPM monitors are linked to a thermostat with the
link tool, below. Holding the tool with a thermostat selected while you create
one links it straight away. Labels are 1-32 letters, numbers, `_` or `-`. A
typed temperature is in the server's `units`; add `F` or `C` (`72F`, `22C`) to
use the other one. The pre-3.0 forms with a group name still work: the group
is ignored, with a note. Build guides:
[Equipment](equipment.md), [Dehumidifiers and humidifiers](dehumidifiers-and-humidifiers.md),
[Air quality](air-quality.md),
[Sensors](sensors.md).

## The link tool (`ezvac.admin`)

**`/hvac link-tool`** gives the link tool, a glinting stick. `/hvac duct-tool`,
`/hvac sensor-tool` and `/hvac tool` give it too, and the duct and sensor tools
from before 3.0 work as link tools.

| Click | Does |
| --- | --- |
| Right-click a thermostat | Selects it |
| Right-click a dispenser, a vent block, a settings panel or an RPM monitor | Links it to the selected thermostat. Something in a headless system brings the whole system along; something that belongs to another thermostat is refused |
| Right-click a sensor hub, then sensors | Links the sensors to the hub: at most nine, in the same world |
| Sneak-right-click a device | Unlinks it: a unit gets a headless system of its own, a vent is unregistered, a sign shows "Not linked" |
| Sneak-right-click a thermostat | Unlinks the thermostat; its system carries on headless |

A system is all air or all water, and the vents must match its equipment. See
[Equipment](equipment.md#the-link-tool) and [Vents and airflow](airflow.md).

## Thermostats and equipment (`ezvac.admin`)

These act on the device you're looking at: labels may repeat, so a thermostat
isn't found by name.

| Command | Does |
| --- | --- |
| `/hvac thermostat set <temperature>` | Sets the target, kept within the allowed range |
| `/hvac thermostat enable` / `disable` | Switches the system on or off |
| `/hvac thermostat info` | Room and target temperature, mode, what's linked, and whether it's on |
| `/hvac equipment enable` / `disable` / `info` | The equipment you're looking at |
| `/hvac equipment tier <t1\|t2\|t3>` | Changes its tier: ★☆☆ single-stage, ★★☆ two-stage, ★★★ variable (also `1`-`3`, `single`, `two-stage`, `variable`, `modulating`). See [Tiers](equipment.md#tiers) |
| `/hvac rename <label>` | Renames the sign you're looking at: a thermostat, RPM monitor, settings panel, sensor hub, thermometer, sensor, or dehumidifier, humidifier or air purifier sign |

The control menu a thermostat's sign opens:

| | Raise | Lower |
| --- | --- | --- |
| Top row | Black pane: +5 | White pane: -5 |
| Middle row | Green pane: +1 | Red pane: -1 |
| Bottom row | Lime pane: +0.1 | Orange pane: -0.1 |

Steps are in the server's `units`: degrees Fahrenheit, or Celsius with
`units: C`. The target never goes past the allowed range (60-85 °F for a room,
75-104 °F for a pool). The middle shows the target, the room's temperature and
humidity, and a lever that switches the system on or off.

EZvac signs can't be edited; before 2.1, a thermostat's target was
typed on line 3 of its sign.

## Seasons

Anyone can look with `/hvac season`; changing it needs `ezvac.admin`. Each
world with a sun has its own calendar (see [Climate](climate.md#seasons)).

| Command | Does |
| --- | --- |
| `/hvac season set <spring\|summer\|autumn\|winter> [day]` | Jumps to that season, on day 1 or the day given. `fall` works too |
| `/hvac season next` | Jumps to the first day of the next season |
| `/hvac season pause` / `resume` | Stops the calendar, or starts it again. While paused the days pass but the season stays |

Outdoor temperatures take on the new season at once; rooms drift there over
time, as they do when the weather changes. The commands act on your own world;
from the console, add a world's name at the end (it defaults to the main world).

## Wind

Anyone can look with `/hvac wind`; setting it needs `ezvac.admin`. Only worlds
with a sky have wind (see [Wind](wind.md)).

| Command | Does |
| --- | --- |
| `/hvac wind set <calm\|breezy\|windy\|gale>` | Holds today's wind at that level until the day ends, or the season calendar moves. It isn't saved |
| `/hvac wind set auto` | Lets the wind blow as it would again |

As with the seasons, from the console add a world's name at the end.

## Removing and diagnostics (`ezvac.admin`)

| Command | Does |
| --- | --- |
| `/hvac remove` | Removes the device you're looking at. Equipment takes its water or lava away (later, if that chunk isn't loaded). Removing a settings panel returns its system to Normal; removing a thermostat leaves its system headless; removing a sensor takes it off every hub |
| `/hvac remove system` | Holding the link tool, looking at any part of a system: says what it would remove. `/hvac remove system confirm` within 15 seconds removes every device of it. The blocks stay |
| `/hvac stats` | Device totals and airflow statistics |
| `/hvac hud sections` | The sections addons have added to the HUD, with the `{section:<id>}` token for each (see [HUD and placeholders](hud-and-placeholders.md#addon-sections)) |
| `/hvac sync` | Applies block and world changes straight away instead of on the next update |

Breaking a device's block removes it too, and tells you what was removed.
