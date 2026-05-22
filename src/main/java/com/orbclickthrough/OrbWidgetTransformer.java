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

@Singleton
public class OrbWidgetTransformer
{
    private final Client client;

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

    public void offsetWidgetBoundsRightAndShrinkWidth(int widgetId, int xOffset)
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

    public void restoreEverythingChangedByUs()
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
}