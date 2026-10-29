package com.octagens.api.event;

import com.octagens.api.model.GeneratorInfo;

/**
 * Fired when a generator switches between having enough Mcu for a drop (efficiency 1.0) and not
 * having enough (efficiency below 1.0). It is not fired for every small pool change.
 */
public final class EnergyEfficiencyChangeEvent extends OctaGensEvent {
    private final GeneratorInfo generator;
    private final double oldEfficiency;
    private final double newEfficiency;

    public EnergyEfficiencyChangeEvent(GeneratorInfo generator, double oldEfficiency, double newEfficiency) {
        this.generator = generator;
        this.oldEfficiency = oldEfficiency;
        this.newEfficiency = newEfficiency;
    }

    /** @return the generator */
    public GeneratorInfo getGenerator() { return generator; }

    /** @return previous efficiency (0.0 - 1.0) */
    public double getOldEfficiency() { return oldEfficiency; }

    /** @return new efficiency (0.0 - 1.0) */
    public double getNewEfficiency() { return newEfficiency; }
}
