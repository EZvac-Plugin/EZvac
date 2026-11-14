# Fridges and freezers

New in BETA 1.0 Pilot 7. A fridge, freezer or fridge-freezer keeps a barrel cold, and the heat it pulls out warms the room it stands in.

In EZvac itself **food never spoils**: a fridge is a cold barrel. With an addon such as [EZclimates](https://github.com/EZvac-Plugin/EZclimates) and its Food spoiling part switched on, food keeps longer the colder it's kept, and a fridge or freezer is where it keeps best.

## Building one

1. Place an **iron block**: the compressor.
2. Put a **barrel** on top of it: that's what it keeps cold. Barrels only, not chests.
3. Put a **sign on the side of the iron block**.
4. Look at the sign and run `/hvac create fridge [label]` or `/hvac create freezer [label]`.

| | Holds | A new one |
|---|---|---|
| Fridge | 33 to 50 °F | 37 °F |
| Freezer | −10 to 20 °F | 0 °F |

Click the sign to change the setpoint a degree at a time; sneak-click to switch it on or off. Its screen shows the temperature inside, the setpoint, and whether it's **Cooling**, **Holding**, **Door open** or **Off**. `/hvac info` while looking at it says the same, and what it stands in: a system's room, or the air outside.

Breaking the sign, the iron block or the barrel removes it; the barrel drops what it held, as any barrel does.

## Fridge-freezers

A fridge and a freezer in one, on one compressor:

1. Place an **iron block** and put a **sign on its side**.
2. Stack **two barrels** on it: the lower one is the **freezer**, the one on top the **fridge**.
3. Look at the sign and run `/hvac create fridge-freezer [label]` (or `fridgefreezer`, `combo`).

Each compartment holds its own setpoint: the fridge 33 to 50 °F (37 by default), the freezer −10 to 20 °F (0 by default). The compressor runs while either wants it, and never cools the freezer below its setpoint while it's only working for the fridge. In the sign's menu the top row sets the fridge and the middle row the freezer. Its screen has a line for each compartment, and what it's doing.

Each barrel is its own door: opening the fridge warms only the fridge. The freezer below frosts and defrosts as a freezer does; the fridge never frosts, but warms a little while the freezer defrosts, since the compressor rests. It warms its room as a fridge and a freezer together. Breaking either barrel removes it.

## How it works

- **Cooling.** A running compressor pulls the inside down (20 °F an in-game hour), stopping at the setpoint and starting again 2 °F above it. A fridge from room temperature is cold in about two in-game hours; a freezer takes about four.
- **The walls.** Its inside slowly closes on the air around it: its system's room temperature, or the air outside where no system's room holds it. Switched off, it warms to that.
- **The door.** Opening the barrel lets 15% of the gap to that air in at once, and while anyone has it open it warms fifteen times faster, faster than its compressor can cool it, so a barrel left open warms up. Opened again soon after, less comes in at once: its cold air takes an in-game hour to build up again, so opening it over and over adds next to nothing.
- **Frost.** A freezer frosts up: a little each time it's opened (more in humid air, and only for the air that actually came in, so opening it again and again frosts it no more than once) and slowly while its compressor runs. Frost grows in round the edge of the barrel, a slot for every twentieth: empty edge slots fill with **Frost** first, and only once none are left is food against the wall **frosted over**, which can't be taken out until a defrost frees it. The middle row's inner seven slots never frost. Frost also slows its cooling, by up to half. A **defrost** melts it in half an in-game hour, its heater keeping food frozen: on its own at 60% frost, or from **Defrost now** in its menu, which works even when it's off. **Auto defrost** in the menu switches the automatic ones off. `/hvac frost <0-100>` sets it, for admins.
- **The room.** A running fridge adds 0.3 °F an in-game hour to its system's room, a freezer 0.5, less in a bigger room: a kitchen full of freezers makes its AC work harder.

All of this is under [`refrigeration:`](configuration.md#refrigeration) in `config.yml`. Fridges, freezers and fridge-freezers can be switched off server-wide in [quick settings](screens-and-menus.md#devices), on the Devices page's second row.

## For addon developers

API 1.12 lists them among the climate units (`ClimateUnitView.Kind` `FRIDGE`, `FREEZER` and `FRIDGE_FREEZER`, with `insideF()`: a fridge-freezer's is its freezer's), and `storageTemperatureF(world, x, y, z)` gives the temperature inside any container: a fridge's or freezer's inside (either compartment of a fridge-freezer), or the air there. See [the API](api.md).
