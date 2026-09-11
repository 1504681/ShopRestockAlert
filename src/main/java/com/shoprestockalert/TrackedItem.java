package com.shoprestockalert;

/**
 * One shop item we have seen change: its current stock and how many of it we sold that are still in the shop.
 */
public class TrackedItem
{
	private final int itemId;
	private int quantity;
	private int sold;

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
}
