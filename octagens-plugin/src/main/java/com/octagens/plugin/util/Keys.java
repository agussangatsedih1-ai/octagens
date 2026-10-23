package com.octagens.plugin.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer keys used to identify OctaGens items. */
public final class Keys {
    public final NamespacedKey generator;
    public final NamespacedKey energy;
    public final NamespacedKey drop;
    public final NamespacedKey wand;
    public final NamespacedKey wandUses;

    public Keys(Plugin plugin) {
        this.generator = new NamespacedKey(plugin, "generator");
        this.energy = new NamespacedKey(plugin, "energy");
        this.drop = new NamespacedKey(plugin, "drop");
        this.wand = new NamespacedKey(plugin, "sellwand");
        this.wandUses = new NamespacedKey(plugin, "sellwand_uses");
    }
}
