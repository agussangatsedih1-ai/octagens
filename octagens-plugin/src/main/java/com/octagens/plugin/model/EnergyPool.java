package com.octagens.plugin.model;

/** Per-player Magicule storage. Regenerates continuously; generators spend from it per drop. */
public final class EnergyPool {
    public double current;
    public double max;
    public long updatedAt;
    public double lastSaved = -1;
    public double savedMax = -1;

    public EnergyPool(double current, double max) {
        this.current = current;
        this.max = max;
        this.updatedAt = System.currentTimeMillis();
    }

    /** Adds regen for the time elapsed since the last update (lazy, O(1)). */
    public void update(long now, double perSecond) {
        if (now > updatedAt) {
            if (perSecond > 0 && current < max) {
                current = Math.min(max, current + perSecond * ((now - updatedAt) / 1000.0));
            }
            updatedAt = now;
        }
        if (current > max) current = max;
    }
}
