package com.shoprestockalert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class RestockTrackerTest
{
	private static final int KNIFE = 946;
	private static final int LOBSTER = 379;
	private static final int BOLTS = 9245;

	private static Map<Integer, Integer> stock(Object... pairs)
	{
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			map.put((Integer) pairs[i], (Integer) pairs[i + 1]);
		}
		return map;
	}

	@Test
	public void firstSnapshotIsOnlyABaseline()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(10);
		assertTrue(tracker.update(10, stock(KNIFE, 3), false).isEmpty());
		assertFalse(tracker.getTimer().hasPhase());
	}

	@Test
	public void learnsIntervalFromTwoTimerTicks()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		assertEquals(Collections.singletonList(KNIFE), tracker.update(40, stock(KNIFE, 4), false));
		ShopTimer timer = tracker.getTimer();
		assertFalse(timer.hasInterval());
		// only the phase is known, so the fallback interval is used
		assertEquals(140, timer.nextChangeTick(50, 100));

		tracker.update(140, stock(KNIFE, 5), false);
		assertEquals(100, timer.getInterval());
		assertEquals(240, timer.nextChangeTick(141, 30));
		assertEquals(240, timer.nextChangeTick(240, 30));
		assertEquals(340, timer.nextChangeTick(241, 30));
	}

	@Test
	public void wholeShopMovingTogetherIsOneObservation()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3, BOLTS, 10, LOBSTER, 5), false);
		assertEquals(Arrays.asList(KNIFE, BOLTS, LOBSTER).size(), tracker.update(65, stock(KNIFE, 4, BOLTS, 11, LOBSTER, 4), false).size());
		tracker.update(165, stock(KNIFE, 5, BOLTS, 12, LOBSTER, 3), false);
		assertEquals(100, tracker.getTimer().getInterval());
		assertEquals(165, tracker.getTimer().getLastChangeTick());
	}

	@Test
	public void ownTradeIsNotATimerTick()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		assertTrue(tracker.update(5, stock(KNIFE, 2), true).isEmpty());
		assertTrue(tracker.update(6, stock(KNIFE, 2, LOBSTER, 10), true).isEmpty());
		assertEquals(10, tracker.get(LOBSTER).getSold());
		assertEquals(10, tracker.soldRemaining());
		assertFalse(tracker.getTimer().hasPhase());
	}

	@Test
	public void timerSurvivesTheLastSoldItemDrainingAway()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		// sell one lobster, the only thing in the shop that will move
		tracker.update(1, stock(KNIFE, 3, LOBSTER, 1), true);
		assertEquals(Collections.singletonList(LOBSTER), tracker.update(50, stock(KNIFE, 3), false));
		assertNull(tracker.get(LOBSTER));
		assertEquals(0, tracker.soldRemaining());
		assertTrue(tracker.getTimer().hasPhase());
		assertEquals(150, tracker.getTimer().nextChangeTick(60, 100));
		// selling again later uses the phase already known
		tracker.update(120, stock(KNIFE, 3, LOBSTER, 2), true);
		assertEquals(2, tracker.get(LOBSTER).getSold());
		assertEquals(150, tracker.getTimer().nextChangeTick(121, 100));
		assertEquals(Collections.singletonList(LOBSTER), tracker.update(150, stock(KNIFE, 3, LOBSTER, 1), false));
		assertEquals(100, tracker.getTimer().getInterval());
		assertEquals(1, tracker.get(LOBSTER).getSold());
	}

	@Test
	public void bulkChangeIsAnotherPlayer()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 5), false);
		assertTrue(tracker.update(7, stock(KNIFE, 1), false).isEmpty());
		assertEquals(1, tracker.get(KNIFE).getQuantity());
		assertFalse(tracker.getTimer().hasPhase());
	}

	@Test
	public void offScheduleSingleChangeDoesNotMovePhase()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(100, stock(KNIFE, 4), false);
		tracker.update(200, stock(KNIFE, 5), false);
		ShopTimer timer = tracker.getTimer();
		assertEquals(100, timer.getInterval());
		// someone bought one at 230
		assertTrue(tracker.update(230, stock(KNIFE, 4), false).isEmpty());
		assertEquals(300, timer.nextChangeTick(231, 100));
		// and the real tick still lands at 300
		assertEquals(Collections.singletonList(KNIFE), tracker.update(300, stock(KNIFE, 5), false));
	}

	@Test
	public void twoStrikesRelearnTheInterval()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(100, stock(KNIFE, 4), false);
		tracker.update(200, stock(KNIFE, 5), false);
		tracker.update(230, stock(KNIFE, 6), false);
		tracker.update(260, stock(KNIFE, 7), false);
		assertEquals(60, tracker.getTimer().getInterval());
		assertEquals(260, tracker.getTimer().getLastChangeTick());
	}

	@Test
	public void reopeningKeepsIntervalAndResyncsPhase()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(100, stock(KNIFE, 4), false);
		tracker.update(200, stock(KNIFE, 5), false);
		tracker.stopObserving();
		// while closed the prediction carries on from the last seen tick
		assertEquals(500, tracker.getTimer().nextChangeTick(450, 100));
		tracker.startObserving(450);
		tracker.update(450, stock(KNIFE, 2), false);
		assertEquals(Collections.singletonList(KNIFE), tracker.update(501, stock(KNIFE, 3), false));
		assertEquals(100, tracker.getTimer().getInterval());
		assertEquals(601, tracker.getTimer().nextChangeTick(502, 100));
	}

	@Test
	public void missedTicksOnlyCountWhileSoldStockShouldBeDraining()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(100, stock(KNIFE, 4), false);
		tracker.update(200, stock(KNIFE, 5), false);
		// nothing sold: a fully stocked shop sits still and the phase is kept
		for (int now = 201; now <= 500; now++)
		{
			assertFalse(tracker.tick(now, 100));
		}
		assertTrue(tracker.getTimer().hasPhase());

		// sold stock that fails to drain on two predicted ticks means the phase is wrong
		tracker.update(510, stock(KNIFE, 5, LOBSTER, 3), true);
		boolean dropped = false;
		for (int now = 511; now <= 705 && !dropped; now++)
		{
			dropped = tracker.tick(now, 100);
		}
		assertTrue(dropped);
		assertFalse(tracker.getTimer().hasPhase());
		// the sold count is still known so it can be re-timed
		assertEquals(3, tracker.get(LOBSTER).getSold());
	}

	@Test
	public void veryShortGapsAreNotLearned()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(10, stock(KNIFE, 4), false);
		tracker.update(12, stock(KNIFE, 5), false);
		assertFalse(tracker.getTimer().hasInterval());
	}

	@Test
	public void overlayFormats()
	{
		assertEquals("now", RestockOverlay.formatTicks(0, true, true));
		assertEquals("12t", RestockOverlay.formatTicks(12, true, false));
		assertEquals("~12t 7.2s", RestockOverlay.formatTicks(12, false, true));
		assertEquals("45s", RestockOverlay.formatDuration(75));
		assertEquals("12.5m", RestockOverlay.formatDuration(1250));
	}
}
