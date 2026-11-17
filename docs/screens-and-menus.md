# Screens and control menus

From 2.1, EZvac's signs get a floating **screen**, and thermostats,
dehumidifiers and humidifiers open a **control menu** when clicked. Since
BETA 1.0, admins also have a [quick settings](#quick-settings) menu for the
whole server.

## Screens

Every EZvac sign has a screen in front of it. Since BETA 1.0 Pilot 7 each is
a **panel exactly the size of its sign**, drawn on a fixed grid so that
everything sits in its own place, with buttons where a choice belongs on the
wall and parts that move smoothly while the device works. A mini thermostat's
panel is exactly its button's face.

- **The header** has the device's name, and on the right its state, coloured
  by what it's doing:

  | Header | Means |
  | --- | --- |
  | Blue ❄ | Cooling |
  | Orange ♨ | Heating |
  | Green ● | Holding the target, or running |
  | Grey ○ | Switched off, or stopped |
  | Amber ! | Needs you: no filter, a spent filter, air too cold to dry, a door left open |
  | The AQI's colour | An air monitor or purifier: green for Good, through yellow, orange, red and purple, to maroon for Hazardous |
  | Slate | Nothing to report, such as a thermometer |

  When something needs looking at, an amber **⚠** and a count follow the
  state. The screen doesn't spell out what: the device's menu (or
  `/hvac info`) lists it.
- **The main reading** is big. A reading too wide for its place (three
  digits, Celsius) is drawn a size smaller rather than ever cut off.
- **Bars** (a frost, a filter, a tank, a mini thermostat's progress) fill and
  drain smoothly, and an equipment monitor's icons turn at their equipment's
  speed.

| Device | Shows | Buttons |
| --- | --- | --- |
| Thermostat | A half-ring dial lit between the room and the target, the room temperature big, the target under it. A pool thermostat looks the same, with its own range | − and + for the target |
| Mini thermostat | Its state, the room temperature big in the colour of what it's doing, the target, and a bar filling as the room comes round | + and − |
| Thermometer, hygrometer, thermo-hygrometer | The reading big; feels-like, humidity and the outdoors beside it; the weather and season | |
| Air monitor | The AQI big, its band and what's behind it, pollen | |
| Dehumidifier, humidifier | The humidity big, the setpoint, what it's doing, a dehumidifier's tank | + and −, five points at a time |
| Air purifier | The AQI big, its band, its filter | L, M, H and Auto; the one in use is lit |
| Fridge, mini fridge, deep freezer | The inside in whole degrees, the setpoint; a mini fridge's chiller, a deep freezer's frost | + and −; a deep freezer's Defrost |
| Refrigerator | The fridge and the freezer side by side | − setpoint + for each |
| Equipment monitor | A row per kind of equipment in its system, each with its own icon and speed (`HP T3 ×2 2,950`, `Furnace T2 715`, `AC T1 off`), three to a page | < and > when there are more |
| Sensor hub | A line per sensor, three to a page | < and > when there are more |
| Settings panel | The system's profile | Eco, Normal and Turbo; the one in use is lit |
| Weather station | The outdoor temperature big, today's sky and high and low, the wind and air, then a note or tomorrow's sky | |

- **Buttons** do what the same button in the device's menu does, with the
  same permission (`ezvac.use`), and light up when pressed. Holding one down
  repeats it at a steady pace. Clicking the sign anywhere else opens the
  menu, as before; sneak-clicking switches the device, as before.
- **The sign behind is blank**, so only the screen shows.
- Screens light themselves, so they're readable at night.
- **A screen wakes as you walk up to it:** a panel is up only within 32
  blocks of a player and is taken down when everyone has gone, since each is
  many small parts. A server full of devices costs no more than with the
  classic screens.
- Screens update when someone is nearby, and only send what changed.
- Screens are never saved with the world. They disappear when their chunk
  unloads or the server stops, and come back when it loads again. Removing
  EZvac leaves nothing floating behind.
- Placed on wall signs, standing signs and hanging signs alike.
- `screens.new-look: false` in `config.yml` keeps the classic screens of
  BETA 1.0 Pilots 1 to 6: a status strip with the reading and a few lines,
  no buttons.

**Screens are the only display** (BETA 1.0 Pilot 4). Devices no longer write
on their signs. Bedrock players coming in through Geyser can't see screens.

## Control menus

Right-click a **thermostat** sign (room or pool), or press a mini
thermostat's button, to open its control menu:

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
- **Along the top** (BETA 1.0 Pilot 2):
  - **left, what needs attention:** each thing the system needs, with how to
    fix it: no units, no vent (it won't start without one), units switched off
    server-wide, losing more than it makes even flat out ("can't keep up"),
    or undersized for its building on its profile (and whether a faster
    profile would do). "All good" when there's
    nothing. The thermostat's screen counts them beside a ⚠;
  - **middle, the profile:** Eco, Normal or Turbo (see
    [Operating profiles](equipment.md#operating-profiles)). Click for the
    next, right-click for the previous. A settings panel linked to the
    system shows and sets the same one;
  - **right, the system:** its units and vents, and with building size on,
    its size, openings, and what it has of the units it needs for cooling and
    heating.

Right-click a **dehumidifier** or **humidifier** sign for its menu: the black
pane raises the setpoint by 5% and the white one lowers it, from the lowest to
the highest of its `presets` (35-60% for a dehumidifier, 30-50% for a
humidifier). The lever switches it off or on.

- Anyone with `ezvac.use` (everyone, by default) can use the menus. A menu
  works only within a few blocks of its sign: clicked from further away, it
  closes.
- Sneak-right-click still switches a thermostat's system, or a unit, off or on
  without opening anything.
- EZvac signs can't be edited. Before 2.1 a thermostat's target was
  typed on line 3 of its sign; the menu replaces that.
- Nothing can be taken out of or put into a menu.

## The setup

The first-start setup (BETA 1.0 Pilot 10), from the chat prompt a new install
gives admins or `/hvac setup`: nine pages in a small chest, titled
**Setup 3/9 · Wind** and so on. **Back** is bottom left, **Skip setup** in the
middle (it marks the setup done) and **Next** bottom right. On each page the
current choice is lime and glinting; clicking another applies and saves it at
once.

| Page | Choices |
| --- | --- |
| Welcome | What EZvac is; a book that sends the Getting started link |
| Temperatures | °F or °C |
| Wind | Follow the temperatures, m/s, km/h or mph |
| HUD | For new players: off, sidebar, action bar or boss bar; **Rename the title** asks for a name in chat |
| Updates | Stable, Pilot, or follow this build |
| Backups | Off, every 12 hours, every 24 hours or weekly |
| Statistics | Anonymous statistics on or off, with what they are and why they help |
| Addons | The official addons: installed or not, a click sends the link. Folded, legacy addons aren't offered |
| Done | A summary of the choices, **Build your first system** (the [tutorial](getting-started.md#the-tutorial)) and **Finish** |

Closing the menu partway keeps what was chosen; the next join asks to carry
on.

## Quick settings

New in BETA 1.0. `/hvac quicksettings` (or `/hvac qs`) opens a menu for the
settings that shape the whole server, so an owner can choose how much of
EZvac runs without editing `config.yml` or restarting. It needs
`ezvac.admin`.

The main page has eight sections in two rows (regrouped in BETA 1.0 Pilot
4): **Devices**, **Climate systems**, **Seasons & wind** and **Display &
sound** above, and **Addons**, **Backups & registry**, **Health** and
**Updates** below. Each opens its own page, with **Back** in the bottom-left
corner to return and **Close** in the bottom-right.

- **Every change applies at once** and is written to `config.yml`, comments
  and all, so the file and the menu always agree. Editing the file by hand
  still works; it takes effect at the next start.
- **Every change is logged,** with who made it, for example
  `bladestech switched heat pumps off server-wide (/hvac quicksettings).`
- A switch is an icon with a lime or red dye under it: click either.

### Devices

One switch for each kind that changes the climate: heat pumps, air
conditioners, furnaces, boilers, pool heat pumps, dehumidifiers, humidifiers
and air purifiers, and one for [weather stations](weather-stations.md); on a
second row, mini fridges, fridges, refrigerators and deep freezers (BETA 1.0
Pilot 7). Each
shows how many the server has and how many run.

- **Switched off,** every device of that kind stops, and equipment takes its
  water or lava away, as at any stop. Its sign reads **Off server-wide**, and
  `/hvac info` says why.
- **Its thermostat waits:** a system whose only units for heat (or cooling)
  are switched off doesn't call for them. Its screen reads **Heat to 72.0°F**
  (or **Cool to**) and **Units off** instead of an ETA, the HUD says **Units off**,
  and `/hvac info` on the thermostat says why. A system with another kind
  that can do the job carries on with that.
- **No new one can be made:** `/hvac create` says that kind is switched off.
  Existing ones can still be linked and removed.
- **Switched back on,** each runs again as it was set: a device someone had
  switched off itself stays off.
- **Weather stations** switched off show **Off server-wide**, and
  `/hvac forecast` says forecasts are off.

In `config.yml` this is [`devices.disabled`](configuration.md#devices).

### Climate systems

| Switch | Off means |
| --- | --- |
| **Air quality** | No air readings, and air purifiers sit idle. Pollen goes with it |
| **Pollen** | No pollen outdoors or in, and readings say pollen is "Off". The AQI never counted pollen, so it doesn't change |
| **Wind** | No daily wind, no drafts through openings and no wind chill: "feels like" only has the weather's light breeze |
| **Seasons** | No seasonal warmth, cold or pollen. The calendar keeps counting, so switched back on, it's the season the date has reached |
| **Building size** | Any size heats and cools as before BETA 1.0: no [sizing](equipment.md#sizing), the air spills 30 blocks outdoors, and neighbours don't even out. Switching it maps every system again |

EZvac keeps heating, cooling and drying with any of them off. They're
`air-quality.enabled`, `air-quality.pollen`, `wind.enabled`,
`seasons.enabled` and `sizing.enabled` in `config.yml`.

While EZvac is stopped because `hvac.yml` can't be loaded, `/hvac quicksettings`
opens a recovery menu instead: salvage the file, or restore a backup. See
[When hvac.yml is broken](upgrading-and-troubleshooting.md#when-hvacyml-is-broken).

### Backups & registry

- **Back up now** copies the registry (`hvac.yml`, with `seasons.yml` beside
  it) to `plugins/EZvac/backups/`, as `hvac-<date>_<time>-manual.yml`. A
  backup made by hand is kept until you delete it.
- **Automatic** steps through Off, every 4, 8, 12, 24 or 48 hours, and every
  week. It's every 24 hours unless you change it (BETA 1.0 Pilot 2). It counts real time, through restarts: a server that was down longer
  than the interval backs up a minute after it starts. Only the newest 10
  automatic backups are kept (`backups.keep`).
- The third item shows the newest backup, how long ago it was made, and how
  many there are.
- **hvac.yml** (beneath them) shows when it was last saved and how it read at
  this start: fine, upgraded from an older format, or recovered from a backup.
  With entries kept aside it lists them; click it, then click again, to
  discard them for good, as `/hvac registry repair --force` does (`hvac.yml`
  is copied aside first). See
  [When hvac.yml is broken](upgrading-and-troubleshooting.md#when-hvacyml-is-broken).

To restore one, see
[Restoring a backup](upgrading-and-troubleshooting.md#restoring-a-backup).

### Seasons & wind

New in BETA 1.0 Pilot 4. For the world you stand in (open it from another
world to change that one):

- **The season and day,** with **A day back**, **A day on**, **Next season**
  and **Pause the calendar**: what `/hvac season set`, `next` and `pause` do.
- **Today's wind:** each click sets it to calm, breezy, windy, a gale, and
  then back to blowing as it would, like `/hvac wind set`.

### Display & sound

New in BETA 1.0 Pilot 4. All three apply at once, on every screen and HUD:

- **Temperatures** in °F or °C (`units`). Setpoints keep their temperature.
- **Wind speeds** in mph, km/h or m/s, or auto: mph with °F, km/h with °C
  (`wind.units`).
- **Device sounds** on or off (`audio.enabled`).

### Addons

New in BETA 1.0 Pilot 4; `/hvac addons` opens it directly.

- **The top row is what's installed:** EZvac's official addons on this
  server, each with its version and a switch beneath it. Switched off, an
  addon stays loaded but does nothing, until it is switched on again; the
  choice is kept through restarts.
- **Parts:** an addon with parts says how many are on. Click its icon for a
  page with a switch for each; they only run while the addon itself is on.
  EZclimates has seasonal looks, snow and ice, planned weather, plants, and body
  temperature (off until you switch it on).
- **An addon from before this menu** runs, but says it needs updating to be
  switched here.
- **The bottom row is the store:** the official addons this server doesn't
  have, with what each does and needs. A click closes the menu and puts the
  addon's GitHub link in chat. EZvac never downloads or installs anything.
- **Updates:** an installed addon with a newer release says so (shift-click
  for the link), and admins are told when they join. **Check now** asks
  GitHub at once; otherwise it follows `updates.check`.

### EZdebug, built in

New in BETA 1.0 Pilot 5. Listed first on the Addons page, with its switch.
While you hold the link tool with a thermostat's system selected, you see
that system as EZvac does:
- its equipment glows through walls, its signs are framed, and its vents are marked;
- its vents puff while they work, with a column rising out of them, cyan while cooling and orange while heating;
- the edge of the space its air fills is dotted in near you;
- the openings it leaks through (doorways, windows, holes) glow red, through walls.

Only you see it. With the EZdebug+ addon installed and switched on, it
stands down: EZdebug+ shows all of this, for every system at once, and
more.

### Health

Called Statistics before BETA 1.0 Pilot 4. **Tick cost** says what each part
of EZvac took of the server's 50 ms tick over the last minute: the
controller, screens, airflow, the HUD, weather stations and open-air smoke,
each averaged over every tick, with its longest single run.

It also has what `/hvac stats` reports, written out: each kind of device with how many
run, the thermostats and other signs, and the airflow figures. When
`hvac.yml` was last saved moved to the Backups & registry page in BETA 1.0. **Sizes** lists each system's size and what it has
of the units it needs, for cooling and heating, the worst sized first.
**Refresh** reads them again.

### Updates

This server's EZvac and Minecraft, **Check now** (the same as `/hvac update`,
answered in chat), and switches for the background checks and for telling
admins when they join (`updates.check` and `updates.notify-admins`).

**Channel** (under the version, BETA 1.0 Pilot 2) is which releases the
server hears about: **Stable**, full releases and their hotfixes, or
**Pilot**, pilots too. Click to step through auto, stable and pilot; auto
follows the EZvac the server runs. It's `updates.channel` in `config.yml`.

### Anonymous statistics

What EZvac sends to bStats (BETA 1.0 Pilot 9), and the switches for it. The
paper at the top lists what bStats always sends while statistics are on.
**Anonymous statistics** switches them all on or off; the four beside it
switch EZvac's own charts one by one: **Temperature units**, **Unloaded
devices**, **Number of systems** (in a band) and **Official addons** (which
run, and their versions). Each takes effect at once. See
[metrics](configuration.md#metrics).
