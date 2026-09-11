package com.shoprestockalert;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;

/**
 * Small countdown box next to the other RuneLite timers: ticks until the shop's next restock.
 */
public class RestockInfoBox extends InfoBox
{
	private static final Color SOON = new Color(255, 80, 80);
	private static final Color NOW = new Color(80, 255, 80);

	private final ShopRestockAlertPlugin plugin;
	private final ShopRestockAlertConfig config;

	RestockInfoBox(BufferedImage image, ShopRestockAlertPlugin plugin, ShopRestockAlertConfig config)
	{
		super(image, plugin);
		this.plugin = plugin;
		this.config = config;
		setPriority(InfoBoxPriority.MED);
		setTooltip("Shop restock tick");
	}

	@Override
	public boolean render()
	{
		return config.showInfoBox() && plugin.getTicksToNext() != ShopRestockAlertPlugin.NO_TIMER;
	}

	@Override
	public String getText()
	{
		int ticks = plugin.getTicksToNext();
		if (ticks == ShopRestockAlertPlugin.NO_TIMER)
		{
			return "";
		}
		return ticks <= 0 ? "now" : Integer.toString(ticks);
	}

	@Override
	public Color getTextColor()
	{
		int ticks = plugin.getTicksToNext();
		if (ticks <= 0)
		{
			return NOW;
		}
		if (ticks <= config.countdownTicks())
		{
			return SOON;
		}
		return Color.WHITE;
	}
}
