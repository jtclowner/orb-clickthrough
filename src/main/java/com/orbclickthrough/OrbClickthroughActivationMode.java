package com.orbclickthrough;

public enum OrbClickthroughActivationMode
{
    HOLD_TO_CLICK_THROUGH("Hold to click-through"),
    HOLD_TO_RESTORE_CLICKS("Hold to click orbs"),
    TOGGLE_CLICK_THROUGH("Toggle click-through");

    private final String name;

    OrbClickthroughActivationMode(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}