package com.octagens.api.event;

/** Fired when the weekend bonus window starts. */
public final class WeekendBonusStartEvent extends OctaGensEvent {
    private final String mode;
    private final double minMultiplier;
    private final double maxMultiplier;

    public WeekendBonusStartEvent(String mode, double minMultiplier, double maxMultiplier) {
        this.mode = mode;
        this.minMultiplier = minMultiplier;
        this.maxMultiplier = maxMultiplier;
    }

    /** @return mode name (RANDOM_PER_DROP, FIXED_PER_WEEKEND, FIXED_PER_DAY) */
    public String getMode() { return mode; }

    /** @return configured minimum multiplier */
    public double getMinMultiplier() { return minMultiplier; }

    /** @return configured maximum multiplier */
    public double getMaxMultiplier() { return maxMultiplier; }
}
