package com.shoprestockalert;

/**
 * One shop item we have seen change. Remembers when the restock timer last moved it and how far apart those moves are.
 */
public class TrackedItem
{
	public static final int UNKNOWN = -1;

	// a gap this far off a multiple of the interval still counts as the same timer
	private static final int TOLERANCE = 1;
	private static final int STRIKES_TO_FORGET = 2;
	// two player trades a few ticks apart must not be mistaken for a timer
	private static final int MIN_INTERVAL = 5;
	// predicted ticks that came and went with no change before the item is dropped (fully stocked, or wrong timer)
	static final int MISSES_TO_FORGET = 2;

	private final int itemId;
	private int quantity;
	// how many of this item we sold that are still sitting in the shop
	private int sold;
	private int lastChangeTick = UNKNOWN;
	private int interval = UNKNOWN;
	private int strikes;
	private int expectedTick = UNKNOWN;
	private int misses;

	public TrackedItem(int itemId)
	{
		this.itemId = itemId;
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getQuantity()
	{
		return quantity;
	}

	void setQuantity(int quantity)
	{
		this.quantity = quantity;
	}

	public int getSold()
	{
		return sold;
	}

	void addSold(int delta)
	{
		sold = Math.max(0, sold + delta);
	}

	public int getLastChangeTick()
	{
		return lastChangeTick;
	}

	public int getInterval()
	{
		return interval;
	}

	public boolean hasInterval()
	{
		return interval != UNKNOWN;
	}

	/**
	 * The restock timer moved this item by one at the given tick.
	 * observingSince is the tick we have been watching the shop continuously from, or UNKNOWN.
	 * Returns false when the change does not fit the known timer and was ignored.
	 */
	boolean observe(int tick, int observingSince)
	{
		if (lastChangeTick != UNKNOWN)
		{
			int gap = tick - lastChangeTick;
			if (gap <= 0)
			{
				return false;
			}
			boolean watchedWholeGap = observingSince != UNKNOWN && lastChangeTick >= observingSince;
			if (interval == UNKNOWN)
			{
				if (watchedWholeGap && gap >= MIN_INTERVAL)
				{
					interval = gap;
				}
			}
			else if (!fitsInterval(gap, interval))
			{
				// probably another player trading one, unless it keeps happening
				strikes++;
				if (strikes >= STRIKES_TO_FORGET)
				{
					strikes = 0;
					interval = watchedWholeGap && gap >= MIN_INTERVAL ? gap : UNKNOWN;
					lastChangeTick = tick;
				}
				return false;
			}
			else
			{
				strikes = 0;
			}
		}
		lastChangeTick = tick;
		misses = 0;
		expectedTick = UNKNOWN;
		return true;
	}

	/**
	 * Called every tick while the shop is open. Notes when a predicted change is due and counts the ones that never came.
	 * Returns true when the item should be forgotten.
	 */
	boolean checkExpected(int now, int fallbackInterval)
	{
		if (expectedTick != UNKNOWN && now > expectedTick + TOLERANCE)
		{
			expectedTick = UNKNOWN;
			misses++;
			if (misses >= MISSES_TO_FORGET)
			{
				return true;
			}
		}
		if (expectedTick == UNKNOWN && nextChangeTick(now, fallbackInterval) == now)
		{
			expectedTick = now;
		}
		return false;
	}

	public int getMisses()
	{
		return misses;
	}

	static boolean fitsInterval(int gap, int interval)
	{
		int remainder = gap % interval;
		return remainder <= TOLERANCE || interval - remainder <= TOLERANCE;
	}

	/**
	 * Tick of the next timer move, using fallback when the interval has not been learned yet. UNKNOWN when never seen.
	 */
	public int nextChangeTick(int now, int fallbackInterval)
	{
		if (lastChangeTick == UNKNOWN)
		{
			return UNKNOWN;
		}
		int step = interval != UNKNOWN ? interval : fallbackInterval;
		if (step <= 0)
		{
			return UNKNOWN;
		}
		int elapsed = now - lastChangeTick;
		if (elapsed <= 0)
		{
			return lastChangeTick + step;
		}
		int periods = (elapsed + step - 1) / step;
		return lastChangeTick + periods * step;
	}
}
