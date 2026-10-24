package com.octagens.api.event;

/** Fired when the weekend bonus window ends. */
public final class WeekendBonusEndEvent extends OctaGensEvent {
    private final String mode;

    public WeekendBonusEndEvent(String mode) {
        this.mode = mode;
    }

    /** @return mode name that was active */
    public String getMode() { return mode; }
}
