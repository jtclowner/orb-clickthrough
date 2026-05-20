package com.orbclickthrough;

import com.google.inject.Provides;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
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

	// Radar noclick regions that cover the world map and wiki orb area.
	// Resizable Modern
	private static final int MODERN_MAP_NOCLICK_3 = InterfaceID.ToplevelPreEoc.MAP_NOCLICK_3;
	private static final int MODERN_MAP_NOCLICK_4 = InterfaceID.ToplevelPreEoc.MAP_NOCLICK_4;
	private static final int MODERN_MAP_NOCLICK_5 = InterfaceID.ToplevelPreEoc.MAP_NOCLICK_5;

	// Resizable Classic
	private static final int CLASSIC_MAP_NOCLICK_3 = InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_3;
	private static final int CLASSIC_MAP_NOCLICK_4 = InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_4;
	private static final int CLASSIC_MAP_NOCLICK_5 = InterfaceID.ToplevelOsrsStretch.MAP_NOCLICK_5;

	// Shift and shrink the radar noclick regions so the separated orb area can pass clicks through.
	private static final int MAP_NOCLICK_3_X_OFFSET = 20;
	private static final int MAP_NOCLICK_4_X_OFFSET = 35;
	private static final int MAP_NOCLICK_5_X_OFFSET = 40;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OrbClickthroughConfig config;

	private HotkeyListener hotkeyListener;

	private boolean hotkeyHeld;
	private boolean toggleActive;

	private final Set<Widget> hiddenByUs = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Widget> noClickThroughChangedByUs = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Widget> targetVerbChangedByUs = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Widget> actionsChangedByUs = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Set<Integer> boundsChangedByUs = new HashSet<>();

	private final Map<Widget, Boolean> originalNoClickThrough = new IdentityHashMap<>();
	private final Map<Widget, String> originalTargetVerb = new IdentityHashMap<>();
	private final Map<Widget, String[]> originalActions = new IdentityHashMap<>();
	private final Map<Integer, WidgetBounds> originalBounds = new HashMap<>();

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
			restoreEverythingChangedByUs();
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
			restoreEverythingChangedByUs();
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
			restoreEverythingChangedByUs();
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
			restoreEverythingChangedByUs();
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
			hideWidget(WORLDMAP_TOOLTIP);
		}

		if (config.manageHealthOrb())
		{
			allowClickThrough(HEALTH_BACKING);
			allowClickThrough(HEALTH_BUTTON);
			clearActions(HEALTH_BUTTON);
		}

		if (config.managePrayerOrb())
		{
			allowClickThrough(PRAYER_BACKING);
			allowClickThrough(PRAYER_BUTTON);
			clearActions(PRAYER_BUTTON);
		}

		if (config.manageRunOrb())
		{
			allowClickThrough(RUNENERGY_BACKING);
			allowClickThrough(RUN_BUTTON);
			clearActions(RUN_BUTTON);
		}

		if (config.manageSpecialAttackOrb())
		{
			allowClickThrough(SPECENERGY_BACKING);
			allowClickThrough(SPEC_BUTTON);
			clearActions(SPEC_BUTTON);
		}

		if (config.manageWorldMapOrb())
		{
			allowClickThrough(WORLDMAP_BACKING);
			allowClickThrough(WORLDMAP);
			clearActions(WORLDMAP);
		}

		if (config.manageXpOrb())
		{
			allowClickThrough(XP_DROPS);
			clearActions(XP_DROPS);
		}

		if (config.manageActivityOrb())
		{
			allowClickThrough(ACTIVITY_BACKING);
			allowClickThrough(ACTIVITY_BUTTON);
			clearActions(ACTIVITY_BUTTON);
		}

		if (config.manageWikiOrb())
		{
			allowClickThrough(WIKI_ICON);
			clearTargetVerb(WIKI_ICON);
			clearActions(WIKI_ICON);

			Widget wikiContainer = client.getWidget(WIKI_CONTAINER);

			if (wikiContainer != null)
			{
				allowClickThrough(wikiContainer);
				clearTargetVerb(wikiContainer);
				clearActions(wikiContainer);

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

						allowClickThrough(child);
						clearTargetVerb(child);
						clearActions(child);
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
			allowClickThrough(STORE_BACKING);
			allowClickThrough(STORE_BUTTON);
			clearActions(STORE_BUTTON);
		}
	}

	private void hideWidget(int widgetId)
	{
		hideWidget(client.getWidget(widgetId));
	}

	private void hideWidget(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}

		widget.setHidden(true);
		hiddenByUs.add(widget);
	}

	private void allowClickThrough(int widgetId)
	{
		allowClickThrough(client.getWidget(widgetId));
	}

	private void allowClickThrough(Widget widget)
	{
		if (widget == null)
		{
			return;
		}

		if (!noClickThroughChangedByUs.contains(widget))
		{
			originalNoClickThrough.put(widget, widget.getNoClickThrough());
		}

		widget.setNoClickThrough(false);
		noClickThroughChangedByUs.add(widget);
	}

	private void clearTargetVerb(int widgetId)
	{
		clearTargetVerb(client.getWidget(widgetId));
	}

	private void clearTargetVerb(Widget widget)
	{
		if (widget == null)
		{
			return;
		}

		if (!targetVerbChangedByUs.contains(widget))
		{
			originalTargetVerb.put(widget, widget.getTargetVerb());
		}

		widget.setTargetVerb("");
		targetVerbChangedByUs.add(widget);
	}

	private void clearActions(int widgetId)
	{
		clearActions(client.getWidget(widgetId));
	}

	private void clearActions(Widget widget)
	{
		if (widget == null)
		{
			return;
		}

		if (!actionsChangedByUs.contains(widget))
		{
			String[] actions = widget.getActions();
			originalActions.put(widget, actions == null ? null : Arrays.copyOf(actions, actions.length));
		}

		widget.clearActions();
		actionsChangedByUs.add(widget);
	}

	private void offsetMapNoClickRegions()
	{
		// Fixed layout: do nothing. Only the resizable layouts need these radar noclick offsets.
		if (!client.isResized())
		{
			return;
		}

		// Resizable Modern. Missing/inactive widgets are safely ignored.
		offsetWidgetBoundsRightAndShrinkWidth(MODERN_MAP_NOCLICK_3, MAP_NOCLICK_3_X_OFFSET);
		offsetWidgetBoundsRightAndShrinkWidth(MODERN_MAP_NOCLICK_4, MAP_NOCLICK_4_X_OFFSET);
		offsetWidgetBoundsRightAndShrinkWidth(MODERN_MAP_NOCLICK_5, MAP_NOCLICK_5_X_OFFSET);

		// Resizable Classic. Missing/inactive widgets are safely ignored.
		offsetWidgetBoundsRightAndShrinkWidth(CLASSIC_MAP_NOCLICK_3, MAP_NOCLICK_3_X_OFFSET);
		offsetWidgetBoundsRightAndShrinkWidth(CLASSIC_MAP_NOCLICK_4, MAP_NOCLICK_4_X_OFFSET);
		offsetWidgetBoundsRightAndShrinkWidth(CLASSIC_MAP_NOCLICK_5, MAP_NOCLICK_5_X_OFFSET);
	}

	private void offsetWidgetBoundsRightAndShrinkWidth(int widgetId, int xOffset)
	{
		Widget widget = client.getWidget(widgetId);

		if (widget == null)
		{
			return;
		}

		if (!boundsChangedByUs.contains(widgetId))
		{
			originalBounds.put(
					widgetId,
					new WidgetBounds(widget.getOriginalX(), widget.getOriginalWidth())
			);
		}

		WidgetBounds originalValue = originalBounds.get(widgetId);

		if (originalValue == null)
		{
			return;
		}

		widget.setOriginalX(originalValue.originalX + xOffset);
		widget.setOriginalWidth(Math.max(0, originalValue.originalWidth - xOffset));
		widget.revalidate();

		boundsChangedByUs.add(widgetId);
	}

	private void restoreEverythingChangedByUs()
	{
		restoreHiddenWidgets();
		restoreClickThroughWidgets();
		restoreWidgetBounds();
		restoreTargetVerbs();
		restoreActions();
	}

	private void restoreHiddenWidgets()
	{
		for (Widget widget : new HashSet<>(hiddenByUs))
		{
			if (widget != null)
			{
				widget.setHidden(false);
			}

			hiddenByUs.remove(widget);
		}
	}

	private void restoreClickThroughWidgets()
	{
		for (Widget widget : new HashSet<>(noClickThroughChangedByUs))
		{
			Boolean originalValue = originalNoClickThrough.remove(widget);

			if (widget != null && originalValue != null)
			{
				widget.setNoClickThrough(originalValue);
			}

			noClickThroughChangedByUs.remove(widget);
		}
	}

	private void restoreWidgetBounds()
	{
		for (Integer widgetId : new HashSet<>(boundsChangedByUs))
		{
			Widget widget = client.getWidget(widgetId);
			WidgetBounds originalValue = originalBounds.remove(widgetId);

			if (widget != null && originalValue != null)
			{
				widget.setOriginalX(originalValue.originalX);
				widget.setOriginalWidth(originalValue.originalWidth);
				widget.revalidate();
			}

			boundsChangedByUs.remove(widgetId);
		}
	}

	private void restoreTargetVerbs()
	{
		for (Widget widget : new HashSet<>(targetVerbChangedByUs))
		{
			String originalValue = originalTargetVerb.remove(widget);

			if (widget != null)
			{
				widget.setTargetVerb(originalValue);
			}

			targetVerbChangedByUs.remove(widget);
		}
	}

	private void restoreActions()
	{
		for (Widget widget : new HashSet<>(actionsChangedByUs))
		{
			String[] originalValue = originalActions.remove(widget);

			if (widget != null)
			{
				widget.clearActions();

				if (originalValue != null)
				{
					for (int i = 0; i < originalValue.length; i++)
					{
						widget.setAction(i, originalValue[i]);
					}
				}
			}

			actionsChangedByUs.remove(widget);
		}
	}

	private static final class WidgetBounds
	{
		private final int originalX;
		private final int originalWidth;

		private WidgetBounds(int originalX, int originalWidth)
		{
			this.originalX = originalX;
			this.originalWidth = originalWidth;
		}
	}

	@Provides
	OrbClickthroughConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OrbClickthroughConfig.class);
	}
}