package com.shoprestockalert;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Shop Restock Alert",
	description = "Times shop restock ticks, counts down with dings and shows when sold items clear so you can plan around the next restock",
	tags = {"shop", "restock", "stock", "timer", "tick", "sell", "alert", "store"}
)
public class ShopRestockAlertPlugin extends Plugin
{
	// keep in sync with build.gradle
	public static final String VERSION = "1.0.0";

	// getTicksToNext() when there is no timer to predict from
	public static final int NO_TIMER = -1;

	private static final Logger log = LoggerFactory.getLogger(ShopRestockAlertPlugin.class);

	private static final int TICKS_PER_MINUTE = 100;
	// our inventory update and the shop update for the same trade can land a tick apart
	private static final int OWN_TRADE_WINDOW = 1;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ShopRestockAlertConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RestockOverlay overlay;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	private final RestockTracker tracker = new RestockTracker();
	private RestockInfoBox infoBox;

	private volatile boolean shopOpen;
	// container id of the shop we are tracking, -1 until a shop has sent stock
	private int shopContainerId = -1;
	// latest shop stock received, evaluated a tick later so our own trades can be told apart
	private Map<Integer, Integer> pendingStock;
	private int pendingTick = -1;
	// stock from a container that arrived while no shop was open, in case the shop interface follows it
	private Map<Integer, Integer> candidateStock;
	private int candidateId = -1;
	private int candidateTick = -1;
	private int inventoryChangedTick = -1;
	private int lastSoundTick = -1;
	private int lastRestockAlertTick = -1;

	// what the overlay and infobox draw, refreshed every game tick
	private volatile int ticksToNext = NO_TIMER;
	private volatile int intervalTicks;
	private volatile boolean intervalLearned;
	private volatile List<RestockRow> rows = Collections.emptyList();

	@Provides
	ShopRestockAlertConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ShopRestockAlertConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		infoBox = new RestockInfoBox(itemManager.getImage(ItemID.COINS), this, config);
		infoBoxManager.addInfoBox(infoBox);
		clientThread.invoke(() ->
		{
			shopOpen = client.getWidget(InterfaceID.Shopmain.ITEMS) != null;
			if (shopOpen)
			{
				tracker.startObserving(client.getTickCount());
			}
		});
		log.info("Shop Restock Alert started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		if (infoBox != null)
		{
			infoBoxManager.removeInfoBox(infoBox);
			infoBox = null;
		}
		reset();
		log.info("Shop Restock Alert stopped");
	}

	private void reset()
	{
		tracker.clear();
		shopOpen = false;
		shopContainerId = -1;
		pendingStock = null;
		pendingTick = -1;
		candidateStock = null;
		candidateId = -1;
		candidateTick = -1;
		inventoryChangedTick = -1;
		lastSoundTick = -1;
		lastRestockAlertTick = -1;
		ticksToNext = NO_TIMER;
		intervalTicks = 0;
		intervalLearned = false;
		rows = Collections.emptyList();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
		{
			// shop timers are per world, so nothing we know carries over
			reset();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.SHOPMAIN)
		{
			return;
		}
		shopOpen = true;
		int now = client.getTickCount();
		// the stock container usually arrives just before the interface does, so adopt it
		if (candidateStock != null && candidateTick >= now - 1)
		{
			adoptShop(candidateId, now);
			pendingStock = candidateStock;
			pendingTick = candidateTick;
		}
		else
		{
			tracker.startObserving(now);
		}
		candidateStock = null;
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() != InterfaceID.SHOPMAIN)
		{
			return;
		}
		shopOpen = false;
		flushPending();
		tracker.stopObserving();
		if (!config.keepAfterClose())
		{
			tracker.clear();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		int now = client.getTickCount();
		if (id == InventoryID.INV)
		{
			inventoryChangedTick = now;
			return;
		}
		if (id == InventoryID.WORN || id == InventoryID.BANK || event.getItemContainer() == null)
		{
			return;
		}
		Map<Integer, Integer> stock = snapshot(event.getItemContainer().getItems());
		if (!shopOpen)
		{
			// not a shop we can see. Keep it briefly in case the shop interface opens right after
			candidateStock = stock;
			candidateId = id;
			candidateTick = now;
			return;
		}
		if (id != shopContainerId)
		{
			adoptShop(id, now);
		}
		if (pendingStock != null && pendingTick != now)
		{
			// a new tick's worth of changes: settle the earlier one first so the phases stay separate
			flushPending();
		}
		pendingStock = stock;
		pendingTick = now;
	}

	// a shop container we have not been tracking: forget the old shop's timer and start watching this one
	private void adoptShop(int containerId, int now)
	{
		if (containerId != shopContainerId)
		{
			tracker.clear();
			shopContainerId = containerId;
		}
		tracker.startObserving(now);
	}

	private void flushPending()
	{
		if (pendingStock == null)
		{
			return;
		}
		boolean ownTrade = Math.abs(inventoryChangedTick - pendingTick) <= OWN_TRADE_WINDOW;
		List<Integer> moved = tracker.update(pendingTick, pendingStock, ownTrade);
		if (!moved.isEmpty())
		{
			log.debug("restock tick at {} moved {}", pendingTick, moved);
		}
		pendingStock = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int now = client.getTickCount();
		// wait a tick so an inventory update from the same trade has had time to arrive
		if (pendingStock != null && now > pendingTick + OWN_TRADE_WINDOW - 1)
		{
			flushPending();
		}
		if (tracker.tick(now, config.defaultInterval()))
		{
			log.debug("dropped the timer after predicted ticks that never came");
		}

		int forgetTicks = config.forgetAfter() * TICKS_PER_MINUTE;
		if (tracker.getLastObservationTick() != RestockTracker.UNKNOWN && now - tracker.getLastObservationTick() > forgetTicks)
		{
			tracker.clear();
		}

		refresh(now);
		alert(now);
	}

	private void refresh(int now)
	{
		ShopTimer timer = tracker.getTimer();
		int next = timer.nextChangeTick(now, config.defaultInterval());
		int left = next == ShopTimer.UNKNOWN ? NO_TIMER : next - now;
		ticksToNext = left;
		intervalTicks = timer.stepOr(config.defaultInterval());
		intervalLearned = timer.hasInterval();

		List<RestockRow> result = new ArrayList<>();
		for (TrackedItem item : tracker.allItems())
		{
			int clearTicks = RestockRow.UNKNOWN;
			if (item.getSold() > 0 && left != NO_TIMER)
			{
				clearTicks = left + (item.getSold() - 1) * intervalTicks;
			}
			result.add(new RestockRow(item.getItemId(), itemName(item.getItemId()), item.getQuantity(), item.getSold(), clearTicks));
		}
		// items we sold first, most left to clear first
		result.sort((a, b) -> Integer.compare(b.getSold(), a.getSold()));
		rows = Collections.unmodifiableList(result);
	}

	private boolean alertsWanted()
	{
		switch (config.alertMode())
		{
			case WHILE_SOLD:
				return tracker.soldRemaining() > 0;
			case SHOP_OPEN:
				return shopOpen;
			default:
				return true;
		}
	}

	private void alert(int now)
	{
		int left = ticksToNext;
		if (left == NO_TIMER || !alertsWanted())
		{
			return;
		}
		if (left <= 0)
		{
			if (lastRestockAlertTick != now)
			{
				lastRestockAlertTick = now;
				play(config.restockSound(), now);
				notifier.notify(config.restockNotification(), "Shop restock tick");
			}
		}
		else if (left <= config.countdownTicks())
		{
			play(config.countdownSound(), now);
		}
	}

	private void play(int soundId, int now)
	{
		if (soundId <= 0 || config.soundVolume() <= 0 || lastSoundTick == now)
		{
			return;
		}
		lastSoundTick = now;
		// the two argument form plays even when in-game sound effects are muted
		client.playSoundEffect(soundId, config.soundVolume());
	}

	private static Map<Integer, Integer> snapshot(Item[] items)
	{
		Map<Integer, Integer> stock = new HashMap<>();
		if (items == null)
		{
			return stock;
		}
		for (Item item : items)
		{
			if (item != null && item.getId() > 0)
			{
				stock.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return stock;
	}

	private String itemName(int itemId)
	{
		ItemComposition composition = itemManager.getItemComposition(itemId);
		String name = composition == null ? null : composition.getName();
		return name == null || "null".equals(name) ? "Item " + itemId : name;
	}

	public int getTicksToNext()
	{
		return ticksToNext;
	}

	public int getIntervalTicks()
	{
		return intervalTicks;
	}

	public boolean isIntervalLearned()
	{
		return intervalLearned;
	}

	public List<RestockRow> getRows()
	{
		return rows;
	}

	public boolean isShopOpen()
	{
		return shopOpen;
	}
}
