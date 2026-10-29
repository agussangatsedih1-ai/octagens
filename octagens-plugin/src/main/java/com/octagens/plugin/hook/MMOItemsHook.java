package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** MMOItems hook via reflection (no compile-time dependency). */
public final class MMOItemsHook {
    private final OctaGensPlugin plugin;
    private Object pluginObj;
    private Method getItem, getId, getTypeName;

    public MMOItemsHook(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        try {
            Class<?> c = Class.forName("net.Indyuce.mmoitems.MMOItems");
            pluginObj = c.getField("plugin").get(null);
            getItem = c.getMethod("getItem", String.class, String.class);
            try {
                getId = c.getMethod("getID", ItemStack.class);
                getTypeName = c.getMethod("getTypeName", ItemStack.class);
            } catch (NoSuchMethodException e) {
                plugin.getLogger().warning("MMOItems: method getID/getTypeName tidak ada; pencocokan bahan MMOItems dinonaktifkan.");
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Hook MMOItems gagal diinisialisasi: " + t);
            return false;
        }
    }

    /** @return the item or null if it does not exist */
    public ItemStack create(String type, String id) {
        try {
            Object r = getItem.invoke(pluginObj, type.toUpperCase(), id.toUpperCase());
            return r instanceof ItemStack s ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public String id(ItemStack s) {
        if (getId == null || s == null) return null;
        try {
            Object r = getId.invoke(null, s);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable t) {
            return null;
        }
    }

    public String type(ItemStack s) {
        if (getTypeName == null || s == null) return null;
        try {
            Object r = getTypeName.invoke(null, s);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable t) {
            return null;
        }
    }
}
