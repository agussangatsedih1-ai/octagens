package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.hook.Currency;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Independent booster tracks for drops, sell value and Magicule regeneration. */
public final class BoosterManager implements Listener {
    public enum Type { DROP, SELL, MCU }
    private static final class OfferMenu implements InventoryHolder {
        final Player viewer; final Map<Integer,Object> slots=new LinkedHashMap<>(); Inventory inventory;
        final String stage; final Type type; final double multiplier;
        OfferMenu(Player p,String stage,Type type,double multiplier){viewer=p;this.stage=stage;this.type=type;this.multiplier=multiplier;}
        @Override public Inventory getInventory(){return inventory;}
    }
    private static final class Offer {
        double pricePerOne; String currency, name, icon; boolean enabled, customEnabled;
        int customMin=1,customMax=10; List<Double> multipliers=new ArrayList<>(); Map<String,Long> durations=new LinkedHashMap<>();
    }
    private static final class Running { double multiplier=0.0; long until=0; }

    private final OctaGensPlugin plugin;
    private final Map<Type,Offer> offers=new LinkedHashMap<>();
    private YamlConfiguration gui;
    private final Map<Type,Running> giveaways=new HashMap<>();
    private final Map<Type,Set<UUID>> phraseClaims=new HashMap<>();
    private final Set<String> chatPhrases=new HashSet<>();
    private final Map<UUID,Type> awaitingCustom=new HashMap<>();
    private ZoneId zone=ZoneId.of("Asia/Jakarta");
    private boolean autoSell=true,autoMcu=true;
    private DayOfWeek sellDay=DayOfWeek.SUNDAY;
    private double sellMin=1.5,sellMax=3.0,mcuMin=1.2,mcuMax=2.5;
    private List<String> chatRewards=new ArrayList<>(List.of("VANILLA:DIAMOND:1"));
    private String menuTitle="<gradient:#d4af37:#fff1a8><bold>Booster Giveaway Shop</bold></gradient>";
    private String menuFiller="BLACK_STAINED_GLASS_PANE";
    private String giveawayBroadcast="<gold><bold>GIVEAWAY!</bold> <yellow>{player} mengaktifkan {booster} <white>{multiplier}x selama {duration}.";
    private String claimBroadcast="<green>{player} berhasil mengklaim hadiah giveaway!";
    private BukkitTask task;

    public BoosterManager(OctaGensPlugin plugin){this.plugin=plugin;for(Type t:Type.values()){giveaways.put(t,new Running());phraseClaims.put(t,new HashSet<>());}}

    public void load(){
        offers.clear();
        gui=Yml.load(plugin,"guis/booster.yml",false);
        YamlConfiguration auto=com.octagens.plugin.util.Yml.load(plugin,"boosters/automatic.yml",false);
        File legacy = new File(plugin.getDataFolder(), "boosters.yml");
        if (legacy.isFile() && !auto.getBoolean("legacy-migrated", false)) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(legacy);
            auto.set("timezone", old.getString("timezone", "Asia/Jakarta"));
            ConfigurationSection oldAuto = old.getConfigurationSection("automatic");
            if (oldAuto != null) for (String path : oldAuto.getKeys(true)) if (!oldAuto.isConfigurationSection(path)) auto.set("automatic." + path, oldAuto.get(path));
            auto.set("legacy-migrated", true);
            try { auto.save(new File(plugin.getDataFolder(), "boosters/automatic.yml")); } catch (IOException ex) { plugin.getLogger().warning("Gagal migrasi boosters.yml: " + ex.getMessage()); }
        }
        try{zone=ZoneId.of(auto.getString("timezone","Asia/Jakarta"));}catch(Exception e){zone=ZoneId.of("Asia/Jakarta");}
        ConfigurationSection a=auto.getConfigurationSection("automatic");
        if(a!=null){autoSell=a.getBoolean("sell.enabled",true);autoMcu=a.getBoolean("mcu-gain.enabled",true);sellDay=parseDay(a.getString("sell.day","SUNDAY"),DayOfWeek.SUNDAY);sellMin=Math.max(1,a.getDouble("sell.min",1.5));sellMax=Math.max(sellMin,a.getDouble("sell.max",3));mcuMin=Math.max(1,a.getDouble("mcu-gain.min",1.2));mcuMax=Math.max(mcuMin,a.getDouble("mcu-gain.max",2.5));}
        YamlConfiguration legacyOffers = legacy.isFile() ? YamlConfiguration.loadConfiguration(legacy) : null;
        String[] paths={"boosters/gen-drop.yml","boosters/sell-multi.yml","boosters/mcu-gain.yml"};
        for(Type t:Type.values()){
            YamlConfiguration y=com.octagens.plugin.util.Yml.load(plugin,paths[t.ordinal()],false);
            if (legacyOffers != null) {
                ConfigurationSection oldOffer = legacyOffers.getConfigurationSection("giveaway." + key(t));
                if (oldOffer != null && !y.contains("legacy-migrated")) {
                    double oldMult = Math.max(1.0, oldOffer.getDouble("multiplier", 1.0));
                    double oldPrice = Math.max(0.0, oldOffer.getDouble("price", 0.0));
                    y.set("enabled", oldOffer.getBoolean("enabled", true)); y.set("name", oldOffer.getString("name", display(t)));
                    y.set("icon", oldOffer.getString("icon", t==Type.DROP?"DIAMOND_BLOCK":t==Type.SELL?"GOLD_INGOT":"AMETHYST_SHARD"));
                    y.set("currency", oldOffer.getString("currency", "excellenteconomy:denari")); y.set("multipliers", List.of(oldMult));
                    y.set("price-per-1x", oldPrice / oldMult); long oldDuration = Fmt.parseDuration(oldOffer.getString("duration", "1h"));
                    if (oldDuration > 0) { y.set("durations", null); y.set("durations.legacy", oldDuration); }
                    y.set("legacy-migrated", true); try { y.save(new File(plugin.getDataFolder(), paths[t.ordinal()])); } catch (IOException ex) { plugin.getLogger().warning("Gagal migrasi booster lama: " + ex.getMessage()); }
                }
            }
            Offer o=new Offer();o.enabled=y.getBoolean("enabled",true);if(!o.enabled)continue;
            o.name=y.getString("name",display(t));o.icon=y.getString("icon",t==Type.DROP?"DIAMOND_BLOCK":t==Type.SELL?"GOLD_INGOT":"AMETHYST_SHARD");o.currency=y.getString("currency","excellenteconomy:denari");o.pricePerOne=Math.max(0,y.getDouble("price-per-1x",10000));
            for(Object v:y.getList("multipliers",List.of(1,1.5,2,3,4)))try{double d=Double.parseDouble(String.valueOf(v));if(d>=1&&Double.isFinite(d))o.multipliers.add(d);}catch(Exception ignored){}
            ConfigurationSection custom=y.getConfigurationSection("custom-multiplier");o.customEnabled=custom!=null&&custom.getBoolean("enabled",true);if(custom!=null){o.customMin=Math.max(1,custom.getInt("min",1));o.customMax=Math.min(10,Math.max(o.customMin,custom.getInt("max",10)));}
            ConfigurationSection ds=y.getConfigurationSection("durations");if(ds!=null)for(String k:ds.getKeys(false)){long seconds=ds.getLong(k,0);if(seconds>0)o.durations.put(k,seconds);}
            if(o.durations.isEmpty()){o.durations.put("1m",60L);o.durations.put("5m",300L);o.durations.put("10m",600L);o.durations.put("30m",1800L);o.durations.put("1h",3600L);o.durations.put("8h",28800L);o.durations.put("12h",43200L);o.durations.put("24h",86400L);}
            offers.put(t,o);
        }
        YamlConfiguration chat=com.octagens.plugin.util.Yml.load(plugin,"boosters/chat-rewards.yml",false);
        chatPhrases.clear();for(String phrase:chat.getStringList("chat-phrases"))chatPhrases.add(phrase.toUpperCase(Locale.ROOT));
        chatRewards=chat.getStringList("chat-rewards");if(chatRewards.isEmpty())chatRewards=List.of("VANILLA:DIAMOND:1");
        giveawayBroadcast=chat.getString("notifications.giveaway",giveawayBroadcast);claimBroadcast=chat.getString("notifications.claim",claimBroadcast);
        String prefix = plugin.messages().get("prefix");
        if (!giveawayBroadcast.contains("OctaGens")) giveawayBroadcast = prefix + giveawayBroadcast;
        if (!claimBroadcast.contains("OctaGens")) claimBroadcast = prefix + claimBroadcast;
        if (legacy.isFile()) legacy.delete();
        loadState();
    }
    private DayOfWeek parseDay(String raw,DayOfWeek def){try{return DayOfWeek.valueOf(raw.toUpperCase(Locale.ROOT));}catch(Exception e){return def;}}
    private String key(Type t){return switch(t){case DROP->"drop-booster";case SELL->"sell-booster";case MCU->"mcu-booster";};}
    private String display(Type t){return switch(t){case DROP->"Generator Drop";case SELL->"Sell Multiplier";case MCU->"MCu Gain";};}
    public void start(){stop();task=Bukkit.getScheduler().runTaskTimer(plugin,()->expire(),20L,20L*30);}
    public void stop(){if(task!=null)task.cancel();task=null;saveState();}
    private void expire(){long now=System.currentTimeMillis();boolean changed=false;for(Running r:giveaways.values())if(r.until>0&&now>=r.until){r.multiplier=0;r.until=0;changed=true;}if(changed)saveState();}

    private double randomDaily(String id,double min,double max){if(max<=min)return min;long seed=LocalDate.now(zone).toEpochDay()*0x9E3779B97F4A7C15L+id.hashCode();Random r=new Random(seed);return Math.round((min+r.nextDouble()*(max-min))*100.0)/100.0;}
    private boolean isAutoActive(Type t){LocalDate d=LocalDate.now(zone);return switch(t){case DROP->plugin.weekend()!=null&&plugin.weekend().isActive();case SELL->autoSell&&d.getDayOfWeek()==sellDay;case MCU->autoMcu;};}
    private double autoMultiplier(Type t){if(!isAutoActive(t))return 1.0;return switch(t){case DROP->plugin.weekend().dropMultiplier();case SELL->randomDaily("sell",sellMin,sellMax);case MCU->randomDaily("mcu",mcuMin,mcuMax);};}
    public double multiplier(Type t){expire();double auto=autoMultiplier(t);Running g=giveaways.get(t);boolean manual=g!=null&&g.until>System.currentTimeMillis()&&g.multiplier>0.0;
        if(manual&&auto>1.0)return auto+g.multiplier;if(manual)return 1.0+g.multiplier;return auto;}
    public double dropMultiplier(){double base=multiplier(Type.DROP);double weekend=plugin.weekend()==null?1.0:plugin.weekend().dropMultiplier();
        // WeekendManager remains the source of the automatic Gen Drop window; avoid multiplying it twice.
        if(weekend>1.0){Running g=giveaways.get(Type.DROP);return g!=null&&g.until>System.currentTimeMillis()?weekend+g.multiplier:weekend;}
        return base;
    }
    public double sellMultiplier(){return multiplier(Type.SELL);}
    public double mcuMultiplier(){return multiplier(Type.MCU);}
    public boolean hasActiveGiveaway(){for(Running r:giveaways.values())if(r.until>System.currentTimeMillis()&&r.multiplier>0)return true;return false;}

    private static final int MENU_SIZE = 45;

    public void openMenu(Player p){openMenu(p,null);}

    /** Main category screen, configured in guis/booster.yml. */
    public void openMenu(Player p,Type focus){
        String path="menus.category";
        OfferMenu h=new OfferMenu(p,"TYPE",null,1);
        h.inventory=Bukkit.createInventory(h,guiRows(path),guiTitle(path,"<gradient:#8b5cf6:#f5c76a><bold>✦ OCTAGENS • BOOSTER EXCHANGE ✦</bold></gradient>"));
        fill(h.inventory);
        setConfigured(h.inventory,path+".header",guiSlot(path+".header.slot",4),Material.NETHER_STAR,
                "<gradient:#f8d878:#fff0b3><bold>✦ THE BOOSTER VAULT ✦</bold></gradient>",
                List.of("<gray>Server-wide enhancements","<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━","<white>Choose a booster category to begin.","<gray>Every purchase is shared with the whole server.","","<gold>✦ Duration is FREE","<yellow>✦ Price depends on multiplier."),true,Map.of());

        int[] slots=guiSlots(path+".booster-slots",new int[]{20,22,24});
        int index=0;
        for(Type t:Type.values()){
            if(focus!=null&&focus!=t)continue;
            Offer o=offers.get(t);if(o==null)continue;
            String configuredIcon=gui.getString(path+".icons."+key(t),o.icon);
            Material material=Material.matchMaterial(configuredIcon);if(material==null||!material.isItem())material=defaultIcon(t);
            Currency currency=plugin.hooks().currency(o.currency);
            String price=currency==null?Fmt.money(o.pricePerOne):currency.format(o.pricePerOne);
            Map<String,String> vars=Map.of("name",o.name,"booster",o.name,"multiplier",Fmt.number(multiplier(t)),"price",price,"currency",o.currency);
            int slot=slots[Math.min(index++,slots.length-1)];
            ItemStack display=configuredItem(path+".booster-item",material,boosterName(t,o.name),
                    List.of("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━","<light_purple>✧ <white>SERVER-WIDE BOOSTER","<gray>Current bonus: <green><bold>{multiplier}x",
                            "<gray>Price: <gold><bold>{price} <dark_gray>per <yellow>1.0x","","<gray>Choose the strength and duration.","<gray>Duration is free of charge.","","<yellow><bold>➜ SELECT BOOSTER"),true,vars);
            h.inventory.setItem(slot,display);h.slots.put(slot,t);
        }
        setConfigured(h.inventory,path+".info",guiSlot(path+".info.slot",40),Material.BOOK,
                "<aqua><bold>✧ HOW THE VAULT WORKS</bold>",List.of("<gray>1. Choose a booster category.","<gray>2. Select the multiplier you want.","<gray>3. Choose how long it stays active.","","<gold>✦ Your purchase boosts the whole server.","<dark_gray>Automatic boosts are added to giveaway boosts."),false,Map.of());
        int closeSlot=guiSlot(path+".close.slot",44);
        setConfigured(h.inventory,path+".close",closeSlot,Material.BARRIER,"<red><bold>✕ CLOSE MENU</bold>",List.of("<gray>Return to the game."),false,Map.of());h.slots.put(closeSlot,"CLOSE");
        p.openInventory(h.inventory);
    }

    private Material defaultIcon(Type type){return switch(type){case DROP->Material.DIAMOND_BLOCK;case SELL->Material.GOLD_INGOT;case MCU->Material.AMETHYST_SHARD;};}
    private String boosterName(Type type,String name){
        String gradient=switch(type){case DROP->"<gradient:#f59e0b:#fde68a>";case SELL->"<gradient:#16a34a:#bbf7d0>";case MCU->"<gradient:#8b5cf6:#67e8f9>";};
        return gradient+"<bold>✦ "+name+" ✦</bold></gradient>";
    }

    private int guiRows(String path){return Math.max(1,Math.min(6,gui.getInt(path+".rows",5)));}
    private String guiTitle(String path,String fallback){return Text.color(plugin.hooks().fonts(Text.color(gui.getString(path+".title",fallback))));}
    private int guiSlot(String path,int fallback){return gui.getInt(path,fallback);}
    private int[] guiSlots(String path,int[] fallback){List<Integer> list=gui.getIntegerList(path);if(list.isEmpty())return fallback;return list.stream().mapToInt(Integer::intValue).toArray();}

    private void fill(Inventory inv){
        ConfigurationSection layout=gui.getConfigurationSection("layout");
        Material filler=guiMaterial(layout==null?"BLACK_STAINED_GLASS_PANE":layout.getString("filler.material","BLACK_STAINED_GLASS_PANE"),Material.BLACK_STAINED_GLASS_PANE);
        String fillerName=layout==null?" ":layout.getString("filler.name"," ");
        ItemStack base=item(filler,fillerName,layout==null?List.of():layout.getStringList("filler.lore"),false);
        for(int i=0;i<inv.getSize();i++)inv.setItem(i,base);
        if(layout!=null){
            ConfigurationSection border=layout.getConfigurationSection("border");
            if(border!=null&&border.getBoolean("enabled",true)){
                ItemStack pane=item(guiMaterial(border.getString("material","PURPLE_STAINED_GLASS_PANE"),Material.PURPLE_STAINED_GLASS_PANE),border.getString("name"," "),border.getStringList("lore"),false);
                for(int slot:com.octagens.plugin.gui.GuiTemplate.parseSlots(border.get("slots")))if(slot>=0&&slot<inv.getSize())inv.setItem(slot,pane);
            }
            ConfigurationSection accents=layout.getConfigurationSection("accents");
            if(accents!=null){
                ItemStack pane=item(guiMaterial(accents.getString("material","YELLOW_STAINED_GLASS_PANE"),Material.YELLOW_STAINED_GLASS_PANE),accents.getString("name"," "),accents.getStringList("lore"),false);
                for(int slot:com.octagens.plugin.gui.GuiTemplate.parseSlots(accents.get("slots")))if(slot>=0&&slot<inv.getSize())inv.setItem(slot,pane);
            }
        }
    }

    private Material guiMaterial(String raw,Material fallback){Material material=Material.matchMaterial(raw==null?"":raw);return material==null||!material.isItem()?fallback:material;}
    private ItemStack configuredItem(String path,Material fallback,String defaultName,List<String> defaultLore,boolean defaultGlow,Map<String,String> vars){
        ConfigurationSection section=gui.getConfigurationSection(path);
        Material material=section==null?fallback:guiMaterial(section.getString("material",fallback.name()),fallback);
        String name=section==null?defaultName:section.getString("name",defaultName);
        List<String> lore=section==null?defaultLore:section.getStringList("lore");
        boolean glow=section==null?defaultGlow:section.getBoolean("glow",defaultGlow);
        List<String> filled=new ArrayList<>();for(String line:lore)filled.add(Text.replace(line,vars));
        return item(material,Text.replace(name,vars),filled,glow);
    }
    private void setConfigured(Inventory inventory,String path,int slot,Material fallback,String name,List<String> lore,boolean glow,Map<String,String> vars){
        if(slot>=0&&slot<inventory.getSize())inventory.setItem(slot,configuredItem(path,fallback,name,lore,glow,vars));
    }

    private ItemStack item(Material material,String name,List<String> lore,boolean glint){
        ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();
        if(meta!=null){meta.setDisplayName(Text.item(name));List<String> formatted=new ArrayList<>(lore.size());
            for(String line:lore)formatted.add(Text.item(line));meta.setLore(formatted);if(glint)meta.setEnchantmentGlintOverride(true);stack.setItemMeta(meta);}
        return stack;
    }

    private void openMultiplier(Player p,Type type){
        Offer o=offers.get(type);if(o==null)return;String path="menus.multiplier";
        OfferMenu h=new OfferMenu(p,"MULTIPLIER",type,1);
        h.inventory=Bukkit.createInventory(h,guiRows(path),guiTitle(path,"<gradient:#8b5cf6:#f5c76a><bold>✦ SELECT YOUR MULTIPLIER ✦</bold></gradient>"));fill(h.inventory);
        Currency currency=plugin.hooks().currency(o.currency);
        Map<String,String> vars=Map.of("name",o.name,"multiplier",Fmt.number(multiplier(type)),"price",currency==null?Fmt.money(o.pricePerOne):currency.format(o.pricePerOne));
        String headerIcon=gui.getString("menus.category.icons."+key(type),o.icon);
        Material headerMaterial=guiMaterial(headerIcon,defaultIcon(type));
        setConfigured(h.inventory,path+".header",guiSlot(path+".header.slot",4),headerMaterial,"<gradient:#f8d878:#fff0b3><bold>✦ {name} ✦</bold></gradient>",
                List.of("<gray>Choose the strength of this server-wide boost.","<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━","<gray>Custom values must be whole numbers from 1 to 10."),true,vars);
        setConfigured(h.inventory,path+".info",guiSlot(path+".info.slot",13),Material.ENCHANTED_BOOK,"<light_purple><bold>✧ BOOSTER DETAILS</bold>",
                List.of("<gray>Current server bonus: <green><bold>{multiplier}x","<gray>Price per 1.0x: <gold>{price}","<gray>Selected booster: <white>{name}","","<yellow>✦ Select one of the options below."),true,vars);
        int[] slots=guiSlots(path+".option-slots",new int[]{19,20,21,22,23,24,25,26});int i=0;
        for(double m:o.multipliers){
            if(i>=slots.length)break;int slot=slots[i++];double price=o.pricePerOne*m;String priceText=currency==null?Fmt.money(price):currency.format(price);
            Map<String,String> optionVars=Map.of("name",o.name,"multiplier",Fmt.number(m),"price",priceText);
            h.inventory.setItem(slot,configuredItem(path+".option-item",Material.AMETHYST_SHARD,"<gradient:#c084fc:#f5d0fe><bold>✦ {multiplier}x BOOST ✦</bold></gradient>",
                    List.of("<dark_gray>━━━━━━━━━━━━━━━━━━━━","<gray>Multiplier: <light_purple><bold>{multiplier}x","<gray>Final price: <gold><bold>{price}","","<green>✔ Duration is selected next.","<yellow>➜ CLICK TO CONTINUE"),true,optionVars));
            h.slots.put(slot,m);
        }
        if(o.customEnabled){
            int slot=guiSlot(path+".custom.slot",31);Map<String,String> customVars=Map.of("min",String.valueOf(o.customMin),"max",String.valueOf(o.customMax));
            setConfigured(h.inventory,path+".custom",slot,Material.NAME_TAG,"<gradient:#f8d878:#fff0b3><bold>✎ CUSTOM MULTIPLIER</bold></gradient>",
                    List.of("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━","<gray>Choose a whole number from <white>{min} <gray>to <white>{max}.","<red>Decimals are not accepted.","<yellow>➜ CLICK, THEN TYPE IN CHAT"),true,customVars);h.slots.put(slot,"CUSTOM");
        }
        int back=guiSlot(path+".back.slot",40);setConfigured(h.inventory,path+".back",back,Material.ARROW,"<aqua><bold>↶ BACK</bold>",List.of("<gray>Return to the previous step."),false,Map.of());h.slots.put(back,"BACK");
        int close=guiSlot(path+".close.slot",44);setConfigured(h.inventory,path+".close",close,Material.BARRIER,"<red><bold>✕ CLOSE MENU</bold>",List.of("<gray>Cancel this purchase."),false,Map.of());h.slots.put(close,"CLOSE");
        p.openInventory(h.inventory);
    }

    private void openDuration(Player p,Type type,double multiplier){
        Offer o=offers.get(type);if(o==null)return;String path="menus.duration";
        OfferMenu h=new OfferMenu(p,"DURATION",type,multiplier);
        h.inventory=Bukkit.createInventory(h,guiRows(path),guiTitle(path,"<gradient:#8b5cf6:#f5c76a><bold>✦ CHOOSE BOOST DURATION ✦</bold></gradient>"));fill(h.inventory);
        Currency currency=plugin.hooks().currency(o.currency);double price=o.pricePerOne*multiplier;String priceText=currency==null?Fmt.money(price):currency.format(price);
        Map<String,String> vars=Map.of("name",o.name,"multiplier",Fmt.number(multiplier),"price",priceText);
        setConfigured(h.inventory,path+".header",guiSlot(path+".header.slot",4),Material.CLOCK,"<gradient:#f8d878:#fff0b3><bold>✦ ACTIVATE YOUR BOOST ✦</bold></gradient>",
                List.of("<gray>One final step before activating the giveaway.","<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━","<gray>Booster: <white>{name}","<gray>Multiplier: <light_purple><bold>{multiplier}x","<gray>Purchase price: <gold><bold>{price}","<green>✔ Duration is FREE"),true,vars);
        setConfigured(h.inventory,path+".summary",guiSlot(path+".summary.slot",13),Material.NETHER_STAR,"<gold><bold>✧ YOUR GIVEAWAY SUMMARY</bold>",
                List.of("<gray>Booster: <white>{name}","<gray>Multiplier: <light_purple><bold>{multiplier}x","<gray>Price: <gold><bold>{price}","<gray>Duration: <aqua>Choose below","","<yellow>✦ Duration adds no extra cost."),true,vars);
        int[] slots=guiSlots(path+".duration-slots",new int[]{19,20,21,22,23,24,25,26});int i=0;
        for(Map.Entry<String,Long> entry:o.durations.entrySet()){
            if(i>=slots.length)break;int slot=slots[i++];String label=entry.getKey();long duration=entry.getValue();
            Map<String,String> durationVars=new LinkedHashMap<>(vars);durationVars.put("duration",label);
            h.inventory.setItem(slot,configuredItem(path+".duration-item",Material.CLOCK,"<gradient:#7dd3fc:#dbeafe><bold>⌛ {duration}</bold></gradient>",
                    List.of("<dark_gray>━━━━━━━━━━━━━━━━━━━━","<gray>Boost duration: <aqua><bold>{duration}","<gray>Multiplier: <light_purple><bold>{multiplier}x","<gray>Additional duration cost: <green>FREE","","<yellow>➜ ACTIVATE FOR {duration}"),true,durationVars));
            h.slots.put(slot,duration);
        }
        int back=guiSlot(path+".back.slot",40);setConfigured(h.inventory,path+".back",back,Material.ARROW,"<aqua><bold>↶ BACK</bold>",List.of("<gray>Return to multiplier selection."),false,Map.of());h.slots.put(back,"BACK");
        int close=guiSlot(path+".close.slot",44);setConfigured(h.inventory,path+".close",close,Material.BARRIER,"<red><bold>✕ CLOSE MENU</bold>",List.of("<gray>Cancel this purchase."),false,Map.of());h.slots.put(close,"CLOSE");
        p.openInventory(h.inventory);
    }

    @EventHandler public void onClick(InventoryClickEvent e){
        if(!(e.getInventory().getHolder() instanceof OfferMenu h))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||e.getClickedInventory()!=e.getInventory())return;
        Object value=h.slots.get(e.getRawSlot());if(value==null)return;
        if("CLOSE".equals(value)){p.closeInventory();return;}
        if("BACK".equals(value)){
            if(h.stage.equals("MULTIPLIER"))openMenu(p);
            else if(h.stage.equals("DURATION"))openMultiplier(p,h.type);
            return;
        }
        if(h.stage.equals("TYPE")){if(value instanceof Type type)openMultiplier(p,type);return;}
        if(h.stage.equals("MULTIPLIER")){
            if("CUSTOM".equals(value)){
                awaitingCustom.put(p.getUniqueId(),h.type);p.closeInventory();
                p.sendMessage(Text.color(plugin.messages().get("prefix")+"<yellow>Enter a whole-number multiplier from 1 to 10 in chat. Decimals are not allowed."));
                return;
            }
            if(value instanceof Number number)openDuration(p,h.type,number.doubleValue());
            return;
        }
        if(h.stage.equals("DURATION")&&value instanceof Number number)purchase(p,h.type,h.multiplier,number.longValue());
    }
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getInventory().getHolder() instanceof OfferMenu)e.setCancelled(true);}
    private void purchase(Player p,Type t,double selectedMultiplier,long durationSeconds){Offer o=offers.get(t);if(o==null)return;Currency c=plugin.hooks().currency(o.currency);double price=o.pricePerOne*selectedMultiplier;if(c==null){p.sendMessage(plugin.messages().get("prefix") + Text.color("<red>Currency giveaway tidak tersedia: "+o.currency));return;}
        if(!c.has(p,price)){p.sendMessage(Text.color("<gold><bold>OctaGens</bold> » <red>Saldo tidak cukup. Harga: "+c.format(price)));return;}if(!c.withdraw(p,price)){p.sendMessage(Text.color("<gold><bold>OctaGens</bold> » <red>Pembayaran gagal."));return;}
        Running r=giveaways.get(t);long now=System.currentTimeMillis();r.multiplier=(r.until>now?r.multiplier:0)+selectedMultiplier;r.until=Math.max(r.until,now)+durationSeconds*1000L;phraseClaims.get(t).clear();saveState();
        String msg=Text.color(Text.replace(giveawayBroadcast,Map.of("player",p.getName(),"booster",o.name,"multiplier",String.valueOf(multiplier(t)),"duration",Fmt.duration(durationSeconds))));Bukkit.broadcastMessage(msg);plugin.txLog().log(false,p.getName(),"membeli giveaway "+key(t)+" "+selectedMultiplier+"x selama "+durationSeconds+" detik",price);p.closeInventory();}
    @EventHandler public void onChat(AsyncPlayerChatEvent e){
        Type pending=awaitingCustom.remove(e.getPlayer().getUniqueId());
        if(pending!=null){e.setCancelled(true);String raw=e.getMessage().trim();int value;try{value=Integer.parseInt(raw);}catch(NumberFormatException ex){Bukkit.getScheduler().runTask(plugin,()->e.getPlayer().sendMessage(Text.color("<gold><bold>OctaGens</bold> » <red>Masukkan angka bulat 1-10 tanpa desimal.")));return;}Offer offer=offers.get(pending);if(value<offer.customMin||value>offer.customMax){Bukkit.getScheduler().runTask(plugin,()->e.getPlayer().sendMessage(Text.color("<gold><bold>OctaGens</bold> » <red>Multiplier harus "+offer.customMin+" sampai "+offer.customMax+".")));return;}final int chosen=value;Bukkit.getScheduler().runTask(plugin,()->openDuration(e.getPlayer(),pending,chosen));return;}
        String phrase=e.getMessage().trim().toUpperCase(Locale.ROOT);if(!chatPhrases.contains(phrase)||!hasActiveGiveaway())return;
        Player p=e.getPlayer();Bukkit.getScheduler().runTask(plugin,()->claimPhrase(p));}
    private void claimPhrase(Player p){
        long now=System.currentTimeMillis();
        boolean claimedAny=false;
        for(Type type:Type.values()){
            Running running=giveaways.get(type);
            if(running.until<=now||running.multiplier<=0.0)continue;
            if(!phraseClaims.get(type).add(p.getUniqueId()))continue;
            for(String reward:chatRewards)giveReward(p,reward);
            claimedAny=true;
        }
        if(claimedAny){saveState();Bukkit.broadcastMessage(Text.color(Text.replace(claimBroadcast,Map.of("player",p.getName()))));}
    }
    private void giveReward(Player p,String raw){if(raw==null||raw.isBlank())return;String v=raw.trim();if(v.startsWith("COMMAND:")){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),v.substring(8).replace("{player}",p.getName()));return;}
        String[] a=v.split(":");if(a.length<3)return;int amount=1;try{amount=Integer.parseInt(a[a.length-1]);}catch(Exception ignored){}
        String ref=a[0].equalsIgnoreCase("MMOITEMS")&&a.length>=4?"MMOITEMS:"+a[1]+":"+a[2]:a[0].equalsIgnoreCase("VANILLA")?"VANILLA:"+a[1]:"";
        if(ref.isBlank())return;ItemStack base=plugin.items().resolve(ref);if(base==null)return;int left=Math.max(1,amount),max=Math.max(1,base.getMaxStackSize());while(left>0){int n=Math.min(left,max);ItemStack it=base.clone();it.setAmount(n);plugin.items().giveOrDrop(p,it);left-=n;}}

    private File stateFile(){return new File(plugin.getDataFolder(),"boosters/state.yml");}
    private void loadState(){File f=stateFile();if(!f.isFile())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(f);for(Type t:Type.values()){String k=key(t);Running r=giveaways.get(t);r.multiplier=y.getDouble("running."+k+".multiplier",0);r.until=y.getLong("running."+k+".until",0);
            for(String uuid:y.getStringList("claims."+k)){try{phraseClaims.get(t).add(UUID.fromString(uuid));}catch(Exception ignored){}}}}
    private void saveState(){YamlConfiguration y=new YamlConfiguration();for(Type t:Type.values()){String k=key(t);Running r=giveaways.get(t);y.set("running."+k+".multiplier",r.multiplier);y.set("running."+k+".until",r.until);List<String> ids=new ArrayList<>();for(UUID id:phraseClaims.get(t))ids.add(id.toString());y.set("claims."+k,ids);}
        File f=stateFile();if(!f.getParentFile().exists()&&!f.getParentFile().mkdirs())return;try{y.save(f);}catch(IOException e){plugin.getLogger().warning("Failed saving booster state: "+e.getMessage());}}
    public Map<String,Object> claimSnapshot(UUID id){Map<String,Object> out=new LinkedHashMap<>();for(Type t:Type.values())out.put(key(t),phraseClaims.get(t).contains(id));return out;}
}
