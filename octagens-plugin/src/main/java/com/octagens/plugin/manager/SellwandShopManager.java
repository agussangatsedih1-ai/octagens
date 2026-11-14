package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.hook.Currency;
import com.octagens.plugin.model.SellwandType;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Text;
import com.octagens.plugin.util.Yml;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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

/** Configurable six-row Sellwand shop. All visual settings live in guis/sellwandshop.yml. */
public final class SellwandShopManager implements Listener {
    private static final class Menu implements InventoryHolder {
        final Map<Integer,SellwandType> entries=new LinkedHashMap<>();
        Inventory inventory;
        @Override public Inventory getInventory(){return inventory;}
    }
    private final OctaGensPlugin plugin;
    private YamlConfiguration gui;
    public SellwandShopManager(OctaGensPlugin plugin){this.plugin=plugin;}

    public void load(){gui=Yml.load(plugin,"guis/sellwandshop.yml",false);}

    public void open(Player player){
        if(gui==null)load();
        ConfigurationSection menu=gui.getConfigurationSection("menu");
        int rows=Math.max(1,Math.min(6,menu==null?6:menu.getInt("rows",6)));
        String title=menu==null?"<gradient:#d4af37:#fff1a8><bold>✦ SELLWAND COLLECTION ✦</bold></gradient>":menu.getString("title","<gradient:#d4af37:#fff1a8><bold>✦ SELLWAND COLLECTION ✦</bold></gradient>");
        Menu holder=new Menu();holder.inventory=Bukkit.createInventory(holder,rows*9,Text.color(plugin.hooks().fonts(Text.color(title))));
        fill(holder.inventory,menu);
        addDecorations(holder.inventory,gui.getConfigurationSection("decorations"));
        List<Integer> slots=new ArrayList<>();for(int slot:com.octagens.plugin.gui.GuiTemplate.parseSlots(menu==null?null:menu.get("sellwand-slots")))if(slot>=0&&slot<holder.inventory.getSize())slots.add(slot);
        if(slots.isEmpty())for(int slot:new int[]{20,22,24,29,31,33})if(slot<holder.inventory.getSize())slots.add(slot);
        int index=0;
        for(SellwandType type:plugin.registry().wands()){
            if(!type.shopEnabled||index>=slots.size())continue;
            Currency currency=plugin.hooks().currency(type.shopCurrency);
            String price=currency==null?Fmt.money(type.shopPrice):currency.format(type.shopPrice);
            Map<String,String> replacements=new LinkedHashMap<>();
            replacements.put("id",type.id);replacements.put("name",Fmt.pretty(type.id));
            replacements.put("uses",type.uses<0?"Unlimited":String.valueOf(type.uses));
            replacements.put("multiplier",Fmt.number(type.multiplier));replacements.put("cooldown",Fmt.number(type.cooldownMs/1000.0));
            replacements.put("price",price);replacements.put("currency",type.shopCurrency);
            ConfigurationSection display=gui.getConfigurationSection("sellwand-item");
            ItemStack displayItem=plugin.items().wandItem(type,type.uses,type.multiplier,1,player);
            if(displayItem==null)displayItem=new ItemStack(Material.BLAZE_ROD);
            if(display!=null&&display.contains("material")){
                Material guiMaterial=Material.matchMaterial(display.getString("material",""));
                if(guiMaterial!=null&&guiMaterial.isItem())displayItem=new ItemStack(guiMaterial);
            }
            ItemMeta meta=displayItem.getItemMeta();
            if(meta!=null){
                String name=display==null?"<gradient:#f8d878:#fff1b3><bold>✦ {name} Sellwand ✦</bold></gradient>":display.getString("name","<gold>{name} Sellwand");
                meta.setDisplayName(Text.color(Text.replace(name,replacements)));
                List<String> lore=new ArrayList<>();
                List<String> template=display==null?List.of("<dark_gray>━━━━━━━━━━━━━━━━━━━━","<gray>✧ Uses: <white>{uses}","<gray>✧ Sell multiplier: <green>{multiplier}x","<gray>✧ Cooldown: <yellow>{cooldown}s","","<gold>Price: <white>{price}","","<yellow>➜ CLICK TO PURCHASE"):display.getStringList("lore");
                for(String line:template)lore.add(Text.color(Text.replace(line,replacements)));
                meta.setLore(lore);if(display!=null&&display.getBoolean("glow",true))meta.setEnchantmentGlintOverride(true);displayItem.setItemMeta(meta);
            }
            int slot=slots.get(index++);holder.inventory.setItem(slot,displayItem);holder.entries.put(slot,type);
        }
        ConfigurationSection close=gui.getConfigurationSection("close");
        if(close!=null&&close.getBoolean("enabled",true)){
            int slot=close.getInt("slot",49);
            if(slot>=0&&slot<holder.inventory.getSize())holder.inventory.setItem(slot,makeItem(close.getString("material","BARRIER"),close.getString("name","<red><bold>✕ CLOSE</bold>"),close.getStringList("lore"),close.getBoolean("glow",false)));
        }
        player.openInventory(holder.inventory);
    }

    private void fill(Inventory inventory,ConfigurationSection menu){
        ConfigurationSection filler=menu==null?null:menu.getConfigurationSection("filler");
        ItemStack base=makeItem(filler==null?"BLACK_STAINED_GLASS_PANE":filler.getString("material","BLACK_STAINED_GLASS_PANE"),filler==null?" ":filler.getString("name"," "),filler==null?List.of():filler.getStringList("lore"),false);
        for(int i=0;i<inventory.getSize();i++)inventory.setItem(i,base);
        ConfigurationSection frame=menu==null?null:menu.getConfigurationSection("frame");
        if(frame!=null&&frame.getBoolean("enabled",true)){
            ItemStack pane=makeItem(frame.getString("material","PURPLE_STAINED_GLASS_PANE"),frame.getString("name"," "),frame.getStringList("lore"),false);
            for(int slot:com.octagens.plugin.gui.GuiTemplate.parseSlots(frame.get("slots")))if(slot>=0&&slot<inventory.getSize())inventory.setItem(slot,pane);
        }
        ConfigurationSection accents=menu==null?null:menu.getConfigurationSection("accents");
        if(accents!=null){ItemStack pane=makeItem(accents.getString("material","YELLOW_STAINED_GLASS_PANE"),accents.getString("name"," "),accents.getStringList("lore"),false);
            for(int slot:com.octagens.plugin.gui.GuiTemplate.parseSlots(accents.get("slots")))if(slot>=0&&slot<inventory.getSize())inventory.setItem(slot,pane);}
    }

    private void addDecorations(Inventory inventory,ConfigurationSection decorations){
        if(decorations==null)return;for(String key:decorations.getKeys(false)){
            ConfigurationSection item=decorations.getConfigurationSection(key);if(item==null)continue;int slot=item.getInt("slot",-1);
            if(slot>=0&&slot<inventory.getSize())inventory.setItem(slot,makeItem(item.getString("material","PAPER"),item.getString("name"," "),item.getStringList("lore"),item.getBoolean("glow",false)));
        }
    }
    private ItemStack makeItem(String rawMaterial,String name,List<String> lore,boolean glow){
        Material material=Material.matchMaterial(rawMaterial==null?"":rawMaterial);if(material==null||!material.isItem())material=Material.PAPER;
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();if(meta!=null){meta.setDisplayName(Text.color(name));List<String> colored=new ArrayList<>();for(String line:lore)colored.add(Text.color(line));meta.setLore(colored);if(glow)meta.setEnchantmentGlintOverride(true);item.setItemMeta(meta);}return item;
    }

    @EventHandler public void onClick(InventoryClickEvent event){
        if(!(event.getInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player)||event.getClickedInventory()!=event.getInventory())return;
        ConfigurationSection close=gui.getConfigurationSection("close");int closeSlot=close==null?49:close.getInt("slot",49);
        if(event.getRawSlot()==closeSlot){player.closeInventory();return;}
        SellwandType type=menu.entries.get(event.getRawSlot());if(type==null)return;
        Currency currency=plugin.hooks().currency(type.shopCurrency);
        if(currency==null||!currency.available()){plugin.messages().send(player,"sellwand-shop-currency-missing");return;}
        if(!currency.has(player,type.shopPrice)){plugin.messages().send(player,"sellwand-shop-insufficient","price",currency.format(type.shopPrice));return;}
        if(!currency.withdraw(player,type.shopPrice)){plugin.messages().send(player,"sellwand-shop-payment-failed");return;}
        ItemStack wand=plugin.items().wandItem(type,type.uses,type.multiplier,1,player);
        if(wand==null){currency.deposit(player,type.shopPrice);plugin.messages().send(player,"generator-item-unavailable");return;}
        plugin.items().giveOrDrop(player,wand);plugin.messages().send(player,"sellwand-shop-purchased","id",type.id,"price",currency.format(type.shopPrice));player.closeInventory();
    }
    @EventHandler public void onDrag(InventoryDragEvent event){if(event.getInventory().getHolder() instanceof Menu)event.setCancelled(true);}
}
