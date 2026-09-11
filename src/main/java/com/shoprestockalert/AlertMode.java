package com.shoprestockalert;

public enum AlertMode
{
	WHILE_SOLD("While sold items remain"),
	SHOP_OPEN("While the shop is open"),
	ALWAYS("Always");

	private final String label;

	AlertMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
