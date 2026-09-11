# Shop Restock Alert

RuneLite plugin that times shop restocks. Every item in a shop has its own restock timer that moves the stock one unit towards its normal amount every so many game ticks: items you sold drain away one at a time, items that were bought out fill back up. This plugin watches those ticks while the shop is open, works out each item's interval, and then predicts the next one, with a tick timer on screen and dings before it lands.

## What it does

Open a shop and the panel appears, saying it is waiting for a restock tick. The timer is only visible when it has something to do: a shop where every item is at its normal stock never changes, so nothing can be timed until someone buys or sells. Buy one of something and the plugin catches the restock tick when the shop puts it back, usually within a minute.

Every time the timer moves an item's stock by one, the plugin notes the tick. Your own buys and sells are told apart from the timer by your inventory changing on the same tick, and someone else's trade of a whole stack is ignored because it isn't a change of exactly one.

After the first observed tick the plugin knows the phase of that item's timer and predicts the next tick using the assumed interval (100 ticks, one minute, which is what most shop items use). After the second observed tick with the shop still open it knows the real interval and switches to it. Items that stop changing, because they are fully stocked or because the guess was wrong, are dropped after two predicted ticks pass with nothing happening.

Items you sold are shown straight away with how many are still in the shop to clear, marked "waiting" until the timer first touches them. The sold count goes down by one each restock tick until the item disappears, which is when you can dump another load.

Shop timers run on the world whether or not anyone has the shop open, so by default the plugin keeps predicting after you close the window. Walk off, do something else, and the dings still come. Timers are forgotten after 15 minutes without seeing a real tick, when you hop or log out (stock is per world), and when you open a different shop.

## Settings

**Timer**: the assumed interval used before an item's real one is measured, whether to keep timing after the shop is closed, and how long to keep timers without seeing a real tick.

**Alerts**: which items alert (the soonest one, only items you sold, or every item), how many countdown dings to play (3 by default: ding, ding, ding, restock), the sound effect ids for the countdown and for the restock tick itself, and an optional RuneLite notification on the restock tick. Defaults are the town crier bell for the countdown and the Grand Exchange offer chime for the restock. Set a sound id to 0 to silence it.

**Overlay**: a panel in the top left listing tracked items soonest first, with ticks and seconds until the next restock. A `~` in front means the interval is still assumed. Red in the last few ticks, green on the tick itself. You can limit it to items you sold and cap the number of lines.

## Running it locally

You need a JDK, 11 or newer. Gradle comes with the wrapper.

```
git clone https://github.com/1504681/ShopRestockAlert.git
cd ShopRestockAlert
./gradlew run
```

On Windows use `.\gradlew.bat run`. That starts a normal RuneLite client in developer mode with the plugin already loaded. Log in, open a shop and buy something.

`./gradlew build` compiles and runs the unit tests, which is what the Plugin Hub CI does. `./gradlew installPlugin` puts a jar in `~/.runelite/externalPlugins` if you'd rather sideload. From an IDE, run `ShopRestockAlertLauncher` with `-ea`.

## Changelog

1.0.0: first release.

## License

BSD 2-Clause, see LICENSE.
