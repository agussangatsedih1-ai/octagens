package com.octagens.api.event;

import com.octagens.api.model.EnergyInfo;
import org.bukkit.entity.Player;

/** Fired when a player breaks or picks up an energy block. */
public final class EnergyBreakEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final EnergyInfo energy;

    public EnergyBreakEvent(Player player, EnergyInfo energy) {
        this.player = player;
        this.energy = energy;
    }

    /** @return the player */
    public Player getPlayer() { return player; }

    /** @return the energy block */
    public EnergyInfo getEnergy() { return energy; }
}
