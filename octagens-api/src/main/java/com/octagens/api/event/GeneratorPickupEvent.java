package com.octagens.api.event;

import com.octagens.api.model.GeneratorInfo;
import org.bukkit.entity.Player;

/** Fired when a player picks up a generator (sneak + click, or via the manage GUI). */
public final class GeneratorPickupEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final GeneratorInfo generator;

    public GeneratorPickupEvent(Player player, GeneratorInfo generator) {
        this.player = player;
        this.generator = generator;
    }

    /** @return the player picking up */
    public Player getPlayer() { return player; }

    /** @return the generator */
    public GeneratorInfo getGenerator() { return generator; }
}
