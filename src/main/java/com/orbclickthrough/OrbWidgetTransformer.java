package com.orbclickthrough;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

@Singleton
public class OrbWidgetTransformer
{
    // Small always-on trims so noclick regions do not overlap exposed orb edges.
    private static final int MAP_NOCLICK_1_SHRINK_LEFT = 4;
    private static final int MAP_NOCLICK_2_SHRINK_LEFT = 4;
    private static final int MAP_NOCLICK_4_SHRINK_LEFT = 6;

    // Existing world map/wiki/radar-side trims, now applied whenever the plugin is enabled.
    private static final int MAP_NOCLICK_3_SHRINK_RIGHT = 20;
    private static final int MAP_NOCLICK_4_SHRINK_RIGHT = 33;
    private static final int MAP_NOCLICK_5_SHRINK_RIGHT = 40;

    // Resize native MAP_NOCLICK_0 to expose the compass/logout orb area.
    private static final int MAP_NOCLICK_0_SHRINK_WIDTH = 37;
    private static final int MAP_NOCLICK_0_MOVE_LEFT = 11;
    private static final int MAP_NOCLICK_0_SHIFT_DOWN = 24;
    private static final int MAP_NOCLICK_0_HEIGHT = 24;

    // Extra dynamic blocker created by this plugin.
    // Needed because shrinking MAP_NOCLICK_0 exposes part of the minimap between the logout/radar widget areas.
    private static final int MAP_NOCLICK_EXTRA_MOVE_LEFT = 18;
    private static final int MAP_NOCLICK_EXTRA_MOVE_UP = 22;
    private static final int MAP_NOCLICK_EXTRA_EXTRA_WIDTH = -34;
    private static final int MAP_NOCLICK_EXTRA_HEIGHT = 23;

    private final Client client;

    private final Set<Integer> compassLogoutNoClickPatched = new HashSet<>();
    private final Map<Integer, Widget> compassLogoutNoClickExtras = new HashMap<>();

    private final Set<Widget> hiddenByUs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Widget> noClickThroughChangedByUs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Widget> targetVerbChangedByUs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Widget> actionsChangedByUs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Integer> boundsChangedByUs = new HashSet<>();

    private final Map<Widget, Boolean> originalNoClickThrough = new IdentityHashMap<>();
    private final Map<Widget, String> originalTargetVerb = new IdentityHashMap<>();
    private final Map<Widget, String[]> originalActions = new IdentityHashMap<>();
    private final Map<Integer, WidgetBounds> originalBounds = new HashMap<>();

    @Inject
    public OrbWidgetTransformer(Client client)
    {
        this.client = client;
    }

    public void hideWidget(int widgetId)
    {
        hideWidget(client.getWidget(widgetId));
    }

    public void hideWidget(Widget widget)
    {
        if (widget == null || widget.isHidden())
        {
            return;
        }

        widget.setHidden(true);
        hiddenByUs.add(widget);
    }

    public void allowClickThrough(int widgetId)
    {
        allowClickThrough(client.getWidget(widgetId));
    }

    public void allowClickThrough(Widget widget)
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

    public void clearTargetVerb(int widgetId)
    {
        clearTargetVerb(client.getWidget(widgetId));
    }

    public void clearTargetVerb(Widget widget)
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

    public void clearActions(int widgetId)
    {
        clearActions(client.getWidget(widgetId));
    }

    public void clearActions(Widget widget)
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

    public void updateNoClickRegions(
            int modernMapNoClick1,
            int modernMapNoClick2,
            int modernMapNoClick3,
            int modernMapNoClick4,
            int modernMapNoClick5,
            int classicMapNoClick1,
            int classicMapNoClick2,
            int classicMapNoClick3,
            int classicMapNoClick4,
            int classicMapNoClick5
    )
    {
        shrinkFromLeft(modernMapNoClick1, MAP_NOCLICK_1_SHRINK_LEFT);
        shrinkFromLeft(modernMapNoClick2, MAP_NOCLICK_2_SHRINK_LEFT);
        shrinkFromRight(modernMapNoClick3, MAP_NOCLICK_3_SHRINK_RIGHT);
        shrinkFromLeftAndRight(modernMapNoClick4, MAP_NOCLICK_4_SHRINK_LEFT, MAP_NOCLICK_4_SHRINK_RIGHT);
        shrinkFromRight(modernMapNoClick5, MAP_NOCLICK_5_SHRINK_RIGHT);

        shrinkFromLeft(classicMapNoClick1, MAP_NOCLICK_1_SHRINK_LEFT);
        shrinkFromLeft(classicMapNoClick2, MAP_NOCLICK_2_SHRINK_LEFT);
        shrinkFromRight(classicMapNoClick3, MAP_NOCLICK_3_SHRINK_RIGHT);
        shrinkFromLeftAndRight(classicMapNoClick4, MAP_NOCLICK_4_SHRINK_LEFT, MAP_NOCLICK_4_SHRINK_RIGHT);
        shrinkFromRight(classicMapNoClick5, MAP_NOCLICK_5_SHRINK_RIGHT);
    }

    public void restoreNoClickRegions(
            int modernMapNoClick0,
            int modernMapNoClick1,
            int modernMapNoClick2,
            int modernMapNoClick3,
            int modernMapNoClick4,
            int modernMapNoClick5,
            int classicMapNoClick0,
            int classicMapNoClick1,
            int classicMapNoClick2,
            int classicMapNoClick3,
            int classicMapNoClick4,
            int classicMapNoClick5
    )
    {
        restoreCompassLogoutNoClickRegions(modernMapNoClick0);
        restoreWidgetBounds(modernMapNoClick1);
        restoreWidgetBounds(modernMapNoClick2);
        restoreWidgetBounds(modernMapNoClick3);
        restoreWidgetBounds(modernMapNoClick4);
        restoreWidgetBounds(modernMapNoClick5);

        restoreCompassLogoutNoClickRegions(classicMapNoClick0);
        restoreWidgetBounds(classicMapNoClick1);
        restoreWidgetBounds(classicMapNoClick2);
        restoreWidgetBounds(classicMapNoClick3);
        restoreWidgetBounds(classicMapNoClick4);
        restoreWidgetBounds(classicMapNoClick5);
    }

    /*
     * Shrinks the widget from the visual left edge:
     * - normal left-anchored widgets: move X right and reduce width
     * - right-anchored widgets: reduce width only
     *
     * This keeps the visual right edge roughly fixed.
     */
    private void shrinkFromLeft(int widgetId, int pixels)
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
                    new WidgetBounds(
                            widget.getOriginalX(),
                            widget.getOriginalY(),
                            widget.getOriginalWidth(),
                            widget.getOriginalHeight()
                    )
            );
        }

        WidgetBounds originalValue = originalBounds.get(widgetId);

        if (originalValue == null)
        {
            return;
        }

        if (widget.getXPositionMode() == 2)
        {
            widget.setOriginalX(originalValue.originalX);
        }
        else
        {
            widget.setOriginalX(originalValue.originalX + pixels);
        }

        widget.setOriginalWidth(Math.max(0, originalValue.originalWidth - pixels));
        widget.revalidate();

        boundsChangedByUs.add(widgetId);
    }

    /*
     * Shrinks the widget from the visual right edge:
     * - normal left-anchored widgets: reduce width only
     * - right-anchored widgets: move X right and reduce width
     *
     * This keeps the visual left edge roughly fixed.
     */
    private void shrinkFromRight(int widgetId, int pixels)
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
                    new WidgetBounds(
                            widget.getOriginalX(),
                            widget.getOriginalY(),
                            widget.getOriginalWidth(),
                            widget.getOriginalHeight()
                    )
            );
        }

        WidgetBounds originalValue = originalBounds.get(widgetId);

        if (originalValue == null)
        {
            return;
        }

        if (widget.getXPositionMode() == 2)
        {
            widget.setOriginalX(originalValue.originalX + pixels);
        }
        else
        {
            widget.setOriginalX(originalValue.originalX);
        }

        widget.setOriginalWidth(Math.max(0, originalValue.originalWidth - pixels));
        widget.revalidate();

        boundsChangedByUs.add(widgetId);
    }

    /*
     * Applies both edge trims from the same saved original bounds.
     */
    private void shrinkFromLeftAndRight(int widgetId, int shrinkLeftPixels, int shrinkRightPixels)
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
                    new WidgetBounds(
                            widget.getOriginalX(),
                            widget.getOriginalY(),
                            widget.getOriginalWidth(),
                            widget.getOriginalHeight()
                    )
            );
        }

        WidgetBounds originalValue = originalBounds.get(widgetId);

        if (originalValue == null)
        {
            return;
        }

        int newWidth = Math.max(0, originalValue.originalWidth - shrinkLeftPixels - shrinkRightPixels);

        if (widget.getXPositionMode() == 2)
        {
            // Right-anchored mode.
            widget.setOriginalX(originalValue.originalX + shrinkRightPixels);
        }
        else
        {
            // Normal left-anchored mode.
            widget.setOriginalX(originalValue.originalX + shrinkLeftPixels);
        }

        widget.setOriginalWidth(newWidth);
        widget.revalidate();

        boundsChangedByUs.add(widgetId);
    }

    public void patchCompassLogoutNoClickRegions(int mapNoClick0WidgetId)
    {
        if (compassLogoutNoClickPatched.contains(mapNoClick0WidgetId))
        {
            return;
        }

        if (!client.isResized())
        {
            restoreCompassLogoutNoClickRegions(mapNoClick0WidgetId);
            return;
        }

        Widget mapNoClick0 = client.getWidget(mapNoClick0WidgetId);

        if (mapNoClick0 == null)
        {
            restoreCompassLogoutNoClickRegions(mapNoClick0WidgetId);
            return;
        }

        if (!boundsChangedByUs.contains(mapNoClick0WidgetId))
        {
            originalBounds.put(
                    mapNoClick0WidgetId,
                    new WidgetBounds(
                            mapNoClick0.getOriginalX(),
                            mapNoClick0.getOriginalY(),
                            mapNoClick0.getOriginalWidth(),
                            mapNoClick0.getOriginalHeight()
                    )
            );
        }

        WidgetBounds originalValue = originalBounds.get(mapNoClick0WidgetId);

        if (originalValue == null)
        {
            return;
        }

        if (mapNoClick0.getXPositionMode() == 2)
        {
            // Right-anchored mode:
            // larger OriginalX = visually left.
            mapNoClick0.setOriginalX(originalValue.originalX + MAP_NOCLICK_0_MOVE_LEFT);
        }
        else
        {
            // Normal left-anchored mode:
            // smaller OriginalX = visually left.
            mapNoClick0.setOriginalX(originalValue.originalX - MAP_NOCLICK_0_MOVE_LEFT);
        }

        mapNoClick0.setOriginalY(originalValue.originalY + MAP_NOCLICK_0_SHIFT_DOWN);
        mapNoClick0.setOriginalWidth(Math.max(0, originalValue.originalWidth - MAP_NOCLICK_0_SHRINK_WIDTH));
        mapNoClick0.setOriginalHeight(MAP_NOCLICK_0_HEIGHT);

        mapNoClick0.setHidden(false);
        mapNoClick0.setNoClickThrough(true);
        mapNoClick0.setNoScrollThrough(true);
        mapNoClick0.revalidate();

        boundsChangedByUs.add(mapNoClick0WidgetId);

        createCompassLogoutNoClickExtra(mapNoClick0WidgetId, mapNoClick0);
        compassLogoutNoClickPatched.add(mapNoClick0WidgetId);
    }

    public void restoreCompassLogoutNoClickRegions(int mapNoClick0WidgetId)
    {
        restoreCompassLogoutNoClickExtra(mapNoClick0WidgetId);
        restoreWidgetBounds(mapNoClick0WidgetId);
        compassLogoutNoClickPatched.remove(mapNoClick0WidgetId);
    }

    private void createCompassLogoutNoClickExtra(int mapNoClick0WidgetId, Widget mapNoClick0)
    {
        Widget parent = mapNoClick0.getParent();

        if (parent == null)
        {
            return;
        }

        Widget extra = compassLogoutNoClickExtras.get(mapNoClick0WidgetId);

        if (extra == null || extra.getParent() != parent)
        {
            extra = parent.createChild(-1, WidgetType.LAYER);
            compassLogoutNoClickExtras.put(mapNoClick0WidgetId, extra);
        }

        extra.setXPositionMode(mapNoClick0.getXPositionMode());
        extra.setYPositionMode(mapNoClick0.getYPositionMode());
        extra.setWidthMode(mapNoClick0.getWidthMode());
        extra.setHeightMode(mapNoClick0.getHeightMode());

        if (extra.getXPositionMode() == 2)
        {
            // Right-anchored mode:
            // larger OriginalX = visually left.
            extra.setOriginalX(mapNoClick0.getOriginalX() + MAP_NOCLICK_EXTRA_MOVE_LEFT);
        }
        else
        {
            // Normal left-anchored mode:
            // smaller OriginalX = visually left.
            extra.setOriginalX(mapNoClick0.getOriginalX() - MAP_NOCLICK_EXTRA_MOVE_LEFT);
        }

        extra.setOriginalY(mapNoClick0.getOriginalY() - MAP_NOCLICK_EXTRA_MOVE_UP);
        extra.setOriginalWidth(Math.max(0, mapNoClick0.getOriginalWidth() + MAP_NOCLICK_EXTRA_EXTRA_WIDTH));
        extra.setOriginalHeight(MAP_NOCLICK_EXTRA_HEIGHT);

        extra.setHidden(false);
        extra.setNoClickThrough(true);
        extra.setNoScrollThrough(true);
        extra.setHasListener(true);
        extra.revalidate();
    }

    private void restoreCompassLogoutNoClickExtra(int mapNoClick0WidgetId)
    {
        Widget extra = compassLogoutNoClickExtras.remove(mapNoClick0WidgetId);

        if (extra != null)
        {
            extra.setHidden(true);
            extra.revalidate();
        }
    }

    public void restoreEverythingChangedByUs()
    {
        for (Integer widgetId : new HashSet<>(compassLogoutNoClickExtras.keySet()))
        {
            restoreCompassLogoutNoClickExtra(widgetId);
        }

        compassLogoutNoClickPatched.clear();

        restoreOrbWidgetsChangedByUs();
        restoreWidgetBounds();
    }

    public void restoreOrbWidgetsChangedByUs()
    {
        restoreHiddenWidgets();
        restoreClickThroughWidgets();
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
            restoreWidgetBounds(widgetId);
        }
    }

    private void restoreWidgetBounds(int widgetId)
    {
        Widget widget = client.getWidget(widgetId);
        WidgetBounds originalValue = originalBounds.remove(widgetId);

        if (widget != null && originalValue != null)
        {
            widget.setOriginalX(originalValue.originalX);
            widget.setOriginalY(originalValue.originalY);
            widget.setOriginalWidth(originalValue.originalWidth);
            widget.setOriginalHeight(originalValue.originalHeight);
            widget.revalidate();
        }

        boundsChangedByUs.remove(widgetId);
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
        private final int originalY;
        private final int originalWidth;
        private final int originalHeight;

        private WidgetBounds(int originalX, int originalY, int originalWidth, int originalHeight)
        {
            this.originalX = originalX;
            this.originalY = originalY;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
        }
    }
}