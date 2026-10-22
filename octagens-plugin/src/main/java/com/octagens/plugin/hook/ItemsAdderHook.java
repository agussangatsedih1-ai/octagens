package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** ItemsAdder hook via reflection (custom items + font images). */
public final class ItemsAdderHook {
    private final OctaGensPlugin plugin;
    private Method getInstance, getItemStack, byItemStack, getNamespacedId, replaceFonts;

    public ItemsAdderHook(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        try {
            Class<?> cs = Class.forName("dev.lone.itemsadder.api.CustomStack");
            getInstance = cs.getMethod("getInstance", String.class);
            getItemStack = cs.getMethod("getItemStack");
            byItemStack = cs.getMethod("byItemStack", ItemStack.class);
            getNamespacedId = cs.getMethod("getNamespacedID");
        } catch (Throwable t) {
            plugin.getLogger().warning("Hook ItemsAdder (CustomStack) gagal: " + t);
            return false;
        }
        try {
            Class<?> fw = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
            replaceFonts = fw.getMethod("replaceFontImages", String.class);
        } catch (Throwable t) {
            plugin.getLogger().warning("ItemsAdder: FontImageWrapper tidak tersedia; font image di title dinonaktifkan.");
        }
        return true;
    }

    /** @param namespacedId e.g. {@code mynamespace:my_item} */
    public ItemStack create(String namespacedId) {
        try {
            Object stack = getInstance.invoke(null, namespacedId);
            if (stack == null) return null;
            Object r = getItemStack.invoke(stack);
            return r instanceof ItemStack s ? s.clone() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public String id(ItemStack s) {
        if (s == null) return null;
        try {
            Object stack = byItemStack.invoke(null, s);
            if (stack == null) return null;
            Object r = getNamespacedId.invoke(stack);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable t) {
            return null;
        }
    }

    public String fonts(String text) {
        if (replaceFonts == null || text == null) return text;
        try {
            Object r = replaceFonts.invoke(null, text);
            return r == null ? text : String.valueOf(r);
        } catch (Throwable t) {
            return text;
        }
    }
}
