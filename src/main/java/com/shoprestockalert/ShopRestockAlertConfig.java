package com.shoprestockalert;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ShopRestockAlertConfig.GROUP)
public interface ShopRestockAlertConfig extends Config
{
	String GROUP = "shoprestockalert";

	@ConfigSection(
		name = "Timer",
		description = "How restock ticks are predicted",
		position = 0
	)
	String timerSection = "timer";

	@ConfigSection(
		name = "Alerts",
		description = "Sounds and notifications around a restock tick",
		position = 1
	)
	String alertSection = "alerts";

	@ConfigSection(
		name = "Overlay",
		description = "The on-screen tick timer",
		position = 2
	)
	String overlaySection = "overlay";

	@Range(min = 1, max = 6000)
	@Units(Units.TICKS)
	@ConfigItem(
		keyName = "defaultInterval",
		name = "Assumed interval",
		description = "Ticks between restocks to assume for an item until two of its restock ticks have been seen with the shop open. Most shop items use 100 (1 minute), some 10, 20 or 50",
		position = 0,
		section = timerSection
	)
	default int defaultInterval()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "keepAfterClose",
		name = "Keep timing after closing",
		description = "Shop timers keep running on the world, so keep predicting after the shop window is closed",
		position = 1,
		section = timerSection
	)
	default boolean keepAfterClose()
	{
		return true;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "forgetDistance",
		name = "Forget beyond (tiles)",
		description = "With the shop closed, drop the timer and hide the overlays once you are this many tiles from where you opened it, or on another floor. 0 to never",
		position = 2,
		section = timerSection
	)
	default int forgetDistance()
	{
		return 30;
	}

	@Range(min = 1, max = 120)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "forgetAfter",
		name = "Forget after",
		description = "Drop the timers this long after the last restock tick was actually seen",
		position = 3,
		section = timerSection
	)
	default int forgetAfter()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "alertMode",
		name = "Alert",
		description = "When the countdown and restock alerts fire: only while items you sold are still draining, only while the shop window is open, or on every tick until the timer is forgotten",
		position = 0,
		section = alertSection
	)
	default AlertMode alertMode()
	{
		return AlertMode.WHILE_SOLD;
	}

	@Range(min = 0, max = 10)
	@Units(Units.TICKS)
	@ConfigItem(
		keyName = "countdownTicks",
		name = "Countdown dings",
		description = "Play a ding on each of this many ticks before the predicted restock. 3 gives ding, ding, ding, restock",
		position = 1,
		section = alertSection
	)
	default int countdownTicks()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "countdownSound",
		name = "Countdown sound",
		description = "Sound effect id for the countdown dings. 3813 is the town crier bell",
		position = 2,
		section = alertSection
	)
	default int countdownSound()
	{
		return 3813;
	}

	@Range(min = 0, max = 127)
	@ConfigItem(
		keyName = "soundVolume",
		name = "Sound volume",
		description = "Volume for the dings, 0 to 127. Plays even when in-game sound effects are muted",
		position = 3,
		section = alertSection
	)
	default int soundVolume()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "restockSound",
		name = "Restock sound",
		description = "Sound effect id played on the restock tick itself. 3925 is the GE offer chime, 0 for none",
		position = 4,
		section = alertSection
	)
	default int restockSound()
	{
		return 3925;
	}

	@ConfigItem(
		keyName = "restockNotification",
		name = "Restock notification",
		description = "RuneLite notification on the restock tick",
		position = 5,
		section = alertSection
	)
	default Notification restockNotification()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show tick timer",
		description = "Overlay listing tracked items and ticks until their next restock",
		position = 0,
		section = overlaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInfoBox",
		name = "Show infobox",
		description = "Countdown in ticks as an infobox next to the other RuneLite timers",
		position = 1,
		section = overlaySection
	)
	default boolean showInfoBox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSeconds",
		name = "Show seconds",
		description = "Show seconds next to the tick count",
		position = 2,
		section = overlaySection
	)
	default boolean showSeconds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "listAllItems",
		name = "List every item",
		description = "Also list the shop's own restocking items under the countdown, not just the ones you sold",
		position = 3,
		section = overlaySection
	)
	default boolean listAllItems()
	{
		return false;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(
		keyName = "maxLines",
		name = "Max lines",
		description = "How many items to list under the countdown",
		position = 4,
		section = overlaySection
	)
	default int maxLines()
	{
		return 1;
	}
}
