package com.shoprestockalert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RestockTrackerTest
{
	private static final int KNIFE = 946;
	private static final int LOBSTER = 379;

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
		assertTrue(tracker.timedItems().isEmpty());
	}

	@Test
	public void learnsIntervalFromTwoTimerTicks()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		assertEquals(Collections.singletonList(KNIFE), tracker.update(40, stock(KNIFE, 4), false));
		TrackedItem knife = tracker.get(KNIFE);
		assertFalse(knife.hasInterval());
		// only the phase is known, so the fallback interval is used
		assertEquals(140, knife.nextChangeTick(50, 100));

		tracker.update(140, stock(KNIFE, 5), false);
		assertEquals(100, knife.getInterval());
		assertEquals(240, knife.nextChangeTick(141, 30));
		assertEquals(240, knife.nextChangeTick(240, 30));
		assertEquals(340, knife.nextChangeTick(241, 30));
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
		assertTrue(tracker.timedItems().isEmpty());
	}

	@Test
	public void soldItemsCountDownAsTheyDecayAndVanishAtZero()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(1, stock(KNIFE, 3, LOBSTER, 2), true);
		tracker.update(50, stock(KNIFE, 3, LOBSTER, 1), false);
		assertEquals(1, tracker.get(LOBSTER).getSold());
		tracker.update(100, stock(KNIFE, 3), false);
		assertNull(tracker.get(LOBSTER));
	}

	@Test
	public void bulkChangeIsAnotherPlayer()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 5), false);
		assertTrue(tracker.update(7, stock(KNIFE, 1), false).isEmpty());
		assertEquals(1, tracker.get(KNIFE).getQuantity());
		assertTrue(tracker.timedItems().isEmpty());
	}

	@Test
	public void offScheduleSingleChangeDoesNotMovePhase()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(100, stock(KNIFE, 4), false);
		tracker.update(200, stock(KNIFE, 5), false);
		TrackedItem knife = tracker.get(KNIFE);
		assertEquals(100, knife.getInterval());
		// someone bought one at 230
		assertTrue(tracker.update(230, stock(KNIFE, 4), false).isEmpty());
		assertEquals(300, knife.nextChangeTick(231, 100));
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
		TrackedItem knife = tracker.get(KNIFE);
		tracker.update(230, stock(KNIFE, 6), false);
		tracker.update(260, stock(KNIFE, 7), false);
		assertEquals(60, knife.getInterval());
		assertEquals(260, knife.getLastChangeTick());
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
		assertEquals(500, tracker.get(KNIFE).nextChangeTick(450, 100));
		tracker.startObserving(450);
		tracker.update(450, stock(KNIFE, 2), false);
		assertEquals(Collections.singletonList(KNIFE), tracker.update(501, stock(KNIFE, 3), false));
		assertEquals(100, tracker.get(KNIFE).getInterval());
		assertEquals(601, tracker.get(KNIFE).nextChangeTick(502, 100));
	}

	@Test
	public void ticksThatNeverComeDropTheItem()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(100, stock(KNIFE, 4), false);
		tracker.update(200, stock(KNIFE, 5), false);
		for (int now = 201; now <= 300; now++)
		{
			assertTrue(tracker.tick(now, 100).isEmpty());
		}
		// due at 300, nothing happened by 302
		assertTrue(tracker.tick(302, 100).isEmpty());
		assertEquals(1, tracker.get(KNIFE).getMisses());
		List<Integer> dropped = Collections.emptyList();
		for (int now = 303; now <= 402 && dropped.isEmpty(); now++)
		{
			dropped = tracker.tick(now, 100);
		}
		assertEquals(Collections.singletonList(KNIFE), dropped);
		assertNull(tracker.get(KNIFE));
	}

	@Test
	public void tickResetsMissesWhenTheChangeArrives()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(100, stock(KNIFE, 4), false);
		for (int now = 101; now <= 202; now++)
		{
			tracker.tick(now, 100);
		}
		assertEquals(1, tracker.get(KNIFE).getMisses());
		tracker.update(300, stock(KNIFE, 5), false);
		assertEquals(0, tracker.get(KNIFE).getMisses());
		assertEquals(200, tracker.get(KNIFE).getInterval());
	}

	@Test
	public void veryShortGapsAreNotLearned()
	{
		RestockTracker tracker = new RestockTracker();
		tracker.startObserving(0);
		tracker.update(0, stock(KNIFE, 3), false);
		tracker.update(10, stock(KNIFE, 4), false);
		tracker.update(12, stock(KNIFE, 5), false);
		assertFalse(tracker.get(KNIFE).hasInterval());
	}

	@Test
	public void overlayTickFormat()
	{
		assertEquals("now", RestockOverlay.formatTicks(0, true, true));
		assertEquals("12t", RestockOverlay.formatTicks(12, true, false));
		assertEquals("~12t 7.2s", RestockOverlay.formatTicks(12, false, true));
	}
}
