package com.orbclickthrough;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
		name = "Orb Clickthrough",
		description = "Makes selected minimap orb buttons click-through with a configurable hotkey.",
		tags = {"orbs", "orb", "minimap", "clickthrough", "hotkey"}
)
public class OrbClickthroughPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "orbclickthrough";

	// Minimap orb widget component IDs
	private static final int ACTIVITY_BUTTON = InterfaceID.Orbs.CR_BUTTON;
	private static final int ACTIVITY_BACKING = InterfaceID.Orbs.CR_BACKING;

	private static final int XP_DROPS = InterfaceID.Orbs.XP_DROPS;

	private static final int HEALTH_BUTTON = InterfaceID.Orbs.HEALTHBUTTON;
	private static final int HEALTH_BACKING = InterfaceID.Orbs.HEALTH_BACKING;

	private static final int PRAYER_BUTTON = InterfaceID.Orbs.PRAYERBUTTON;
	private static final int PRAYER_BACKING = InterfaceID.Orbs.PRAYER_BACKING;

	private static final int RUN_BUTTON = InterfaceID.Orbs.RUNBUTTON;
	private static final int RUNENERGY_BACKING = InterfaceID.Orbs.RUNENERGY_BACKING;

	private static final int SPEC_BUTTON = InterfaceID.Orbs.SPECBUTTON;
	private static final int SPECENERGY_BACKING = InterfaceID.Orbs.SPECENERGY_BACKING;

	private static final int STORE_BUTTON = InterfaceID.Orbs.STORE_BUTTON;
	private static final int STORE_BACKING = InterfaceID.Orbs.STORE_BACKING;

	private static final int WORLDMAP = InterfaceID.Orbs.WORLDMAP;
	private static final int WORLDMAP_BACKING = InterfaceID.Orbs.WORLDMAP_BACKING;
	private static final int WORLDMAP_TOOLTIP = InterfaceID.Orbs.TOOLTIP;

	// Base game wiki orb
	private static final int WIKI_ICON = InterfaceID.Orbs.WIKI_ICON;

	// Wiki orb parent container.
	// Used to target the orb created by Wiki plugin, a dynamic replacement child.
	private static final int WIKI_CONTAINER = InterfaceID.Orbs.WIKI;

	// Compass/logout widgets.
	private static final int COMPASS_CLICK = InterfaceID.ToplevelPreEoc.COMPASSCLICK;
	private static final int COMPASS_NOCLICK_CHILD_INDEX = 0;
	private static final int COMPASS_ACTION_CHILD_INDEX = 1;


	private static final int LOGOUT_STONE = InterfaceID.ToplevelPreEoc.STONE10;

	// Radar noclick regions that cover the world map/wiki/logout/radar orb areas.
	// Resizable Modern
	private static final int MODERN_MAP_NOCLICK_0 = InterfaceID.ToplevelPreEoc.MAP_NOCLICK_0; // Radar/logout
	private static final int MODERN_MAP_NOCLICK_3 = InterfaceID.ToplevelPreEoc.MAP_NOCLICK_3;
	private static final int MODERN_MAP_NOCLICK_4 = InterfaceID.ToplevelPreEoc.MAP_NOCLICK_4;
	private static final int MODERN_MAP_NOCLICK_5 = InterfaceID.ToplevelPreEoc.MAP_NOCLICK_5;

	// Resizable Classic
	private static final int CLASSIC_MAP_NOCLICK_0 = InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_0; // Radar/logout
	private static final int CLASSIC_MAP_NOCLICK_3 = InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_3;
	private static final int CLASSIC_MAP_NOCLICK_4 = InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_4;
	private static final int CLASSIC_MAP_NOCLICK_5 = InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_5;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OrbClickthroughConfig config;

	@Inject
	private OrbWidgetTransformer widgetTransformer;

	private HotkeyListener hotkeyListener;

	private boolean hotkeyHeld;
	private boolean toggleActive;

	@Override
	protected void startUp()
	{
		log.debug("Orb Clickthrough started");

		hotkeyListener = new HotkeyListener(() -> config.hotkey())
		{
			@Override
			public void hotkeyPressed()
			{
				if (config.activationMode() == OrbClickthroughActivationMode.TOGGLE_CLICK_THROUGH)
				{
					toggleActive = !toggleActive;
				}
				else
				{
					hotkeyHeld = true;
				}

				clientThread.invokeLater(OrbClickthroughPlugin.this::syncState);
			}

			@Override
			public void hotkeyReleased()
			{
				if (config.activationMode() != OrbClickthroughActivationMode.TOGGLE_CLICK_THROUGH)
				{
					hotkeyHeld = false;
					clientThread.invokeLater(OrbClickthroughPlugin.this::syncState);
				}
			}
		};

		keyManager.registerKeyListener(hotkeyListener);
		clientThread.invokeLater(this::syncState);
	}

	@Override
	protected void shutDown()
	{
		if (hotkeyListener != null)
		{
			keyManager.unregisterKeyListener(hotkeyListener);
			hotkeyListener = null;
		}

		clientThread.invokeLater(() ->
		{
			widgetTransformer.restoreEverythingChangedByUs();
			hotkeyHeld = false;
			toggleActive = false;
		});

		log.debug("Orb Clickthrough stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			widgetTransformer.restoreEverythingChangedByUs();
			hotkeyHeld = false;
			toggleActive = false;
			return;
		}

		clientThread.invokeLater(this::syncState);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		clientThread.invokeLater(() ->
		{
			widgetTransformer.restoreEverythingChangedByUs();
			hotkeyHeld = false;
			toggleActive = false;
			syncState();
		});
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		syncState();
	}

	private void syncState()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (shouldApplyNow())
		{
			applyConfiguredOrbChanges();
		}
		else
		{
			widgetTransformer.restoreEverythingChangedByUs();
		}
	}

	private boolean shouldApplyNow()
	{
		switch (config.activationMode())
		{
			case HOLD_TO_CLICK_THROUGH:
				return hotkeyHeld;
			case HOLD_TO_RESTORE_CLICKS:
				return !hotkeyHeld;
			case TOGGLE_CLICK_THROUGH:
				return toggleActive;
			default:
				return false;
		}
	}

	private void applyConfiguredOrbChanges()
	{
		if (config.hideWorldMapTooltip())
		{
			widgetTransformer.hideWidget(WORLDMAP_TOOLTIP);
		}

		if (config.manageHealthOrb())
		{
			widgetTransformer.allowClickThrough(HEALTH_BACKING);
			widgetTransformer.allowClickThrough(HEALTH_BUTTON);
			widgetTransformer.clearActions(HEALTH_BUTTON);
		}

		if (config.managePrayerOrb())
		{
			widgetTransformer.allowClickThrough(PRAYER_BACKING);
			widgetTransformer.allowClickThrough(PRAYER_BUTTON);
			widgetTransformer.clearActions(PRAYER_BUTTON);
		}

		if (config.manageRunOrb())
		{
			widgetTransformer.allowClickThrough(RUNENERGY_BACKING);
			widgetTransformer.allowClickThrough(RUN_BUTTON);
			widgetTransformer.clearActions(RUN_BUTTON);
		}

		if (config.manageSpecialAttackOrb())
		{
			widgetTransformer.allowClickThrough(SPECENERGY_BACKING);
			widgetTransformer.allowClickThrough(SPEC_BUTTON);
			widgetTransformer.clearActions(SPEC_BUTTON);
		}

		if (config.manageWorldMapOrb())
		{
			widgetTransformer.allowClickThrough(WORLDMAP_BACKING);
			widgetTransformer.allowClickThrough(WORLDMAP);
			widgetTransformer.clearActions(WORLDMAP);
		}

		if (config.manageXpOrb())
		{
			widgetTransformer.allowClickThrough(XP_DROPS);
			widgetTransformer.clearActions(XP_DROPS);
		}

		if (config.manageActivityOrb())
		{
			widgetTransformer.allowClickThrough(ACTIVITY_BACKING);
			widgetTransformer.allowClickThrough(ACTIVITY_BUTTON);
			widgetTransformer.clearActions(ACTIVITY_BUTTON);
		}

		if (config.manageWikiOrb())
		{
			widgetTransformer.allowClickThrough(WIKI_ICON);
			widgetTransformer.clearTargetVerb(WIKI_ICON);
			widgetTransformer.clearActions(WIKI_ICON);

			Widget wikiContainer = client.getWidget(WIKI_CONTAINER);

			if (wikiContainer != null)
			{
				widgetTransformer.allowClickThrough(wikiContainer);
				widgetTransformer.clearTargetVerb(wikiContainer);
				widgetTransformer.clearActions(wikiContainer);

				// The Wiki plugin adds its replacement orb as a dynamic child of WIKI_CONTAINER.
				Widget[] dynamicChildren = wikiContainer.getDynamicChildren();

				if (dynamicChildren != null)
				{
					for (Widget child : dynamicChildren)
					{
						if (child == null)
						{
							continue;
						}

						widgetTransformer.allowClickThrough(child);
						widgetTransformer.clearTargetVerb(child);
						widgetTransformer.clearActions(child);
					}
				}
			}
		}

		if (config.manageWorldMapOrb() || config.manageWikiOrb())
		{
			offsetMapNoClickRegions();
		}

		if (config.manageStoreOrb())
		{
			widgetTransformer.allowClickThrough(STORE_BACKING);
			widgetTransformer.allowClickThrough(STORE_BUTTON);
			widgetTransformer.clearActions(STORE_BUTTON);
		}

		if (config.manageCompassOrb())
		{
			Widget compassClick = client.getWidget(COMPASS_CLICK);

			if (compassClick != null)
			{
				Widget compassActionChild = compassClick.getChild(COMPASS_ACTION_CHILD_INDEX);

				if (compassActionChild != null)
				{
					widgetTransformer.clearActions(compassActionChild);
				}

				Widget compassNoClickChild = compassClick.getChild(COMPASS_NOCLICK_CHILD_INDEX);

				if (compassNoClickChild != null)
				{
					widgetTransformer.allowClickThrough(compassNoClickChild);
				}
			}
		}

		if (config.manageLogoutOrb())
		{
			widgetTransformer.clearActions(LOGOUT_STONE);
		}

		if (config.manageCompassOrb() || config.manageLogoutOrb())
		{
			widgetTransformer.patchCompassLogoutNoClickRegions(MODERN_MAP_NOCLICK_0);
			widgetTransformer.patchCompassLogoutNoClickRegions(CLASSIC_MAP_NOCLICK_0);
		}
		else
		{
			widgetTransformer.restoreCompassLogoutNoClickRegions(MODERN_MAP_NOCLICK_0);
			widgetTransformer.restoreCompassLogoutNoClickRegions(CLASSIC_MAP_NOCLICK_0);
		}
	}

	private void offsetMapNoClickRegions()
	{
		// Fixed layout: do nothing. Only the resizable layouts need these radar noclick offsets.
		if (!client.isResized())
		{
			return;
		}

		widgetTransformer.offsetWorldMapWikiNoClickRegions(
				MODERN_MAP_NOCLICK_3,
				MODERN_MAP_NOCLICK_4,
				MODERN_MAP_NOCLICK_5,
				CLASSIC_MAP_NOCLICK_3,
				CLASSIC_MAP_NOCLICK_4,
				CLASSIC_MAP_NOCLICK_5
		);
	}

	@Provides
	OrbClickthroughConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OrbClickthroughConfig.class);
	}
}