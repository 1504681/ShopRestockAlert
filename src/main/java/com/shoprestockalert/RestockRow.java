package com.shoprestockalert;

/**
 * One line of the overlay, computed once per game tick.
 */
public class RestockRow
{
	private final int itemId;
	private final String name;
	private final int quantity;
	private final int sold;
	private final int ticksLeft;
	private final boolean learned;

	public RestockRow(int itemId, String name, int quantity, int sold, int ticksLeft, boolean learned)
	{
		this.itemId = itemId;
		this.name = name;
		this.quantity = quantity;
		this.sold = sold;
		this.ticksLeft = ticksLeft;
		this.learned = learned;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getName()
	{
		return name;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public int getSold()
	{
		return sold;
	}

	public int getTicksLeft()
	{
		return ticksLeft;
	}

	// true when the interval was measured rather than assumed
	public boolean isLearned()
	{
		return learned;
	}
}
