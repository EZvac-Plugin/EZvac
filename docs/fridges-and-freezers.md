# Fridges and freezers

New in BETA 1.0 Pilot 7. A fridge or freezer keeps a barrel cold, and the heat it pulls out warms the room it stands in.

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

Click the sign to change the setpoint a degree at a time; sneak-click to switch it on or off. Its screen shows the temperature inside, the setpoint, and whether it's **Cooling**, **Holding**, **Door open** or **Off**. `/hvac info` while looking at it says the same, and which system's room it warms.

Breaking the sign, the iron block or the barrel removes it; the barrel drops what it held, as any barrel does.

## How it works

- **Cooling.** A running compressor pulls the inside down (20 °F an in-game hour), stopping at the setpoint and starting again 2 °F above it. A fridge from room temperature is cold in about two in-game hours; a freezer takes about four.
- **The walls.** Its inside slowly closes on the air around it: its system's room temperature, or the air outside where no system's room holds it. Switched off, it warms to that.
- **The door.** Opening the barrel lets a quarter of the room's warmth in at once, and while anyone has it open it warms six times faster.
- **The room.** A running fridge adds 0.3 °F an in-game hour to its system's room, a freezer 0.5, less in a bigger room: a kitchen full of freezers makes its AC work harder.

All of this is under [`refrigeration:`](configuration.md#refrigeration) in `config.yml`. Fridges and freezers can be switched off server-wide in [quick settings](screens-and-menus.md#devices), on the Devices page's second row.

## For addon developers

API 1.12 lists them among the climate units (`ClimateUnitView.Kind` `FRIDGE` and `FREEZER`, with `insideF()`), and `storageTemperatureF(world, x, y, z)` gives the temperature inside any container: a fridge's or freezer's inside, or the air there. See [the API](api.md).
