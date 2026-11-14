package com.octagens.plugin.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;

import java.lang.reflect.Field;
import java.util.Locale;

/** Version-tolerant lookups (Particle changed from enum to registry on newer servers). */
public final class Registries {
    private Registries() {
    }

    public static Particle particle(String name) {
        if (name == null || name.isBlank()) return null;
        Object[] consts = Particle.class.getEnumConstants();
        if (consts != null) {
            for (Object o : consts) {
                if (((Enum<?>) o).name().equalsIgnoreCase(name)) return (Particle) o;
            }
            return null;
        }
        try {
            Field f = Registry.class.getField("PARTICLE_TYPE");
            Registry<?> r = (Registry<?>) f.get(null);
            Object v = r.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
            return v instanceof Particle p ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
