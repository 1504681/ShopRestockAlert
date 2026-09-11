package com.shoprestockalert;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class RestockOverlay extends OverlayPanel
{
	private static final Color SOON = new Color(255, 80, 80);
	private static final Color NOW = new Color(80, 255, 80);
	private static final Color ASSUMED = new Color(200, 200, 200);

	private final ShopRestockAlertPlugin plugin;
	private final ShopRestockAlertConfig config;

	@Inject
	RestockOverlay(ShopRestockAlertPlugin plugin, ShopRestockAlertConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}
		List<RestockRow> rows = plugin.getRows();
		boolean shopOpen = plugin.isShopOpen();
		if (rows.isEmpty() && !shopOpen)
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Shop restock").build());
		if (rows.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Waiting for a restock tick")
				.leftColor(ASSUMED)
				.build());
			return super.render(graphics);
		}
		int shown = 0;
		for (RestockRow row : rows)
		{
			if (config.soldOnly() && row.getSold() == 0)
			{
				continue;
			}
			if (shown++ >= config.maxLines())
			{
				break;
			}
			String left = row.getName();
			if (row.getSold() > 0)
			{
				left += " (" + row.getSold() + " to clear)";
			}
			else
			{
				left += " x" + row.getQuantity();
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left(left)
				.right(formatTicks(row.getTicksLeft(), row.isLearned(), config.showSeconds()))
				.rightColor(colour(row))
				.build());
		}
		return super.render(graphics);
	}

	private Color colour(RestockRow row)
	{
		if (row.getTicksLeft() == RestockRow.WAITING)
		{
			return ASSUMED;
		}
		if (row.getTicksLeft() <= 0)
		{
			return NOW;
		}
		if (row.getTicksLeft() <= config.countdownTicks())
		{
			return SOON;
		}
		return row.isLearned() ? Color.WHITE : ASSUMED;
	}

	static String formatTicks(int ticks, boolean learned, boolean seconds)
	{
		if (ticks == RestockRow.WAITING)
		{
			return "waiting";
		}
		if (ticks <= 0)
		{
			return "now";
		}
		String text = (learned ? "" : "~") + ticks + "t";
		if (seconds)
		{
			text += String.format(" %.1fs", ticks * 0.6);
		}
		return text;
	}
}
