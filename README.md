# Shop Restock Alert

RuneLite plugin that times shop restocks. Every item in a shop has its own restock timer that moves the stock one unit towards its normal amount every so many game ticks: items you sold drain away one at a time, items that were bought out fill back up. This plugin watches those ticks while the shop is open, works out each item's interval, and then predicts the next one, with a tick timer on screen and dings before it lands.

## What it does

Open a shop and the panel appears, waiting for a restock tick. The timer is only visible when it has something to do: a shop where every item is at its normal stock never changes, so nothing can be timed until someone buys or sells. Sell something, or buy one of anything, and the plugin catches the tick when the shop moves it.

Every item in a shop moves on the same tick, so the plugin keeps one timer per shop. Your own buys and sells are told apart from the timer by your inventory changing around the same tick, and someone else's trade of a whole stack is ignored because it isn't a change of exactly one.

The first observed tick gives the phase and the next one is predicted using the assumed interval (100 ticks, one minute, which is what most shops use). The second observed tick with the shop still open gives the real interval. The timer belongs to the shop, not to any item, so it survives the last thing you sold draining to nothing. Sell again later and the countdown is already running.

Items you sold are listed with how many are left in the shop and how long until they are all gone: the next tick plus one interval for every remaining unit. Once they are gone you can sell that item again at full price.

Shop timers run on the world whether or not anyone has the shop open, so by default the plugin keeps predicting after you close the window. Walk off and the dings still come. The timer is forgotten, and the panel and infobox disappear until you open a shop again, when you leave: with the shop closed and you more than 30 tiles from where you opened it, or on a different floor. That distance is a setting, 0 to never. It is also forgotten after 30 minutes without seeing a real tick, when you hop or log out (stock is per world), when you open a different shop, and when stock you sold fails to drain on three predicted ticks in a row, which means the phase was wrong.

## Settings

**Timer**: the assumed interval used before the shop's real one is measured, whether to keep timing after the shop is closed, how far you can walk from the shop before the timer is dropped, and how long to keep it without seeing a real tick.

**Alerts**: when to alert (while items you sold are still draining, which is the default, while the shop window is open, or always), how many countdown dings to play (3 by default: ding, ding, ding, restock), the volume, the sound effect ids for the countdown and for the restock tick itself, and an optional RuneLite notification on the restock tick. Defaults are the town crier bell for the countdown and the Grand Exchange offer chime for the restock. The dings play even with in-game sound effects muted. Set a sound id or the volume to 0 to silence them.

**Overlay**: a panel in the top left with the countdown to the next tick in ticks and seconds, a bar filling up over the interval, then the items you sold with how many are left and when they clear. A `~` in front of a countdown means the interval is still assumed. Red in the last few ticks, green on the tick itself. "Max lines" caps the item list at one by default, and "List every item" adds the shop's own restocking items. There is also an infobox with the tick count next to the other RuneLite timers, which you can turn off.

## Changelog

1.0.0: first release.

## License

BSD 2-Clause, see LICENSE.
