package com.shoprestockalert;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
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

	private static final Logger log = LoggerFactory.getLogger(ShopRestockAlertPlugin.class);

	private static final int TICKS_PER_MINUTE = 100;

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
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	private final RestockTracker tracker = new RestockTracker();

	private boolean shopOpen;
	// container id of the shop we are tracking, -1 until a shop has sent stock
	private int shopContainerId = -1;
	// latest shop stock received this tick, evaluated on the game tick so we can tell our own trades apart
	private Map<Integer, Integer> pendingStock;
	private int pendingTick = -1;
	// stock from a container that arrived while no shop was open, in case the shop interface follows it
	private Map<Integer, Integer> candidateStock;
	private int candidateId = -1;
	private int candidateTick = -1;
	private int inventoryChangedTick = -1;
	private int lastSoundTick = -1;
	private int lastRestockAlertTick = -1;

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
		if (event.getGroupId() == InterfaceID.SHOPMAIN)
		{
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
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.SHOPMAIN)
		{
			shopOpen = false;
			tracker.stopObserving();
			pendingStock = null;
			if (!config.keepAfterClose())
			{
				tracker.clear();
				rows = Collections.emptyList();
			}
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
		if (id == InventoryID.WORN || id == InventoryID.BANK)
		{
			return;
		}
		if (event.getItemContainer() == null)
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
		pendingStock = stock;
		pendingTick = now;
	}

	// a shop container we have not been tracking: forget the old shop's timers and start watching this one
	private void adoptShop(int containerId, int now)
	{
		if (containerId != shopContainerId)
		{
			tracker.clear();
			shopContainerId = containerId;
		}
		tracker.startObserving(now);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int now = client.getTickCount();
		if (pendingStock != null && shopOpen)
		{
			boolean ownTrade = inventoryChangedTick == pendingTick;
			List<Integer> ticked = tracker.update(pendingTick, pendingStock, ownTrade);
			if (!ticked.isEmpty())
			{
				log.debug("restock tick for {} at {}", ticked, pendingTick);
			}
			pendingStock = null;
		}
		List<Integer> dropped = tracker.tick(now, config.defaultInterval());
		if (!dropped.isEmpty())
		{
			log.debug("dropped {} after missed restock ticks", dropped);
		}

		int forgetTicks = config.forgetAfter() * TICKS_PER_MINUTE;
		if (tracker.getLastObservationTick() != RestockTracker.UNKNOWN && now - tracker.getLastObservationTick() > forgetTicks)
		{
			tracker.clear();
		}

		List<RestockRow> next = buildRows(now);
		rows = next;
		alert(next, now);
	}

	private List<RestockRow> buildRows(int now)
	{
		List<RestockRow> result = new ArrayList<>();
		for (TrackedItem item : tracker.timedItems())
		{
			int nextTick = item.nextChangeTick(now, config.defaultInterval());
			if (nextTick == TrackedItem.UNKNOWN)
			{
				continue;
			}
			result.add(new RestockRow(item.getItemId(), itemName(item.getItemId()), item.getQuantity(), item.getSold(),
				nextTick - now, item.hasInterval()));
		}
		result.sort(Comparator.comparingInt(RestockRow::getTicksLeft));
		return Collections.unmodifiableList(result);
	}

	private void alert(List<RestockRow> current, int now)
	{
		int soonest = Integer.MAX_VALUE;
		for (RestockRow row : current)
		{
			if (config.alertScope() == AlertScope.SOLD && row.getSold() == 0)
			{
				continue;
			}
			soonest = Math.min(soonest, row.getTicksLeft());
			if (config.alertScope() == AlertScope.SOONEST)
			{
				break;
			}
		}
		if (soonest == Integer.MAX_VALUE)
		{
			return;
		}
		if (soonest <= 0)
		{
			if (lastRestockAlertTick != now)
			{
				lastRestockAlertTick = now;
				play(config.restockSound(), now);
				notifier.notify(config.restockNotification(), "Shop restock tick");
			}
		}
		else if (soonest <= config.countdownTicks())
		{
			play(config.countdownSound(), now);
		}
	}

	private void play(int soundId, int now)
	{
		if (soundId <= 0 || lastSoundTick == now)
		{
			return;
		}
		lastSoundTick = now;
		client.playSoundEffect(soundId);
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

	public List<RestockRow> getRows()
	{
		return rows;
	}
}
