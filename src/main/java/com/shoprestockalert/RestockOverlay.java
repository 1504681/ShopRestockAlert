package com.shoprestockalert;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class RestockOverlay extends OverlayPanel
{
	private static final Color SOON = new Color(255, 80, 80);
	private static final Color NOW = new Color(80, 255, 80);
	private static final Color ASSUMED = new Color(200, 200, 200);
	private static final Color BAR = new Color(60, 140, 220);
	private static final Color BAR_SOON = new Color(220, 70, 70);
	private static final Color BAR_BACKGROUND = new Color(30, 30, 30, 200);

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
		int ticksLeft = plugin.getTicksToNext();
		List<RestockRow> rows = plugin.getRows();
		if (ticksLeft == ShopRestockAlertPlugin.NO_TIMER && rows.isEmpty() && !plugin.isShopOpen())
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder().text("Shop restock").build());

		if (ticksLeft == ShopRestockAlertPlugin.NO_TIMER)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Waiting for a restock tick")
				.leftColor(ASSUMED)
				.build());
		}
		else
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Next tick")
				.right(formatTicks(ticksLeft, plugin.isIntervalLearned(), config.showSeconds()))
				.rightColor(colour(ticksLeft))
				.rightFont(FontManager.getRunescapeBoldFont())
				.build());
			int interval = plugin.getIntervalTicks();
			if (interval > 0)
			{
				ProgressBarComponent bar = new ProgressBarComponent();
				bar.setMinimum(0);
				bar.setMaximum(interval);
				bar.setValue(Math.max(0, Math.min(interval, interval - ticksLeft)));
				bar.setForegroundColor(ticksLeft <= config.countdownTicks() ? BAR_SOON : BAR);
				bar.setBackgroundColor(BAR_BACKGROUND);
				bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
				bar.setCenterLabel(ticksLeft <= 0 ? "restocking" : formatDuration(ticksLeft));
				panelComponent.getChildren().add(bar);
			}
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
			String left;
			String right;
			if (sold)
			{
				left = row.getName() + " (" + row.getSold() + " left)";
				right = row.getClearTicks() == RestockRow.UNKNOWN ? "waiting" : "clear in " + formatDuration(row.getClearTicks());
			}
			else
			{
				left = row.getName();
				right = "x" + row.getQuantity();
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left(left)
				.right(right)
				.rightColor(sold ? Color.WHITE : ASSUMED)
				.build());
		}
		return super.render(graphics);
	}

	private Color colour(int ticksLeft)
	{
		if (ticksLeft <= 0)
		{
			return NOW;
		}
		if (ticksLeft <= config.countdownTicks())
		{
			return SOON;
		}
		return plugin.isIntervalLearned() ? Color.WHITE : ASSUMED;
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
