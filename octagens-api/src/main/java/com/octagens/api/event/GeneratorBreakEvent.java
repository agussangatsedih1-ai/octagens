package com.octagens.api.event;

import com.octagens.api.model.GeneratorInfo;
import org.bukkit.entity.Player;

/** Fired when a player breaks a generator block (owner or editor). Cancelling keeps the generator. */
public final class GeneratorBreakEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final GeneratorInfo generator;

    public GeneratorBreakEvent(Player player, GeneratorInfo generator) {
        this.player = player;
        this.generator = generator;
    }

    /** @return the player breaking the generator */
    public Player getPlayer() { return player; }

    /** @return the generator */
    public GeneratorInfo getGenerator() { return generator; }
}
