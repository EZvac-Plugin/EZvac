# Screens and control menus

From 2.1, EZvac's signs get a floating **screen**, and thermostats,
dehumidifiers and humidifiers open a **control menu** when clicked. Since
BETA 1.0, admins also have a [quick settings](#quick-settings) menu for the
whole server.

## Screens

Every EZvac sign has a screen in front of it, with more room than a sign's
four lines. Since BETA 1.0 each screen is a **status strip**:

- **A header** with the device's name, coloured by what it's doing, with a
  glyph at its right end:

  | Header | Means |
  | --- | --- |
  | Blue ❄ | Cooling |
  | Orange ♨ | Heating |
  | Green ● | Holding the target, or running |
  | Grey ○ | Switched off |
  | Amber ! | Needs you: no filter, a spent filter, air too cold to dry |
  | The AQI's colour ● | An air monitor or purifier: green for Good, through yellow, orange, red and purple, to maroon for Hazardous |
  | Slate | Nothing to report, such as a thermometer |

- **An opaque body** under it, with the main reading big and centred, and a
  few lines under that.

All screens are the same size, so a wall of devices lines up.

| Device | Shows |
| --- | --- |
| Thermostat | Its name (and "Pool" for a pool thermostat), the room temperature big, what it's doing (`Cooling to 70.0°F`, `Heating to`, `Holding`), a load bar (how hard it's working, 0-100%), the ETA and the room's humidity, the outdoor temperature and season (the season is left off on a day too hot to fit both). An amber **⚠2** beside the header's glyph counts what needs attention, which its menu lists (BETA 1.0 Pilot 2); what keeps it from starting is said in red instead of what it's doing (`Needs a vent`, `Needs a unit`, `Units off`) |
| Mini thermostat | On its wall button (BETA 1.0 Pilot 2): the room temperature big in whole degrees, coloured by what it's doing, the target (`Set 85°`), and a gauge of thin bars of how far the room has come toward it since it started heating or cooling; a green dot while it holds. `⚠2` follows the target when something needs attention, and a short reason (`No vent`) replaces the gauge when something stops it. No outdoor readings or season |
| Thermometer, hygrometer, thermo-hygrometer | The reading, feels-like, the outdoors, the weather and season |
| Air monitor | The AQI, its band, what's behind it, pollen |
| Dehumidifier, humidifier | The humidity, the setpoint, its state, and a dehumidifier's tank |
| Air purifier | The AQI, its fan, its filter, pollen |
| Sensor hub | Up to five sensors at once; with more, it pages along with the sign |
| RPM monitor | Its system's equipment, one row per kind and tier, up to six: `HP T3 ×2 2,950` (the kind, its tier, how many, and the average speed of those running; `on` or `off`). See [RPM monitors](equipment.md#rpm-monitors) |
| Settings panel | The profile, and how to change it |
| Weather station | The outdoor temperature around it big, today's weather, high and low, and wind, then what's certain today or tomorrow's weather; the header coloured by today's sky (BETA 1.0, see [Weather stations](weather-stations.md)) |

- **The sign behind is blank** (BETA 1.0), so only the screen shows.
  Clicking the sign works exactly as before.
- Screens light themselves, so they're readable at night.
- Like signs, screens update when someone is nearby, and only when what they
  show has changed.
- Screens are never saved with the world. They disappear when their chunk
  unloads or the server stops, and come back when it loads again. Removing
  EZvac leaves nothing floating behind.
- Placed on wall signs, standing signs and hanging signs alike.

**Screens are the only display** (BETA 1.0 Pilot 4). Devices no longer write
on their signs, and `displays.screens` is gone from `config.yml`. Bedrock
players coming in through Geyser can't see screens.

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
and air purifiers, and one for [weather stations](weather-stations.md). Each
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
