package com.octagens.plugin.gui;

import com.octagens.plugin.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A GUI layout parsed from guis/*.yml. */
public final class GuiTemplate {
    public static final class Item {
        public String key;
        public int[] slots = new int[0];
        public String function;
        public ItemSpec spec;
        public final List<String> actions = new ArrayList<>();
        public final List<String> left = new ArrayList<>();
        public final List<String> right = new ArrayList<>();
    }

    public final String id;
    public String title;
    public int rows;
    public ItemSpec filler;
    public int[] contentSlots = new int[0];
    public final List<Item> items = new ArrayList<>();
    public List<String> entryLore = new ArrayList<>();

    private GuiTemplate(String id) {
        this.id = id;
    }

    public static GuiTemplate parse(String id, ConfigurationSection y) {
        GuiTemplate t = new GuiTemplate(id);
        t.title = y.getString("title", id);
        t.rows = Math.max(1, Math.min(6, y.getInt("rows", 3)));
        ConfigurationSection f = y.getConfigurationSection("filler");
        if (f != null && f.getBoolean("enabled", true)) {
            t.filler = ItemSpec.fromSection(f);
            if (t.filler.name == null) t.filler.name = " ";
        }
        t.contentSlots = parseSlots(y.get("content-slots"));
        t.entryLore = new ArrayList<>(y.getStringList("entry-lore"));
        ConfigurationSection items = y.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(key);
                if (s == null) continue;
                Item it = new Item();
                it.key = key;
                Object slotRaw = s.contains("slots") ? s.get("slots") : s.get("slot");
                it.slots = parseSlots(slotRaw);
                String fn = s.getString("function");
                it.function = fn == null ? null : fn.toUpperCase(Locale.ROOT);
                it.spec = ItemSpec.fromSection(s);
                it.actions.addAll(s.getStringList("actions"));
                it.left.addAll(s.getStringList("left-actions"));
                it.right.addAll(s.getStringList("right-actions"));
                t.items.add(it);
            }
        }
        return t;
    }

    /** Accepts an int, "a-b", or a list of ints / "a-b" strings. */
    public static int[] parseSlots(Object raw) {
        List<Integer> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) addSlots(out, o);
        } else if (raw != null) {
            addSlots(out, raw);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    private static void addSlots(List<Integer> out, Object o) {
        if (o instanceof Number n) {
            out.add(n.intValue());
            return;
        }
        String s = String.valueOf(o).trim();
        try {
            if (s.contains("-")) {
                String[] p = s.split("-");
                int a = Integer.parseInt(p[0].trim());
                int b = Integer.parseInt(p[1].trim());
                for (int i = Math.min(a, b); i <= Math.max(a, b); i++) out.add(i);
            } else if (!s.isEmpty()) {
                out.add(Integer.parseInt(s));
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
