package com.shoprestockalert;

public enum AlertScope
{
	SOONEST("Soonest item"),
	SOLD("Items you sold"),
	ALL("Every item");

	private final String label;

	AlertScope(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
