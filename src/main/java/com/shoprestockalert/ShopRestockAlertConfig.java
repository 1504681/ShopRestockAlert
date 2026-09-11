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

	@Range(min = 1, max = 120)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "forgetAfter",
		name = "Forget after",
		description = "Drop the timers this long after the last restock tick was actually seen",
		position = 2,
		section = timerSection
	)
	default int forgetAfter()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "alertScope",
		name = "Alert for",
		description = "Which items the countdown and restock alerts fire for",
		position = 0,
		section = alertSection
	)
	default AlertScope alertScope()
	{
		return AlertScope.SOONEST;
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

	@ConfigItem(
		keyName = "restockSound",
		name = "Restock sound",
		description = "Sound effect id played on the restock tick itself. 3925 is the GE offer chime, 0 for none",
		position = 3,
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
		position = 4,
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
		keyName = "showSeconds",
		name = "Show seconds",
		description = "Show seconds next to the tick count",
		position = 1,
		section = overlaySection
	)
	default boolean showSeconds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soldOnly",
		name = "Only items you sold",
		description = "List only items you sold to the shop, with how many are left to clear",
		position = 2,
		section = overlaySection
	)
	default boolean soldOnly()
	{
		return false;
	}

	@Range(min = 1, max = 30)
	@ConfigItem(
		keyName = "maxLines",
		name = "Max lines",
		description = "How many items to list, soonest first",
		position = 3,
		section = overlaySection
	)
	default int maxLines()
	{
		return 8;
	}
}
