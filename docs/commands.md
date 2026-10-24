# Commands and permissions

Every command starts with `/hvac`; `/climate` works too. Commands that act on a
block use the block you're looking at, within six blocks.

## Permissions

| Permission | Default | Allows |
| --- | --- | --- |
| `ezvac.use` | everyone | help, readings, `/hvac list` and `info`, and using signs: thermostat on/off, settings panels, dehumidifiers and humidifiers, sensor hubs, pressing sensors |
| `ezvac.hud` | everyone | `/hvac hud` |
| `ezvac.admin` | operators | creating, changing, linking and removing devices, `stats` and `sync` |

## For everyone (`ezvac.use`)

| Command | Does |
| --- | --- |
| `/hvac help` | Lists the commands you can use |
| `/hvac temperature` (`temp`) | The temperature where you stand, the outdoor temperature and weather, the humidity and "feels like" in air, and which system reaches you (core or edge, and how far along its airflow). In water: the water temperature and the air above |
| `/hvac hud [on\|off]` | Shows or hides the on-screen HUD for yourself (`ezvac.hud`). With no argument it toggles; your choice is remembered |
| `/hvac list` | The systems in your world, with mode, room and target temperature, and how many units are running |
| `/hvac info` | Details of the device you're looking at: a thermostat (with its room's humidity and feels-like), equipment, a vent, a thermometer, an RPM monitor, a settings panel, a dehumidifier or humidifier, a sensor or a sensor hub |

### Using signs and sensors

- **Thermostat:** sneak-right-click switches its system off or on.
- **Settings panel:** right-click steps Normal → Turbo → Eco → Normal;
  sneak-right-click goes the other way.
- **Dehumidifier / humidifier:** right-click steps the setpoint; sneak-right-click
  switches it on or off.
- **Sensor hub:** right-click turns the page; sneak-right-click switches
  auto-flip on or off.
- **Sensor:** right-click shows its reading in chat.

## Creating devices (`ezvac.admin`)

| Command | Look at | Notes |
| --- | --- | --- |
| `/hvac create thermostat <id> <group> [targetF]` | a sign | One per group. Target 60-85 °F, default 72 |
| `/hvac create poolthermostat <id> <group> [targetF]` | a sign | Also `pool`. Target 75-104 °F, default 82. Only for boilers and pool heat pumps |
| `/hvac create heatpump <group>` | a dispenser | Cools and heats |
| `/hvac create ac <group>` | a dispenser | Cools |
| `/hvac create furnace <group>` | a dispenser | Heats, at full power |
| `/hvac create boiler <group>` | a dispenser | Heats a pool |
| `/hvac create poolheatpump <group>` | a dispenser | Also `poolhp`. Heats and cools a pool |
| `/hvac create thermometer [label]` | a sign | |
| `/hvac create hygrometer [label]` | a sign | |
| `/hvac create thermohygrometer [label]` | a sign | Also `thermohygro` |
| `/hvac create rpmmonitor <group> <heatpump\|ac\|furnace\|boiler\|poolheatpump> [label]` | a sign | That kind of equipment must already be in the group |
| `/hvac create settingspanel <group> [label]` | a sign | One per group. The group must already have a thermostat or equipment |
| `/hvac create dehumidifier [label]` | its sign | Also `dehum`. A sign on an iron block that sits on a waxed copper grate |
| `/hvac create humidifier [label]` | its sign | Also `hum`. A sign on the side of an iron block under a waxed cut copper slab |
| `/hvac create sensor [label]` | a button | Stone, polished blackstone, mangrove or birch |
| `/hvac create sensor-hub [label]` | a sign | Also `sensorhub` |

IDs and group names are 1-32 letters, numbers, `_` or `-`. A typed temperature
is in the server's `units`; add `F` or `C` (`72F`, `22C`) to use the other one. Build guides:
[Equipment](equipment.md), [Dehumidifiers and humidifiers](dehumidifiers-and-humidifiers.md),
[Sensors](sensors.md).

## Linking tools (`ezvac.admin`)

**`/hvac duct-tool`** (`/hvac tool` still works) gives the duct link tool:

1. Right-click a registered dispenser to select its group.
2. Right-click iron trapdoors (air vents) or waxed copper grates in water (water
   vents) in the same world to link them.
3. Sneak-right-click a vent to unlink it.

A system is all air or all water, and the vents must match its equipment. See
[Vents and airflow](airflow.md).

**`/hvac sensor-tool`** gives the sensor link tool (it glints):

1. Right-click a sensor hub to select it.
2. Right-click sensors to link them: at most nine, in the same world.
3. Sneak-right-click a sensor to unlink it.

## Thermostats, equipment and groups (`ezvac.admin`)

| Command | Does |
| --- | --- |
| `/hvac thermostat <id> set <temperatureF>` | Sets the target, kept within the allowed range |
| `/hvac thermostat <id> enable` / `disable` | Switches the system on or off |
| `/hvac thermostat <id> group <group>` | Moves the thermostat to another group in the same world that has none |
| `/hvac thermostat <id> info` | Room and target temperature, mode, group, and whether it's on |
| `/hvac equipment enable` / `disable` / `group <group>` / `info` | The equipment you're looking at. Moving it stops it and cleans up its output first |
| `/hvac group <group> enable` / `disable` / `info` | A whole system in your world |

Thermostat signs can also be edited: put a temperature on line 3 to set the
target, such as `22`, or `72F` in the other unit.
Other EZvac signs can't be edited.

## Removing and diagnostics (`ezvac.admin`)

| Command | Does |
| --- | --- |
| `/hvac remove` | Removes the device you're looking at. Equipment takes its water or lava away (later, if that chunk isn't loaded). Removing a settings panel returns its group to Normal; removing a sensor takes it off every hub |
| `/hvac stats` | Device totals and airflow statistics |
| `/hvac sync` | Applies block and world changes straight away instead of on the next update |

Breaking a device's block removes it too, and tells you what was removed.
