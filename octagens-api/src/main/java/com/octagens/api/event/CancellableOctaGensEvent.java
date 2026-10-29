package com.octagens.api.event;

import org.bukkit.event.Cancellable;

/** Base class of cancellable OctaGens events. */
public abstract class CancellableOctaGensEvent extends OctaGensEvent implements Cancellable {
    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
