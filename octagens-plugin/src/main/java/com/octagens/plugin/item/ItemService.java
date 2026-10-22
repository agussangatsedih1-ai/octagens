package com.octagens.plugin.item;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.EnergyType;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.hook.Currency;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Keys;
import com.octagens.plugin.util.Text;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates, identifies and matches every item used by OctaGens. Items are identified through
 * PersistentDataContainer, never by name or lore.
 */
public final class ItemService {
    private static final Pattern TEXTURE_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private final OctaGensPlugin plugin;
    private final Set<String> warned = new HashSet<>();
    private final Map<String, ItemStack> dropCache = new HashMap<>();

    public ItemService(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    private Keys keys() {
        return plugin.keys();
    }

    public void clearCache() {
        dropCache.clear();
        warned.clear();
    }

    private void warnOnce(String msg) {
        if (warned.add(msg)) plugin.getLogger().warning(msg);
    }

    // ------------------------------------------------------------------ text helpers

    /** Applies {placeholders} then PlaceholderAPI (when a player is given). */
    public String fill(String s, Map<String, String> repl, OfflinePlayer papi) {
        String out = Text.replace(s == null ? "" : s, repl);
        if (papi != null) out = PlaceholderAPI.setPlaceholders(papi, out);
        return out;
    }

    /** Colors lore lines; replacement values may contain \n to expand into several lines. */
    public List<String> lore(List<String> lines, Map<String, String> repl, OfflinePlayer papi) {
        List<String> out = new ArrayList<>();
        if (lines == null) return out;
        for (String line : lines) {
            String f = fill(line, repl, papi);
            for (String part : f.split("\n", -1)) out.add(Text.item(part));
        }
        return out;
    }

    // ------------------------------------------------------------------ resolve / build

    /** Resolves an item reference (VANILLA / MMOITEMS / ITEMSADDER / HEAD / ENERGY / GENERATOR). Null if unavailable. */
    public ItemStack resolve(String ref) {
        if (ref == null || ref.isBlank()) return null;
        String r = ref.trim();
        int idx = r.indexOf(':');
        String kind = idx < 0 ? "VANILLA" : r.substring(0, idx).toUpperCase(Locale.ROOT);
        String rest = idx < 0 ? r : r.substring(idx + 1);
        switch (kind) {
            case "VANILLA": {
                Material m = Material.matchMaterial(rest);
                if (m == null || !m.isItem() || m.isAir()) {
                    warnOnce("Material tidak dikenal: '" + rest + "' (ref " + ref + ")");
                    return null;
                }
                return new ItemStack(m);
            }
            case "MMOITEMS": {
                if (plugin.hooks().mmo() == null) {
                    warnOnce("Item '" + ref + "' membutuhkan MMOItems yang tidak terpasang/aktif (dilewati).");
                    return null;
                }
                String[] p = rest.split(":", 2);
                if (p.length < 2) {
                    warnOnce("Format MMOItems salah: '" + ref + "' (gunakan MMOITEMS:TYPE:ID)");
                    return null;
                }
                ItemStack s = plugin.hooks().mmo().create(p[0], p[1]);
                if (s == null) warnOnce("Item MMOItems tidak ditemukan: " + ref);
                return s;
            }
            case "ITEMSADDER": {
                if (plugin.hooks().ia() == null) {
                    warnOnce("Item '" + ref + "' membutuhkan ItemsAdder yang tidak terpasang/aktif (dilewati).");
                    return null;
                }
                ItemStack s = plugin.hooks().ia().create(rest);
                if (s == null) warnOnce("Item ItemsAdder tidak ditemukan: " + ref);
                return s;
            }
            case "HEAD":
                return head(rest);
            case "ENERGY": {
                EnergyType e = plugin.registry().energy(rest);
                return e == null ? null : energyItem(e, 1);
            }
            case "GENERATOR": {
                GeneratorType g = plugin.registry().generator(rest);
                return g == null ? null : generatorItem(g, 1);
            }
            default: {
                Material m = Material.matchMaterial(r);
                if (m == null || !m.isItem() || m.isAir()) {
                    warnOnce("Referensi item tidak dikenal: '" + ref + "'");
                    return null;
                }
                return new ItemStack(m);
            }
        }
    }

    /** Builds an item from a spec. Returns null when the base item cannot be resolved. */
    public ItemStack build(ItemSpec spec, Map<String, String> repl, OfflinePlayer papi) {
        ItemStack base = resolve(spec.ref);
        if (base == null) return null;
        ItemStack st = base.clone();
        st.setAmount(Math.max(1, spec.amount));
        ItemMeta meta = st.getItemMeta();
        if (meta == null) return st;
        if (spec.name != null) meta.setDisplayName(Text.item(fill(spec.name, repl, papi)));
        if (spec.lore != null) meta.setLore(lore(spec.lore, repl, papi));
        if (spec.customModelData != null && spec.customModelData > 0) meta.setCustomModelData(spec.customModelData);
        if (spec.itemModel != null && !spec.itemModel.isBlank()) applyItemModel(meta, spec.itemModel);
        if (spec.glow) meta.setEnchantmentGlintOverride(true);
        if (spec.hideFlags) meta.addItemFlags(ItemFlag.values());
        st.setItemMeta(meta);
        return st;
    }

    /** Like build but returns a barrier item instead of null (for GUI display). */
    public ItemStack buildSafe(ItemSpec spec, Map<String, String> repl, OfflinePlayer papi) {
        ItemStack st = build(spec, repl, papi);
        if (st != null) return st;
        ItemStack b = new ItemStack(Material.BARRIER);
        ItemMeta m = b.getItemMeta();
        if (m != null) {
            m.setDisplayName(Text.item("&cItem tidak tersedia"));
            m.setLore(List.of(Text.item("&7" + spec.ref)));
            b.setItemMeta(m);
        }
        return b;
    }

    private void applyItemModel(ItemMeta meta, String model) {
        try {
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key == null) return;
            Method m = ItemMeta.class.getMethod("setItemModel", NamespacedKey.class);
            m.invoke(meta, key);
        } catch (Throwable ignored) {
            // item model component only exists on 1.21.2+
        }
    }

    // ------------------------------------------------------------------ heads

    public static String textureUrl(String base64OrUrl) {
        if (base64OrUrl == null || base64OrUrl.isBlank()) return null;
        String in = base64OrUrl.trim();
        if (in.startsWith("http")) return in;
        try {
            String json = new String(Base64.getDecoder().decode(in), StandardCharsets.UTF_8);
            Matcher m = TEXTURE_URL.matcher(json);
            return m.find() ? m.group(1) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public ItemStack head(String texture) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        String url = textureUrl(texture);
        if (url == null) return it;
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(
                        UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), "OctaGens");
                PlayerTextures tex = profile.getTextures();
                tex.setSkin(java.net.URI.create(url).toURL());
                profile.setTextures(tex);
                sm.setOwnerProfile(profile);
                it.setItemMeta(sm);
            } catch (Exception e) {
                warnOnce("Texture head tidak valid: " + e.getMessage());
            }
        }
        return it;
    }

    // ------------------------------------------------------------------ OctaGens items

    public Map<String, String> generatorRepl(GeneratorType t) {
        Map<String, String> r = new HashMap<>();
        r.put("id", t.id);
        r.put("name", t.displayName);
        r.put("tier", String.valueOf(t.tier));
        r.put("drop_name", t.dropItem.name == null ? Fmt.pretty(t.dropItem.ref) : t.dropItem.name);
        r.put("interval", Fmt.number(t.dropIntervalTicks / 20.0));
        r.put("amount", String.valueOf(t.dropAmount));
        r.put("energy", Fmt.number(t.energyRequirement));
        Currency buy = plugin.hooks().currency(t.currency);
        Currency sell = plugin.hooks().currency(t.sellCurrency);
        r.put("price", buy != null ? buy.format(t.shopPrice) : Fmt.money(t.shopPrice));
        r.put("sell_price", sell != null ? sell.format(t.sellPrice) : Fmt.money(t.sellPrice));
        return r;
    }

    /** The generator item (placeable block + PDC). Null when its base item cannot be resolved or is not a block. */
    public ItemStack generatorItem(GeneratorType t, int amount) {
        ItemSpec spec = t.block.copy();
        spec.amount = 1;
        if (spec.name == null) spec.name = t.displayName;
        if (spec.lore == null) spec.lore = t.lore;
        ItemStack st = build(spec, generatorRepl(t), null);
        if (st == null) return null;
        if (!st.getType().isBlock()) {
            warnOnce("Generator '" + t.id + "': item block '" + t.block.ref + "' bukan block yang bisa dipasang.");
            return null;
        }
        ItemMeta meta = st.getItemMeta();
        meta.getPersistentDataContainer().set(keys().generator, PersistentDataType.STRING, t.id);
        st.setItemMeta(meta);
        st.setAmount(Math.max(1, amount));
        return st;
    }

    /** The shop icon for a generator (no placement semantics needed, but safe either way). */
    public ItemStack generatorIcon(GeneratorType t, Map<String, String> extra, OfflinePlayer papi, List<String> extraLore) {
        ItemSpec spec = (t.shopIcon != null ? t.shopIcon : t.block).copy();
        spec.amount = 1;
        if (spec.name == null) spec.name = t.displayName;
        List<String> lore = new ArrayList<>(spec.lore != null ? spec.lore : t.lore);
        if (extraLore != null) lore.addAll(extraLore);
        spec.lore = lore;
        Map<String, String> repl = generatorRepl(t);
        if (extra != null) repl.putAll(extra);
        return buildSafe(spec, repl, papi);
    }

    public ItemStack energyItem(EnergyType e, int amount) {
        return energyItem(e, amount, null, null);
    }

    public ItemStack energyItem(EnergyType e, int amount, List<String> extraLore, Map<String, String> extra) {
        ItemStack st = head(e.headTexture);
        ItemMeta meta = st.getItemMeta();
        Map<String, String> repl = new HashMap<>();
        repl.put("name", e.displayName);
        repl.put("mcu", Fmt.number(e.mcuPerSecond));
        if (extra != null) repl.putAll(extra);
        meta.setDisplayName(Text.item(Text.replace(e.displayName, repl)));
        List<String> lore = new ArrayList<>(e.lore);
        if (extraLore != null) lore.addAll(extraLore);
        meta.setLore(lore(lore, repl, null));
        if (e.customModelData > 0) meta.setCustomModelData(e.customModelData);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(keys().energy, PersistentDataType.STRING, e.id);
        st.setItemMeta(meta);
        st.setAmount(Math.max(1, amount));
        return st;
    }

    /** A drop item with the PDC drop tag. Null when it cannot be built (hook missing...). */
    public ItemStack dropItem(GeneratorType t, int amount) {
        ItemStack proto = dropCache.get(t.id);
        if (proto == null) {
            ItemSpec spec = t.dropItem.copy();
            spec.amount = 1;
            ItemStack st = build(spec, generatorRepl(t), null);
            if (st == null) return null;
            ItemMeta meta = st.getItemMeta();
            meta.getPersistentDataContainer().set(keys().drop, PersistentDataType.STRING, t.id);
            st.setItemMeta(meta);
            dropCache.put(t.id, st);
            proto = st;
        }
        ItemStack out = proto.clone();
        out.setAmount(Math.max(1, amount));
        return out;
    }

    // ------------------------------------------------------------------ identification

    private String readTag(ItemStack s, NamespacedKey key) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return null;
        return s.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public String generatorId(ItemStack s) {
        return readTag(s, keys().generator);
    }

    public String energyId(ItemStack s) {
        return readTag(s, keys().energy);
    }

    public String dropId(ItemStack s) {
        return readTag(s, keys().drop);
    }

    public boolean isPlain(ItemStack s) {
        if (!s.hasItemMeta()) return true;
        ItemMeta m = s.getItemMeta();
        if (m.hasDisplayName() || m.hasLore() || m.hasCustomModelData()) return false;
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        if (pdc.has(keys().generator, PersistentDataType.STRING)
                || pdc.has(keys().energy, PersistentDataType.STRING)
                || pdc.has(keys().drop, PersistentDataType.STRING)) return false;
        if (plugin.hooks().mmo() != null && plugin.hooks().mmo().id(s) != null) return false;
        return plugin.hooks().ia() == null || plugin.hooks().ia().id(s) == null;
    }

    /** True if the stack is exactly the item described by the reference. */
    public boolean matches(ItemStack s, String ref) {
        if (s == null || s.getType().isAir() || ref == null) return false;
        String r = ref.trim();
        int idx = r.indexOf(':');
        String kind = idx < 0 ? "VANILLA" : r.substring(0, idx).toUpperCase(Locale.ROOT);
        String rest = idx < 0 ? r : r.substring(idx + 1);
        switch (kind) {
            case "ENERGY":
                return rest.equalsIgnoreCase(energyId(s));
            case "GENERATOR":
                return rest.equalsIgnoreCase(generatorId(s));
            case "MMOITEMS": {
                if (plugin.hooks().mmo() == null) return false;
                String[] p = rest.split(":", 2);
                if (p.length < 2) return false;
                return p[1].equalsIgnoreCase(plugin.hooks().mmo().id(s))
                        && p[0].equalsIgnoreCase(plugin.hooks().mmo().type(s));
            }
            case "ITEMSADDER":
                return plugin.hooks().ia() != null && rest.equalsIgnoreCase(plugin.hooks().ia().id(s));
            case "HEAD":
                return false;
            default: {
                Material m = Material.matchMaterial(kind.equals("VANILLA") ? rest : r);
                return m != null && s.getType() == m && isPlain(s);
            }
        }
    }

    /** True when the reference can currently be used (hook present, item exists). */
    public boolean isAvailable(String ref) {
        if (ref == null) return false;
        String up = ref.trim().toUpperCase(Locale.ROOT);
        if (up.startsWith("MMOITEMS:")) return plugin.hooks().mmo() != null;
        if (up.startsWith("ITEMSADDER:")) return plugin.hooks().ia() != null;
        if (up.startsWith("ENERGY:") || up.startsWith("GENERATOR:")) return true;
        return true;
    }

    public int count(PlayerInventory inv, String ref) {
        int n = 0;
        for (ItemStack s : inv.getStorageContents()) {
            if (matches(s, ref)) n += s.getAmount();
        }
        return n;
    }

    /** Removes the amount. Caller must have verified {@link #count} first. */
    public void remove(PlayerInventory inv, String ref, int amount) {
        ItemStack[] contents = inv.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (!matches(s, ref)) continue;
            int take = Math.min(left, s.getAmount());
            left -= take;
            if (take >= s.getAmount()) contents[i] = null;
            else s.setAmount(s.getAmount() - take);
        }
        inv.setStorageContents(contents);
    }

    /** Human readable name of a reference (for GUI lore). */
    public String refName(String ref) {
        String up = ref.trim().toUpperCase(Locale.ROOT);
        if (up.startsWith("ENERGY:")) {
            EnergyType e = plugin.registry().energy(ref.substring(7));
            if (e != null) return e.displayName;
        }
        if (up.startsWith("GENERATOR:")) {
            GeneratorType g = plugin.registry().generator(ref.substring(10));
            if (g != null) return g.displayName;
        }
        if (up.startsWith("MMOITEMS:") || up.startsWith("ITEMSADDER:")) {
            if (isAvailable(ref)) {
                ItemStack s = resolve(ref);
                if (s != null && s.hasItemMeta() && s.getItemMeta().hasDisplayName()) return s.getItemMeta().getDisplayName();
            }
            return Fmt.pretty(ref.substring(ref.lastIndexOf(':') + 1));
        }
        String rest = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
        return Fmt.pretty(rest);
    }

    public void giveOrDrop(Player p, ItemStack stack) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }
}
