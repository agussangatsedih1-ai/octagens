package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Daily mission progress, rewards and reset clock. All state changes run on the server thread. */
public final class DailyMissionManager implements Listener {
    private static final class Mission {
        final String id, name, event, icon;
        final int target;
        final List<String> rewards;
        Mission(String id, String name, String event, String icon, int target, List<String> rewards) {
            this.id=id; this.name=name; this.event=event; this.icon=icon; this.target=target; this.rewards=rewards;
        }
    }
    private static final class State {
        String date;
        long playtime;
        final Map<String,Integer> progress = new LinkedHashMap<>();
        final Map<String,Boolean> claimed = new LinkedHashMap<>();
    }
    private static final class MissionMenu implements InventoryHolder {
        final Player viewer;
        final Map<Integer,String> slots = new LinkedHashMap<>();
        Inventory inventory;
        MissionMenu(Player viewer) { this.viewer=viewer; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final OctaGensPlugin plugin;
    private final Map<String,Mission> missions = new LinkedHashMap<>();
    private final Map<UUID,State> states = new LinkedHashMap<>();
    private ZoneId zone = ZoneId.of("Asia/Jakarta");
    private String activeDate;
    private String title = "<gradient:#7F00FF:#E100FF><bold>Daily Missions</bold></gradient>";
    private String resetMessage = "<gold>Daily Missions telah di-reset! <yellow>/dailymissions";
    private String reminderMessage = "<yellow>Kamu masih memiliki Daily Mission yang belum selesai. <white>/dailymissions";
    private int reminderSeconds = 180;
    private long lastReminder;
    private long seconds;
    private BukkitTask task;

    public DailyMissionManager(OctaGensPlugin plugin) { this.plugin=plugin; }

    public void load() {
        missions.clear();
        YamlConfiguration c = com.octagens.plugin.util.Yml.load(plugin,"missions/daily-missions.yml",false);
        java.io.File legacy = new java.io.File(plugin.getDataFolder(), "daily-missions.yml");
        if (legacy.isFile() && !c.getBoolean("legacy-migrated", false)) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(legacy);
            for (String key : old.getKeys(false)) c.set(key, old.get(key));
            c.set("legacy-migrated", true);
            try { c.save(new java.io.File(plugin.getDataFolder(), "missions/daily-missions.yml")); legacy.delete(); } catch (java.io.IOException ex) { plugin.getLogger().warning("Gagal migrasi daily-missions.yml: " + ex.getMessage()); }
        }
        try { zone=ZoneId.of(c.getString("timezone","Asia/Jakarta")); }
        catch (Exception ex) { zone=ZoneId.of("Asia/Jakarta"); plugin.getLogger().warning("daily-missions timezone invalid; default Asia/Jakarta used."); }
        title=c.getString("menu.title",title);
        resetMessage=c.getString("notifications.reset",resetMessage);
        reminderMessage=c.getString("notifications.reminder",reminderMessage);
        reminderSeconds=Math.max(30,c.getInt("notifications.reminder-interval-seconds",180));
        ConfigurationSection root=c.getConfigurationSection("missions");
        if (root!=null) for(String id:root.getKeys(false)) {
            ConfigurationSection s=root.getConfigurationSection(id); if(s==null) continue;
            List<String> rewards=s.getStringList("rewards");
            missions.put(id,new Mission(id,s.getString("name",id),s.getString("event",id).toUpperCase(Locale.ROOT),
                    s.getString("icon","PAPER"),Math.max(1,s.getInt("target",1)),rewards));
        }
        activeDate=LocalDate.now(zone).toString();
        loadOnlineStates();
    }

    private void loadOnlineStates() {
        for(Player p:Bukkit.getOnlinePlayers()) state(p.getUniqueId());
    }

    public void start() {
        stop(); seconds=0; lastReminder=System.currentTimeMillis();
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->tick(),20L,20L);
    }
    public void stop() { if(task!=null) task.cancel(); task=null; for(UUID id:new ArrayList<>(states.keySet())) save(id); }
    private void sendPrefixed(Player player, String message) { player.sendMessage(plugin.messages().get("prefix") + Text.color(message)); }

    private void tick() {
        String today=LocalDate.now(zone).toString();
        if(!today.equals(activeDate)) {
            activeDate=today;
            seconds=0;
            for(UUID id:new ArrayList<>(states.keySet())) { State s=states.get(id); reset(s,today); save(id); }
            for(Player p:Bukkit.getOnlinePlayers()) sendPrefixed(p, resetMessage);
            plugin.getLogger().info("Daily Missions reset at 00:00 " + zone + " ("+today+").");
        }
        seconds++;
        if(seconds>=60) {
            seconds=0;
            for(Player p:Bukkit.getOnlinePlayers()) {
                State s=state(p.getUniqueId()); s.playtime+=60; progressEvent(p,"PLAYTIME",60); save(p.getUniqueId());
            }
        }
        long now=System.currentTimeMillis();
        if(now-lastReminder>=reminderSeconds*1000L) {
            lastReminder=now;
            for(Player p:Bukkit.getOnlinePlayers()) if(hasPending(p.getUniqueId())) sendPrefixed(p, reminderMessage);
        }
    }

    private void reset(State s,String date) { s.date=date; s.playtime=0; s.progress.clear(); s.claimed.clear(); }
    private State state(UUID id) {
        State s=states.get(id);
        if(s!=null) { if(!activeDate.equals(s.date)) reset(s,activeDate); return s; }
        s=new State(); File f=file(id);
        if(f.isFile()) {
            YamlConfiguration y=YamlConfiguration.loadConfiguration(f);
            s.date=y.getString("date",activeDate); s.playtime=y.getLong("playtime",0);
            ConfigurationSection pr=y.getConfigurationSection("progress");
            if(pr!=null) for(String k:pr.getKeys(false)) s.progress.put(k,Math.max(0,pr.getInt(k)));
            ConfigurationSection cl=y.getConfigurationSection("claimed");
            if(cl!=null) for(String k:cl.getKeys(false)) s.claimed.put(k,cl.getBoolean(k));
        }
        if(!activeDate.equals(s.date)) reset(s,activeDate);
        states.put(id,s); return s;
    }
    private File file(UUID id) { return new File(plugin.getDataFolder(),"daily-missions/"+id+".yml"); }
    private void save(UUID id) {
        State s=states.get(id); if(s==null)return;
        YamlConfiguration y=new YamlConfiguration(); y.set("date",s.date); y.set("playtime",s.playtime);
        for(var e:s.progress.entrySet()) y.set("progress."+e.getKey(),e.getValue());
        for(var e:s.claimed.entrySet()) y.set("claimed."+e.getKey(),e.getValue());
        File f=file(id); if(!f.getParentFile().exists()&&!f.getParentFile().mkdirs())return;
        try { y.save(f); } catch(IOException ex){plugin.getLogger().warning("Failed saving daily mission state for "+id+": "+ex.getMessage());}
    }

    public void progress(Player p,String event,int amount) { if(p==null||amount<=0)return; progressEvent(p,event,amount); save(p.getUniqueId()); }
    private void progressEvent(Player p,String event,int amount) {
        State s=state(p.getUniqueId());
        for(Mission m:missions.values()) if(m.event.equalsIgnoreCase(event)) {
            int old=s.progress.getOrDefault(m.id,0);
            if(!s.claimed.getOrDefault(m.id,false)) s.progress.put(m.id,Math.min(m.target,old+amount));
        }
    }
    public boolean hasPending(UUID id) {
        State s=state(id);
        for(Mission m:missions.values()) if(!s.claimed.getOrDefault(m.id,false)) return true;
        return false;
    }

    public void open(Player p) {
        State s=state(p.getUniqueId()); MissionMenu h=new MissionMenu(p);
        h.inventory=Bukkit.createInventory(h,54,Text.color(plugin.hooks().fonts(Text.color(title))));
        ItemStack filler=new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta fm=filler.getItemMeta(); if(fm!=null){fm.setDisplayName(" ");filler.setItemMeta(fm);} for(int i=0;i<54;i++)h.inventory.setItem(i,filler);
        int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        int n=0;
        for(Mission m:missions.values()) {
            if(n>=slots.length)break; int slot=slots[n++];
            int current=s.progress.getOrDefault(m.id,0); boolean claimed=s.claimed.getOrDefault(m.id,false); boolean done=current>=m.target;
            Material mat=Material.matchMaterial(m.icon); if(mat==null||!mat.isItem())mat=Material.PAPER;
            ItemStack item=new ItemStack(mat); ItemMeta im=item.getItemMeta();
            if(im!=null){ im.setDisplayName(Text.color((claimed?"<green><bold>" : done?"<yellow><bold>":"<white>")+m.name));
                List<String> lore=new ArrayList<>(); lore.add(Text.color("<gray>Progress: <white>"+current+"<gray>/"+m.target));
                lore.add(Text.color(claimed?"<green>Claimed" : done?"<yellow>Click to claim reward":"<red>In progress"));
                lore.add(Text.color("<dark_gray>Reward:")); for(String r:m.rewards)lore.add(Text.color("<gray>• <white>"+r));
                im.setLore(lore); item.setItemMeta(im); }
            h.inventory.setItem(slot,item); h.slots.put(slot,m.id);
        }
        p.openInventory(h.inventory);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if(!(e.getInventory().getHolder() instanceof MissionMenu h))return;
        e.setCancelled(true); if(!(e.getWhoClicked() instanceof Player p)||e.getClickedInventory()!=e.getInventory())return;
        String id=h.slots.get(e.getRawSlot()); if(id==null)return; claim(p,id); Bukkit.getScheduler().runTask(plugin,()->open(p));
    }
    @EventHandler public void onDrag(InventoryDragEvent e){if(e.getInventory().getHolder() instanceof MissionMenu)e.setCancelled(true);}
    @EventHandler public void onJoin(PlayerJoinEvent e){state(e.getPlayer().getUniqueId());}
    @EventHandler public void onQuit(PlayerQuitEvent e){save(e.getPlayer().getUniqueId());}

    private void claim(Player p,String id) {
        Mission m=missions.get(id); if(m==null)return; State s=state(p.getUniqueId());
        if(s.claimed.getOrDefault(id,false)){sendPrefixed(p, "<red>Mission sudah diklaim.");return;}
        if(s.progress.getOrDefault(id,0)<m.target){sendPrefixed(p, "<red>Mission belum selesai.");return;}
        for(String raw:m.rewards) if(!canResolveReward(raw)) { sendPrefixed(p, "<red>Reward belum tersedia. Pastikan MMOItems/item hook yang dibutuhkan aktif."); return; }
        for(String raw:m.rewards) giveReward(p,raw);
        s.claimed.put(id,true); save(p.getUniqueId()); sendPrefixed(p, "<green>Reward Daily Mission diterima: "+m.name);
    }
    private boolean canResolveReward(String raw) {
        if(raw==null||raw.isBlank())return true;
        String value=raw.trim();
        if(value.regionMatches(true,0,"COMMAND:",0,8))return true;
        String[] parts=value.split(":");
        if(parts.length>=3&&parts[0].equalsIgnoreCase("VANILLA"))return plugin.items().resolve("VANILLA:"+parts[1])!=null;
        if(parts.length>=4&&parts[0].equalsIgnoreCase("MMOITEMS"))return plugin.items().resolve("MMOITEMS:"+parts[1]+":"+parts[2])!=null;
        return true;
    }

    private void giveReward(Player p,String raw) {
        if(raw==null||raw.isBlank())return; String value=raw.trim();
        if(value.regionMatches(true,0,"COMMAND:",0,8)) { Bukkit.dispatchCommand(Bukkit.getConsoleSender(),value.substring(8).replace("{player}",p.getName())); return; }
        String[] parts=value.split(":");
        if(parts.length>=3 && (parts[0].equalsIgnoreCase("VANILLA")||parts[0].equalsIgnoreCase("MMOITEMS"))) {
            int amount=1; try{amount=Integer.parseInt(parts[parts.length-1]);}catch(Exception ignored){}
            String ref;
            if(parts[0].equalsIgnoreCase("VANILLA")) ref="VANILLA:"+parts[1];
            else if(parts.length>=4) ref="MMOITEMS:"+parts[1]+":"+parts[2]; else ref="";
            if(ref.isBlank())return;
            ItemStack item=plugin.items().resolve(ref); if(item==null)return; int left=Math.max(1,amount); int max=Math.max(1,item.getMaxStackSize());
            while(left>0){int n=Math.min(left,max);ItemStack copy=item.clone();copy.setAmount(n);plugin.items().giveOrDrop(p,copy);left-=n;} return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),value.replace("{player}",p.getName()));
    }
    public Map<String,Object> snapshot(UUID id){State s=state(id);Map<String,Object> out=new LinkedHashMap<>();out.put("date",s.date);out.put("playtime",s.playtime);out.put("progress",new LinkedHashMap<>(s.progress));out.put("claimed",new LinkedHashMap<>(s.claimed));return out;}
}
