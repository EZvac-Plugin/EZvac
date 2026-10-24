# HUD and placeholders

## The climate HUD

```text
/hvac hud [on|off]
```

Keeps the climate where you stand on screen: the temperature, how warm it
feels, a cold-to-hot bar with a comfort word (cold, cool, comfortable, warm,
hot), the humidity, the outdoor temperature and weather, and the system
conditioning that spot with its mode, its target and whether you're in its
core or its edge.

- It's off by default for each player, and EZvac remembers each player's
  choice across restarts. With no argument the command toggles it.
- It needs `ezvac.hud`, which everyone has by default.

<!-- screenshot: the sidebar HUD in a conditioned room -->

### Styles

`hud.style` in `config.yml` picks how it's shown:

- **Sidebar** (default): a scoreboard sidebar with the score numbers hidden.
- **Action bar:** one line above the hotbar.
- **Boss bar:** a bar whose fill and colour follow the temperature.

EZvac never takes over a sidebar the server already uses. It only shows its
sidebar while the player is on the server's main scoreboard and nothing else
is shown there, steps back if another plugin takes over, and uses the action
bar instead when a scoreboard plugin such as TAB is installed
(`hud.sidebar.yield-to-plugins`).

### In water

Swimming switches the HUD to water readings: `Water`, `Air`, `Pool` and
`Circulation` lines, a 60-104 °F bar, and a pool comfort scale where 78-86 °F is
comfortable and above 95 °F is a hot tub. It reads at your eyes when you're
under water and at your feet otherwise, so swimming at the surface and wading
both count.

In air it reads at head height, falling back to your feet.

## Placeholders

With [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) installed
(optional), the same values are available to scoreboard, TAB and chat plugins:

| Placeholder | Example |
| --- | --- |
| `%ezvac_temp%` (`_temp_f`, `_temp_c`) | `72.4°F` |
| `%ezvac_feels_like%` (`_feels_like_f`, `_feels_like_c`) | `74.1°F` |
| `%ezvac_humidity%` | `48%` |
| `%ezvac_weather%` | `Rain` (`Clear`, `Thunder`, `Snow`; empty in dry biomes) |
| `%ezvac_outdoor%` (`_outdoor_f`, `_outdoor_c`) | `88.0°F` |
| `%ezvac_bar%` / `%ezvac_bar_plain%` | `▮▮▮▮▮▯▯▯▯▯` (coloured / plain) |
| `%ezvac_comfort%` | `Comfortable` |
| `%ezvac_group%`, `%ezvac_thermostat%` | `house`, `main` |
| `%ezvac_mode%`, `%ezvac_target%` | `Cooling to 70.0°F`, `70.0°F` |
| `%ezvac_zone%`, `%ezvac_influence%`, `%ezvac_conditioned%` | `Core`, `100`, `true` |
| `%ezvac_medium%` | `air` or `water` |

Values are refreshed for each player every half second (`hud.update-ticks`),
and placeholder requests only read that snapshot, so a scoreboard that
refreshes often costs nothing extra. Without PlaceholderAPI, EZvac logs one
line saying what installing it would add.

There are deliberately no placeholders for individual sensors: a sensor's
reading stays on its hub.

## Customising

Temperatures follow the server-wide `units` setting (°F or °C); see
[Configuration](configuration.md#units). Text, colours, the bar's length and
range, and comfort bands are all under `hud:` (and `hud.water:` for water) in `config.yml`. HUD lines can
use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatting
and these tokens:

`{temp}` `{feels}` `{humidity}` `{bar}` `{comfort}` `{outdoor}` `{weather}`
`{group}` `{thermostat}` `{mode}` `{target}` `{zone}` `{version}`

`{version}` is the short EZvac version, such as `A2.0`. See
[Configuration](configuration.md#hud).
