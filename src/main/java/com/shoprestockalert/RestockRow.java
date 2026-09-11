package com.shoprestockalert;

/**
 * One item line of the overlay, computed once per game tick.
 */
public class RestockRow
{
	public static final int UNKNOWN = -1;

	private final int itemId;
	private final String name;
	private final int quantity;
	private final int sold;
	// ticks until everything we sold has drained out of the shop, UNKNOWN before the timer has been seen
	private final int clearTicks;

	public RestockRow(int itemId, String name, int quantity, int sold, int clearTicks)
	{
		this.itemId = itemId;
		this.name = name;
		this.quantity = quantity;
		this.sold = sold;
		this.clearTicks = clearTicks;
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

	public int getClearTicks()
	{
		return clearTicks;
	}
}
