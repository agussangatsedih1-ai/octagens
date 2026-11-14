package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.hook.Currency;
import com.octagens.plugin.model.SellwandType;
import com.octagens.plugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Config-driven Sellwand shop. */
public final class SellwandShopManager implements Listener {
    private static final class Menu implements InventoryHolder {
        final Map<Integer, SellwandType> entries = new LinkedHashMap<>();
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }
    private final OctaGensPlugin plugin;
    public SellwandShopManager(OctaGensPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 27, Text.color("<gradient:#f7971e:#ffd200><bold>✦ Sellwand Shop ✦</bold></gradient>"));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "<dark_gray>✦", List.of("<gray>OctaGens Sellwand Shop"));
        for (int i = 0; i < 27; i++) menu.inventory.setItem(i, filler);
        int[] slots = {10, 12, 14, 16}; int i = 0;
        for (SellwandType type : plugin.registry().wands()) {
            if (!type.shopEnabled || i >= slots.length) continue;
            Currency currency = plugin.hooks().currency(type.shopCurrency);
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<gray>✧ <white>Multiplier: <gold>" + type.multiplier + "x");
            lore.add("<gray>✧ <white>Uses: <aqua>" + (type.uses < 0 ? "Unlimited" : type.uses));
            lore.add("<gray>✧ <white>Cooldown: <yellow>" + (type.cooldownMs / 1000.0) + "s");
            lore.add("");
            lore.add("<gray>Price: <gold>" + (currency == null ? type.shopPrice : currency.format(type.shopPrice)));
            lore.add("");
            lore.add("<yellow>▶ Click to purchase");
            ItemStack display = plugin.items().wandItem(type, type.uses, type.multiplier, 1, player);
            if (display == null) display = item(Material.BLAZE_ROD, "<gold>" + type.id, lore);
            else { ItemMeta meta = display.getItemMeta(); if (meta != null) { List<String> coloredLore = new ArrayList<>(); for (String line : lore) coloredLore.add(Text.color(line)); meta.setLore(coloredLore); display.setItemMeta(meta); } }
            menu.inventory.setItem(slots[i], display); menu.entries.put(slots[i], type); i++;
        }
        menu.inventory.setItem(22, item(Material.BARRIER, "<red>✖ Close", List.of("<gray>Click to close this menu")));
        player.openInventory(menu.inventory);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        if (meta != null) { meta.setDisplayName(Text.color(name)); List<String> coloredLore = new ArrayList<>(); for (String line : lore) coloredLore.add(Text.color(line)); meta.setLore(coloredLore); stack.setItemMeta(meta); }
        return stack;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) return;
        SellwandType type = menu.entries.get(event.getRawSlot());
        if (type == null) { if (event.getRawSlot() == 22) player.closeInventory(); return; }
        Currency currency = plugin.hooks().currency(type.shopCurrency);
        if (currency == null || !currency.available()) { plugin.messages().send(player, "sellwand-shop-currency-missing"); return; }
        if (!currency.has(player, type.shopPrice)) { plugin.messages().send(player, "sellwand-shop-insufficient", "price", currency.format(type.shopPrice)); return; }
        if (!currency.withdraw(player, type.shopPrice)) { plugin.messages().send(player, "sellwand-shop-payment-failed"); return; }
        ItemStack wand = plugin.items().wandItem(type, type.uses, type.multiplier, 1, player);
        if (wand == null) { currency.deposit(player, type.shopPrice); plugin.messages().send(player, "generator-item-unavailable"); return; }
        plugin.items().giveOrDrop(player, wand);
        plugin.messages().send(player, "sellwand-shop-purchased", "id", type.id, "price", currency.format(type.shopPrice));
        player.closeInventory();
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getInventory().getHolder() instanceof Menu) event.setCancelled(true); }
}
