package com.octagens.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Base class of all OctaGens events. All subclasses share one {@link HandlerList};
 * Bukkit filters listeners by the declared event type.
 */
public abstract class OctaGensEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    @Override
    public final HandlerList getHandlers() {
        return HANDLERS;
    }

    /** @return the shared handler list */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
