package com.octagens.plugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Inventory holder that marks an inventory as an OctaGens GUI. */
public final class OctaGui implements InventoryHolder {
    public final GuiTemplate template;
    public final Player player;
    public final Map<String, Object> data = new HashMap<>();
    private Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> handlers = new HashMap<>();

    public OctaGui(GuiTemplate template, Player player) {
        this.template = template;
        this.player = player;
    }

    @SuppressWarnings("deprecation")
    void init(String title) {
        this.inventory = Bukkit.createInventory(this, template.rows * 9, title);
    }

    public int size() {
        return inventory.getSize();
    }

    public void setRaw(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, item);
    }

    public void set(int slot, ItemStack item, Consumer<ClickType> handler) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (handler != null) handlers.put(slot, handler);
        else handlers.remove(slot);
    }

    public void handle(int slot, ClickType type) {
        Consumer<ClickType> h = handlers.get(slot);
        if (h != null) h.accept(type);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
