package com.octagens.api.event;

import com.octagens.api.model.GeneratorInfo;

/** Fired every time a generator is about to drop items. Amount and multiplier can be modified. */
public final class GeneratorDropEvent extends CancellableOctaGensEvent {
    private final GeneratorInfo generator;
    private int amount;
    private double multiplier;

    public GeneratorDropEvent(GeneratorInfo generator, int amount, double multiplier) {
        this.generator = generator;
        this.amount = amount;
        this.multiplier = multiplier;
    }

    /** @return the dropping generator */
    public GeneratorInfo getGenerator() { return generator; }

    /** @return base amount before the multiplier */
    public int getAmount() { return amount; }

    /** Sets the base amount (minimum 0). */
    public void setAmount(int amount) { this.amount = Math.max(0, amount); }

    /** @return multiplier (weekend bonus x manual event bonus) */
    public double getMultiplier() { return multiplier; }

    /** Sets the multiplier (minimum 0). */
    public void setMultiplier(double multiplier) { this.multiplier = Math.max(0, multiplier); }

    /** @return final amount = round(amount * multiplier) */
    public int getFinalAmount() { return (int) Math.max(0, Math.round(amount * multiplier)); }
}
