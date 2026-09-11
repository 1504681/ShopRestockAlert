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
 * Diffs successive snapshots of a shop's stock, keeps the shop's timer and the items we sold into it.
 * Pure Java so it can be unit tested. Ticks are the client tick counter.
 */
public class RestockTracker
{
	public static final int UNKNOWN = ShopTimer.UNKNOWN;

	private final ShopTimer timer = new ShopTimer();
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
		timer.clear();
		items.clear();
		lastSnapshot = null;
		observingSince = UNKNOWN;
		lastObservationTick = UNKNOWN;
	}

	public ShopTimer getTimer()
	{
		return timer;
	}

	public int getLastObservationTick()
	{
		return lastObservationTick;
	}

	/**
	 * Feed a new stock snapshot (item id to quantity). ownTrade means our own inventory changed around the same
	 * tick, so the difference is our buy or sell rather than the timer. Returns the ids the timer moved.
	 */
	public List<Integer> update(int tick, Map<Integer, Integer> stock, boolean ownTrade)
	{
		List<Integer> moved = new ArrayList<>();
		if (lastSnapshot == null)
		{
			lastSnapshot = new HashMap<>(stock);
			for (TrackedItem item : items.values())
			{
				item.setQuantity(stock.getOrDefault(item.getItemId(), 0));
			}
			forgetGone(stock);
			return moved;
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
			moved.add(id);
		}

		// every item moves on the same tick, so one observation covers them all
		if (!moved.isEmpty() && timer.observe(tick, observingSince))
		{
			lastObservationTick = tick;
		}
		else
		{
			moved.clear();
		}

		forgetGone(stock);
		lastSnapshot = new HashMap<>(stock);
		return moved;
	}

	// an item that left the shop entirely (sold stock that ran out) has nothing left to track
	private void forgetGone(Map<Integer, Integer> stock)
	{
		items.values().removeIf(item -> !stock.containsKey(item.getItemId()));
	}

	/**
	 * Call once per game tick while observing. If something we sold should have drained on a predicted tick and
	 * did not, twice in a row, the phase is wrong and gets dropped. Returns true when that happened.
	 */
	public boolean tick(int now, int fallbackInterval)
	{
		if (!isObserving() || !timer.hasPhase() || soldRemaining() == 0)
		{
			return false;
		}
		return timer.checkExpected(now, fallbackInterval);
	}

	public int soldRemaining()
	{
		int total = 0;
		for (TrackedItem item : items.values())
		{
			total += item.getSold();
		}
		return total;
	}

	public TrackedItem get(int itemId)
	{
		return items.get(itemId);
	}

	public List<TrackedItem> allItems()
	{
		return Collections.unmodifiableList(new ArrayList<>(items.values()));
	}

	public List<TrackedItem> soldItems()
	{
		List<TrackedItem> result = new ArrayList<>();
		for (TrackedItem item : items.values())
		{
			if (item.getSold() > 0)
			{
				result.add(item);
			}
		}
		return Collections.unmodifiableList(result);
	}
}
