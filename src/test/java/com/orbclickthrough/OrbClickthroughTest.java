package com.orbclickthrough;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.client.config.Keybind;
import org.junit.Test;

public class OrbClickthroughTest
{
	@Test
	public void defaultConfigValuesAreExpected()
	{
		OrbClickthroughConfig config = new OrbClickthroughConfig() {};

		assertEquals(Keybind.SHIFT, config.hotkey());
		assertEquals(OrbClickthroughActivationMode.HOLD_TO_RESTORE_CLICKS, config.activationMode());

		assertTrue(config.manageHealthOrb());
		assertFalse(config.managePrayerOrb());
		assertFalse(config.manageRunOrb());
		assertFalse(config.manageSpecialAttackOrb());

		assertTrue(config.manageWorldMapOrb());
		assertTrue(config.manageXpOrb());
		assertTrue(config.manageActivityOrb());
		assertTrue(config.manageWikiOrb());
		assertTrue(config.manageStoreOrb());

		assertTrue(config.hideWorldMapTooltip());
	}
}