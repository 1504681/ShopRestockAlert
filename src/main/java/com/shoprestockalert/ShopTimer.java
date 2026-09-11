package com.shoprestockalert;

/**
 * The restock timer of one shop. Every item in the shop moves on the same tick, so one phase and one interval
 * describe the whole shop, and they outlive the items that revealed them.
 */
public class ShopTimer
{
	public static final int UNKNOWN = -1;

	// a gap this far off a multiple of the interval still counts as the same timer
	private static final int TOLERANCE = 1;
	private static final int STRIKES_TO_FORGET = 2;
	// two player trades a few ticks apart must not be mistaken for the timer
	private static final int MIN_INTERVAL = 5;
	// predicted ticks that came and went without the sold stock moving before the phase is dropped
	static final int MISSES_TO_FORGET = 3;

	private int lastChangeTick = UNKNOWN;
	private int interval = UNKNOWN;
	private int strikes;
	private int expectedTick = UNKNOWN;
	private int misses;

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

	public boolean hasPhase()
	{
		return lastChangeTick != UNKNOWN;
	}

	public int getMisses()
	{
		return misses;
	}

	public void clear()
	{
		lastChangeTick = UNKNOWN;
		interval = UNKNOWN;
		strikes = 0;
		expectedTick = UNKNOWN;
		misses = 0;
	}

	/**
	 * The timer moved stock at the given tick. observingSince is the tick we have been watching the shop
	 * continuously from, or UNKNOWN. Returns false when the change does not fit the known timer and was ignored.
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

	static boolean fitsInterval(int gap, int interval)
	{
		int remainder = gap % interval;
		return remainder <= TOLERANCE || interval - remainder <= TOLERANCE;
	}

	/**
	 * Called every tick while the shop is open and something in it must move on the next tick.
	 * Counts predicted ticks that never came. Returns true when the phase should be forgotten.
	 */
	boolean checkExpected(int now, int fallbackInterval)
	{
		if (expectedTick != UNKNOWN && now > expectedTick + TOLERANCE)
		{
			expectedTick = UNKNOWN;
			misses++;
			if (misses >= MISSES_TO_FORGET)
			{
				clear();
				return true;
			}
		}
		if (expectedTick == UNKNOWN && nextChangeTick(now, fallbackInterval) == now)
		{
			expectedTick = now;
		}
		return false;
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
		int step = stepOr(fallbackInterval);
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

	public int stepOr(int fallbackInterval)
	{
		return interval != UNKNOWN ? interval : fallbackInterval;
	}
}
