package com.shoprestockalert;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

// ./gradlew run, or run this from the IDE with -ea
public class ShopRestockAlertLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ShopRestockAlertPlugin.class);
		RuneLite.main(args);
	}
}
