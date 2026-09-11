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
		// rows are sorted soonest first, so the first timed row is the next tick anywhere in the shop
		RestockRow soonest = rows.get(0);
		if (soonest.getTicksLeft() != RestockRow.WAITING)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Next tick")
				.right(formatTicks(soonest.getTicksLeft(), soonest.isLearned(), config.showSeconds()))
				.rightColor(colour(soonest))
				.build());
		}
		else
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Waiting for a restock tick")
				.leftColor(ASSUMED)
				.build());
		}

		int shown = 0;
		for (RestockRow row : rows)
		{
			boolean sold = row.getSold() > 0;
			if (!sold && !config.listAllItems())
			{
				continue;
			}
			if (shown++ >= config.maxLines())
			{
				break;
			}
			String left = row.getName();
			String right;
			if (sold)
			{
				left += " (" + row.getSold() + " left)";
				right = row.getClearTicks() == RestockRow.WAITING ? "waiting" : "clear in " + formatDuration(row.getClearTicks());
			}
			else
			{
				left += " x" + row.getQuantity();
				right = formatTicks(row.getTicksLeft(), row.isLearned(), config.showSeconds());
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left(left)
				.right(right)
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

	// a longer span in seconds or minutes, e.g. "45s" or "12.5m"
	static String formatDuration(int ticks)
	{
		double seconds = Math.max(0, ticks) * 0.6;
		if (seconds < 90)
		{
			return Math.round(seconds) + "s";
		}
		return String.format("%.1fm", seconds / 60);
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
