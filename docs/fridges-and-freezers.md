# Refrigeration

New in BETA 1.0 ("EZvac/R"). Four appliances keep barrels cold, and the heat they pull out warms the room they stand in.

In EZvac itself **food never spoils**: a fridge is a cold barrel. With an addon such as [EZclimates](https://github.com/EZvac-Plugin/EZclimates) and its Food spoiling part switched on, food keeps longer the colder it's kept.

| | Build | Holds | Frost |
|---|---|---|---|
| **Mini fridge** | a barrel with a sign on its side | 33 to 50 °F (37), plus a chiller box about 10 °F colder | never |
| **Fridge** | an iron block, 1 to 3 barrels on top | 33 to 50 °F (37) | never |
| **Refrigerator** | an iron block, the freezer barrel on it, the fridge barrel on top | fridge 33 to 50 °F (37), freezer −5 to 10 °F (0) | its freezer: defrosts itself |
| **Deep freezer** | an iron block, a barrel on top | −20 to 0 °F (−10) | slowly; defrosted by hand |

Barrels only, never chests. Breaking any of an appliance's blocks removes it; its barrels drop what they held, as any barrel does.

## Building one

1. Place an **iron block** (the compressor) and put a **sign on its side**. A mini fridge has no iron block: put the sign on the barrel itself.
2. Stack its **barrels** on the iron block.
3. Look at the sign and run:
   - `/hvac create minifridge [label]` (or `mini-fridge`, `mini`)
   - `/hvac create fridge [label]`
   - `/hvac create refrigerator [label]` (or `fridge-freezer`, `combo`)
   - `/hvac create deepfreezer [label]` (or `deep-freezer`, `freezer`)

Click the sign to change the setpoint a degree at a time; sneak-click to switch it on or off. Its screen is laid out like an appliance's door panel: the temperature inside in whole degrees, the compartment and its setpoint, and whether it's **Cooling**, **Holding**, **Door open**, **Defrosting** or **Off**. `/hvac info` while looking at it says the same with decimals, and what it stands in: a system's room, or the air outside.

## Mini fridges

A small fridge for a desk, a bedroom or a bar.

```
▒ ▒ ▒ ▒ ▒ ▒ ▒ ▒ ▒      ▒  its walls
□ □ □ □ □ □ □ ▒ ❄      □  food (seven slots)   ❄  the chiller box
▒ ▒ ▒ ▒ ▒ ▒ ▒ ▒ ▒
```

- Food goes in the **seven slots** along the middle row. The rest of the barrel is its walls, which can't be taken out; anything already where a wall goes is moved to a free slot, or dropped.
- The last slot is the **chiller box**: about 10 °F colder than the rest while it's on, so at 37 °F it sits just below freezing. There's no separate control for it, as on a real mini fridge. With EZclimates, food in the chiller keeps as frozen food does.
- It's smaller: it cools at half a fridge's pace, an opening lets in half as much again, and it warms its room half as much.

## Fridges

An all-fridge, as a shop's or a restaurant's is: it takes every barrel stacked on its iron block when it's made, up to **three**. They share one inside, and it warms its room a fridge's worth for each barrel.

## Refrigerators

The household fridge-freezer: the **freezer** barrel sits on the iron block, the **fridge** barrel on top of it. Each compartment holds its own setpoint; one compressor runs while either wants it, and never cools the freezer below its setpoint while it's only working for the fridge. In the sign's menu the top row sets the fridge and the middle row the freezer; the screen shows the two side by side.

Each barrel is its own door: opening the fridge warms only the fridge. Its freezer is an everyday frost-free one: frost builds, and it **defrosts itself** at 60% (**Auto defrost** in the menu switches that off). The fridge never frosts, but warms a little while the freezer defrosts, since the compressor rests. It warms its room as a fridge and a freezer together.

## Deep freezers

A chest freezer, for keeping food the longest.

- Its barrel opens on top like a **chest freezer's lid**: cold air sinks and stays in, so an opening lets in 40% of what a fridge's door does, and half the frost.
- It **never defrosts itself**, as a real chest freezer doesn't. Frost builds slowly; when it gets heavy, use **Defrost now** in its menu.

## How it works

- **Cooling.** A running compressor pulls the inside down (20 °F an in-game hour), stopping at the setpoint and starting again 2 °F above it.
- **The walls.** Its inside slowly closes on the air around it: its system's room temperature, or the air outside where no system's room holds it. Switched off, it warms to that.
- **The door.** Opening a barrel lets 15% of the gap to that air in at once (a deep freezer's lid less, a mini fridge more), and while anyone has it open it warms fifteen times faster, faster than its compressor can cool it. Opened again soon after, less comes in at once: its cold air takes an in-game hour to build up again, so opening it over and over adds next to nothing.
- **Frost** (a refrigerator's freezer and a deep freezer): a little each time it's opened (more in humid air, and only for the air that actually came in) and slowly while its compressor runs. It grows in round the edge of the barrel, a slot for every twentieth: empty edge slots fill with **Frost** first, and only once none are left is food against the wall **frosted over**, which can't be taken out until a defrost frees it. The middle row's inner seven slots never frost. Frost also slows its cooling, by up to half. A **defrost** melts it in half an in-game hour, its heater keeping food frozen; **Defrost now** works even when it's off. `/hvac frost <0-100>` sets it, for admins.
- **The room.** A running fridge adds 0.3 °F an in-game hour to its system's room for each barrel, a deep freezer 0.5, a refrigerator both, a mini fridge half a fridge's; less in a bigger room. A kitchen full of them makes its AC work harder.

All of this is under [`refrigeration:`](configuration.md#refrigeration) in `config.yml`. Each kind can be switched off server-wide in [quick settings](screens-and-menus.md#devices), on the Devices page's second row.

## For addon developers

API 1.12 lists them among the climate units (`ClimateUnitView.Kind` `MINI_FRIDGE`, `FRIDGE`, `REFRIGERATOR` and `DEEP_FREEZER`, with `insideF()`: a refrigerator's is its freezer's), and `storageTemperatureF(world, x, y, z)` gives the temperature inside any container: an appliance's inside (either compartment of a refrigerator, any barrel of a fridge), or the air there. `storageTemperatureF(world, x, y, z, slot)` gives it for one slot, which differs only in a mini fridge's chiller box. See [the API](api.md).
