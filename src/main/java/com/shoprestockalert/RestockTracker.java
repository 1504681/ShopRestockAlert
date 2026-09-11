package com.shoprestockalert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diffs successive snapshots of a shop's stock and works out which changes were the restock timer.
 * Pure Java so it can be unit tested. Ticks are the client tick counter.
 */
public class RestockTracker
{
	public static final int UNKNOWN = TrackedItem.UNKNOWN;

	private final Map<Integer, TrackedItem> items = new LinkedHashMap<>();
	private Map<Integer, Integer> lastSnapshot;
	private int observingSince = UNKNOWN;
	private int lastObservationTick = UNKNOWN;

	// the shop interface opened: from here on every stock change is seen
	public void startObserving(int tick)
	{
		observingSince = tick;
		lastSnapshot = null;
	}

	// the shop interface closed: stock can change unseen, so the next snapshot is a fresh baseline
	public void stopObserving()
	{
		observingSince = UNKNOWN;
		lastSnapshot = null;
	}

	public boolean isObserving()
	{
		return observingSince != UNKNOWN;
	}

	public void clear()
	{
		items.clear();
		lastSnapshot = null;
		observingSince = UNKNOWN;
		lastObservationTick = UNKNOWN;
	}

	public int getLastObservationTick()
	{
		return lastObservationTick;
	}

	/**
	 * Feed a new stock snapshot (item id to quantity). ownTrade means our own inventory changed on the same tick,
	 * so the difference is our buy or sell rather than the timer. Returns the ids the timer moved.
	 */
	public List<Integer> update(int tick, Map<Integer, Integer> stock, boolean ownTrade)
	{
		List<Integer> ticked = new ArrayList<>();
		if (lastSnapshot == null)
		{
			lastSnapshot = new HashMap<>(stock);
			for (TrackedItem item : items.values())
			{
				item.setQuantity(stock.getOrDefault(item.getItemId(), 0));
			}
			return ticked;
		}

		Set<Integer> ids = new HashSet<>(lastSnapshot.keySet());
		ids.addAll(stock.keySet());
		for (int id : ids)
		{
			int before = lastSnapshot.getOrDefault(id, 0);
			int after = stock.getOrDefault(id, 0);
			int delta = after - before;
			if (delta == 0)
			{
				continue;
			}
			TrackedItem item = items.computeIfAbsent(id, TrackedItem::new);
			item.setQuantity(after);
			if (ownTrade)
			{
				item.addSold(delta);
				continue;
			}
			if (Math.abs(delta) != 1)
			{
				// someone else bought or sold a stack
				continue;
			}
			if (delta < 0)
			{
				item.addSold(-1);
			}
			if (item.observe(tick, observingSince))
			{
				ticked.add(id);
				lastObservationTick = tick;
			}
		}

		// an item that left the shop entirely (sold stock that ran out) has nothing left to time
		items.values().removeIf(item -> !stock.containsKey(item.getItemId()));
		lastSnapshot = new HashMap<>(stock);
		return ticked;
	}

	/**
	 * Call once per game tick while observing. Drops items whose predicted changes keep failing to show up,
	 * which is what a fully stocked item or a wrongly learned interval looks like. Returns the dropped ids.
	 */
	public List<Integer> tick(int now, int fallbackInterval)
	{
		List<Integer> dropped = new ArrayList<>();
		if (!isObserving())
		{
			return dropped;
		}
		for (TrackedItem item : items.values())
		{
			if (item.getLastChangeTick() != UNKNOWN && item.checkExpected(now, fallbackInterval))
			{
				dropped.add(item.getItemId());
			}
		}
		for (int id : dropped)
		{
			items.remove(id);
		}
		return dropped;
	}

	public TrackedItem get(int itemId)
	{
		return items.get(itemId);
	}

	// items with a known timer phase, i.e. something to predict
	public List<TrackedItem> timedItems()
	{
		List<TrackedItem> result = new ArrayList<>();
		for (TrackedItem item : items.values())
		{
			if (item.getLastChangeTick() != UNKNOWN)
			{
				result.add(item);
			}
		}
		return Collections.unmodifiableList(result);
	}

	public boolean isEmpty()
	{
		return items.isEmpty();
	}
}
