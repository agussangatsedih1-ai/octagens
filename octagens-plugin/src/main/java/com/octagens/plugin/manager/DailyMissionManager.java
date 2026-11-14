package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Text;
import com.octagens.plugin.util.Yml;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
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
        final Map<Integer,String> slots = new LinkedHashMap<>();
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final OctaGensPlugin plugin;
    private final Map<String,Mission> missions = new LinkedHashMap<>();
    private final Map<UUID,State> states = new LinkedHashMap<>();
    private ZoneId zone = ZoneId.of("Asia/Jakarta");
    private YamlConfiguration gui;
    private String resetMessage = "<gold>Daily missions have reset. {missions}";
    private String reminderMessage = "<yellow>You still have missions to complete. {missions}";
    private String missionLinkLabel = "<gold><bold>[Missions]</bold></gold>";
    private String missionLinkHover = "<yellow>Click to open Daily Missions";
    private int reminderSeconds = 180;
    private int seconds;
    private String activeDate;
    private long lastReminder;
    private BukkitTask task;

    public DailyMissionManager(OctaGensPlugin plugin) { this.plugin=plugin; }

    public void load() {
        missions.clear();
        YamlConfiguration c = Yml.load(plugin,"missions/daily-missions.yml",false);
        gui = Yml.load(plugin,"guis/dailymissions.yml",false);
        try { zone=ZoneId.of(c.getString("timezone","Asia/Jakarta")); }
        catch (Exception ex) { zone=ZoneId.of("Asia/Jakarta"); plugin.getLogger().warning("daily-missions timezone invalid; default Asia/Jakarta used."); }
        resetMessage=c.getString("notifications.reset",resetMessage);
        reminderMessage=c.getString("notifications.reminder",reminderMessage);
        reminderSeconds=Math.max(30,c.getInt("notifications.reminder-interval-seconds",180));
        missionLinkLabel=c.getString("notifications.link-label",missionLinkLabel);
        missionLinkHover=c.getString("notifications.link-hover",missionLinkHover);
        ConfigurationSection root=c.getConfigurationSection("missions");
        if(root!=null) for(String id:root.getKeys(false)) {
            ConfigurationSection section=root.getConfigurationSection(id); if(section==null) continue;
            List<String> rewards=section.getStringList("rewards");
            missions.put(id,new Mission(id,section.getString("name",id),section.getString("event",id).toUpperCase(Locale.ROOT),
                    section.getString("icon","PAPER"),Math.max(1,section.getInt("target",1)),rewards));
        }
        activeDate=LocalDate.now(zone).toString();
        loadOnlineStates();
    }

    private void loadOnlineStates(){ for(Player player:Bukkit.getOnlinePlayers()) state(player.getUniqueId()); }
    public void start(){ stop(); seconds=0; lastReminder=System.currentTimeMillis(); task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20L,20L); }
    public void stop(){ if(task!=null)task.cancel(); task=null; for(UUID id:new ArrayList<>(states.keySet()))save(id); }

    private void sendPrefixed(Player player,String message){ player.sendMessage(plugin.messages().get("prefix")+Text.color(message)); }

    /** Sends a clickable chat message whose [Missions] segment executes /dailymissions when clicked. */
    private void sendMissionLink(Player player,String message){
        String marker="{missions}";
        int at=message.indexOf(marker);
        String before=at>=0?message.substring(0,at):message+" ";
        String after=at>=0?message.substring(at+marker.length()):"";
        TextComponent clickableMessage=new TextComponent();
        for(BaseComponent component:TextComponent.fromLegacyText(Text.color(before)))clickableMessage.addExtra(component);
        TextComponent link=new TextComponent();
        ClickEvent clickEvent=new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/dailymissions");
        HoverEvent hoverEvent=new HoverEvent(HoverEvent.Action.SHOW_TEXT,TextComponent.fromLegacyText(Text.color(missionLinkHover)));
        for(BaseComponent part:TextComponent.fromLegacyText(Text.color(missionLinkLabel))){
            part.setClickEvent(clickEvent);part.setHoverEvent(hoverEvent);link.addExtra(part);
        }
        clickableMessage.addExtra(link);
        for(BaseComponent component:TextComponent.fromLegacyText(Text.color(after)))clickableMessage.addExtra(component);
        player.spigot().sendMessage(clickableMessage);
    }

    private void tick(){
        String today=LocalDate.now(zone).toString();
        if(!today.equals(activeDate)){
            activeDate=today; seconds=0;
            for(UUID id:new ArrayList<>(states.keySet())){reset(states.get(id),today);save(id);}
            for(Player player:Bukkit.getOnlinePlayers())sendMissionLink(player,resetMessage);
            plugin.getLogger().info("Daily Missions reset at 00:00 "+zone+" ("+today+").");
        }
        seconds++;
        if(seconds>=60){
            seconds=0;
            for(Player player:Bukkit.getOnlinePlayers()){
                State state=state(player.getUniqueId());state.playtime+=60;progressEvent(player,"PLAYTIME",60);save(player.getUniqueId());
            }
        }
        long now=System.currentTimeMillis();
        if(now-lastReminder>=reminderSeconds*1000L){
            lastReminder=now;
            for(Player player:Bukkit.getOnlinePlayers())if(hasPending(player.getUniqueId()))sendMissionLink(player,reminderMessage);
        }
    }

    private void reset(State state,String date){state.date=date;state.playtime=0;state.progress.clear();state.claimed.clear();}
    private State state(UUID id){
        State state=states.get(id);
        if(state!=null){if(!activeDate.equals(state.date))reset(state,activeDate);return state;}
        state=new State();File file=file(id);
        if(file.isFile()){
            YamlConfiguration y=YamlConfiguration.loadConfiguration(file);state.date=y.getString("date",activeDate);state.playtime=y.getLong("playtime",0);
            ConfigurationSection progress=y.getConfigurationSection("progress");if(progress!=null)for(String key:progress.getKeys(false))state.progress.put(key,Math.max(0,progress.getInt(key)));
            ConfigurationSection claimed=y.getConfigurationSection("claimed");if(claimed!=null)for(String key:claimed.getKeys(false))state.claimed.put(key,claimed.getBoolean(key));
        }
        if(!activeDate.equals(state.date))reset(state,activeDate);
        states.put(id,state);return state;
    }
    private File file(UUID id){return new File(plugin.getDataFolder(),"daily-missions/"+id+".yml");}
    private void save(UUID id){
        State state=states.get(id);if(state==null)return;YamlConfiguration y=new YamlConfiguration();y.set("date",state.date);y.set("playtime",state.playtime);
        for(var entry:state.progress.entrySet())y.set("progress."+entry.getKey(),entry.getValue());
        for(var entry:state.claimed.entrySet())y.set("claimed."+entry.getKey(),entry.getValue());
        File file=file(id);if(!file.getParentFile().exists()&&!file.getParentFile().mkdirs())return;
        try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("Failed saving daily mission state for "+id+": "+ex.getMessage());}
    }

    public void progress(Player player,String event,int amount){if(player==null||amount<=0)return;progressEvent(player,event,amount);save(player.getUniqueId());}
    private void progressEvent(Player player,String event,int amount){
        State state=state(player.getUniqueId());
        for(Mission mission:missions.values())if(mission.event.equalsIgnoreCase(event)){
            int old=state.progress.getOrDefault(mission.id,0);
            if(!state.claimed.getOrDefault(mission.id,false))state.progress.put(mission.id,Math.min(mission.target,old+amount));
        }
    }
    public boolean hasPending(UUID id){State state=state(id);for(Mission mission:missions.values())if(!state.claimed.getOrDefault(mission.id,false))return true;return false;}

    public void open(Player player){
        State state=state(player.getUniqueId());MissionMenu holder=new MissionMenu();
        ConfigurationSection menu=gui.getConfigurationSection("menu");
        int rows=Math.max(1,Math.min(6,menu==null?6:menu.getInt("rows",6)));
        String title=menu==null?"<gradient:#8b5cf6:#f5c76a><bold>✦ DAILY MISSIONS ✦</bold></gradient>":menu.getString("title","<gradient:#8b5cf6:#f5c76a><bold>✦ DAILY MISSIONS ✦</bold></gradient>");
        title=plugin.hooks().fonts(Text.color(title));
        holder.inventory=Bukkit.createInventory(holder,rows*9,title);
        fillLayout(holder.inventory,menu);
        addDecorations(holder.inventory,gui.getConfigurationSection("decorations"));
        int[] slots=missionSlots(menu,holder.inventory.getSize());
        ConfigurationSection templates=gui.getConfigurationSection("mission-items");
        int index=0;
        for(Mission mission:missions.values()){
            if(index>=slots.length)break;int slot=slots[index++];if(slot<0||slot>=holder.inventory.getSize())continue;
            int current=state.progress.getOrDefault(mission.id,0);boolean claimed=state.claimed.getOrDefault(mission.id,false);boolean complete=current>=mission.target;
            String status=claimed?"claimed":complete?"ready":"in-progress";
            ItemStack item=createMissionItem(mission,status,current,holder.inventory.getSize(),templates);
            holder.inventory.setItem(slot,item);holder.slots.put(slot,mission.id);
        }
        player.openInventory(holder.inventory);
    }

    private void fillLayout(Inventory inventory,ConfigurationSection menu){
        ConfigurationSection filler=menu==null?null:menu.getConfigurationSection("filler");
        Material fillerType=material(filler==null?"BLACK_STAINED_GLASS_PANE":filler.getString("material","BLACK_STAINED_GLASS_PANE"),Material.BLACK_STAINED_GLASS_PANE);
        ItemStack fill=makeItem(fillerType,filler==null?" ":filler.getString("name"," "),filler==null?List.of():filler.getStringList("lore"),false,Map.of());
        for(int slot=0;slot<inventory.getSize();slot++)inventory.setItem(slot,fill);
        ConfigurationSection border=menu==null?null:menu.getConfigurationSection("border");
        if(border!=null&&border.getBoolean("enabled",true)){
            ItemStack pane=makeItem(material(border.getString("material","PURPLE_STAINED_GLASS_PANE"),Material.PURPLE_STAINED_GLASS_PANE),border.getString("name"," "),border.getStringList("lore"),false,Map.of());
            for(int slot:parseSlots(border.get("slots")))if(slot>=0&&slot<inventory.getSize())inventory.setItem(slot,pane);
        }
        if(menu!=null){
            ConfigurationSection accents=menu.getConfigurationSection("accents");
            if(accents!=null){ItemStack pane=makeItem(material(accents.getString("material","YELLOW_STAINED_GLASS_PANE"),Material.YELLOW_STAINED_GLASS_PANE),accents.getString("name"," "),accents.getStringList("lore"),false,Map.of());
                for(int slot:parseSlots(accents.get("slots")))if(slot>=0&&slot<inventory.getSize())inventory.setItem(slot,pane);}
        }
    }

    private void addDecorations(Inventory inventory,ConfigurationSection decorations){
        if(decorations==null)return;
        for(String key:decorations.getKeys(false)){
            ConfigurationSection item=decorations.getConfigurationSection(key);if(item==null)continue;
            int slot=item.getInt("slot",-1);if(slot<0||slot>=inventory.getSize())continue;
            inventory.setItem(slot,makeItem(material(item.getString("material","PAPER"),Material.PAPER),item.getString("name"," "),item.getStringList("lore"),item.getBoolean("glow",false),Map.of()));
        }
    }

    private int[] missionSlots(ConfigurationSection menu,int size){
        List<Integer> values=new ArrayList<>();
        if(menu!=null){
            Object raw=menu.get("mission-slots");
            for(int slot:parseSlots(raw))if(slot>=0&&slot<size)values.add(slot);
        }
        if(values.isEmpty())for(int slot:new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43})if(slot<size)values.add(slot);
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] parseSlots(Object raw){return com.octagens.plugin.gui.GuiTemplate.parseSlots(raw);}

    private ItemStack createMissionItem(Mission mission,String status,int current,int inventorySize,ConfigurationSection templates){
        ConfigurationSection cfg=templates==null?null:templates.getConfigurationSection(status);
        String name=cfg==null?defaultName(status,mission.name):cfg.getString("name",defaultName(status,mission.name));
        List<String> lore=cfg==null?defaultLore(status):cfg.getStringList("lore");
        Map<String,String> placeholders=new LinkedHashMap<>();
        String displayName=gui.getString("mission-items.names."+mission.id,mission.name);
        placeholders.put("name",displayName);placeholders.put("progress",String.valueOf(current));placeholders.put("target",String.valueOf(mission.target));
        placeholders.put("percent",String.valueOf(Math.min(100,(int)Math.floor(current*100.0/mission.target))));
        placeholders.put("action",status.equals("claimed")?"<green>✔ REWARD CLAIMED":status.equals("ready")?"<yellow>➜ CLICK TO CLAIM":"<gray>◷ MISSION IN PROGRESS");
        String rewards="";for(String raw:mission.rewards){String line=(gui.getString("mission-items.reward-format","<gold>✦ <white>{reward}" )).replace("{reward}",formatReward(raw));rewards+=(rewards.isEmpty()?"":"\n")+line;}
        placeholders.put("rewards",rewards);
        Material icon=material(gui.getString("mission-items.icons."+mission.id,mission.icon),Material.PAPER);
        String resolvedName=Text.replace(name,placeholders);
        List<String> resolvedLore=new ArrayList<>();for(String line:lore){
            if(line.contains("{rewards}")){if(!rewards.isBlank())resolvedLore.addAll(List.of(rewards.split("\\n")));}
            else resolvedLore.add(Text.replace(line,placeholders));
        }
        return makeItem(icon,resolvedName,resolvedLore,cfg!=null&&cfg.getBoolean("glow",status.equals("ready")),Map.of());
    }

    private String defaultName(String status,String name){return switch(status){case "claimed"->"<green><bold>✔ "+name;case "ready"->"<gold><bold>✦ "+name;default->"<white><bold>◈ "+name;};}
    private List<String> defaultLore(String status){return List.of("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━","<gray>Progress: <white>{progress}<dark_gray>/<white>{target} <dark_gray>(<aqua>{percent}%<dark_gray>)","","<gold><bold>REWARDS","{rewards}","","{action}","<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━");}

    private ItemStack makeItem(Material material,String name,List<String> lore,boolean glow,Map<String,String> replacements){
        ItemStack stack=new ItemStack(material);ItemMeta meta=stack.getItemMeta();if(meta==null)return stack;
        meta.setDisplayName(Text.color(Text.replace(name,replacements)));
        List<String> colored=new ArrayList<>();for(String line:lore)colored.add(Text.color(Text.replace(line,replacements)));
        meta.setLore(colored);if(glow)meta.setEnchantmentGlintOverride(true);stack.setItemMeta(meta);return stack;
    }

    private String formatReward(String raw){
        if(raw==null||raw.isBlank())return "Bonus reward";
        String value=raw.trim();
        if(value.regionMatches(true,0,"COMMAND:",0,8))return "Special bonus";
        String[] parts=value.split(":");
        if(parts.length>=3&&(parts[0].equalsIgnoreCase("VANILLA")||parts[0].equalsIgnoreCase("MMOITEMS"))){
            int amount=parseAmount(parts[parts.length-1]);String ref;
            if(parts[0].equalsIgnoreCase("VANILLA"))ref="VANILLA:"+parts[1];
            else if(parts.length>=4)ref="MMOITEMS:"+parts[1]+":"+parts[2];else return "Special item reward";
            ItemStack item=plugin.items().resolve(ref);String name=null;
            if(item!=null&&item.hasItemMeta()&&item.getItemMeta().hasDisplayName())name=Text.strip(item.getItemMeta().getDisplayName());
            if(name==null||name.isBlank())name=Fmt.pretty(parts[0].equalsIgnoreCase("VANILLA")?parts[1]:parts[2]);
            name=pluralize(name,amount);
            return amount+"x "+name;
        }
        return "Special bonus";
    }
    private int parseAmount(String raw){try{return Math.max(1,Integer.parseInt(raw));}catch(NumberFormatException ex){return 1;}}
    private String pluralize(String name,int amount){if(amount==1)return name;String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith("s")||lower.endsWith("es"))return name;return name+"s";}
    private Material material(String raw,Material fallback){Material material=Material.matchMaterial(raw==null?"":raw);return material==null||!material.isItem()?fallback:material;}

    @EventHandler public void onClick(InventoryClickEvent event){
        if(!(event.getInventory().getHolder() instanceof MissionMenu holder))return;
        event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player)||event.getClickedInventory()!=event.getInventory())return;
        String id=holder.slots.get(event.getRawSlot());if(id==null)return;claim(player,id);Bukkit.getScheduler().runTask(plugin,()->open(player));
    }
    @EventHandler public void onDrag(InventoryDragEvent event){if(event.getInventory().getHolder() instanceof MissionMenu)event.setCancelled(true);}
    @EventHandler public void onJoin(PlayerJoinEvent event){state(event.getPlayer().getUniqueId());}
    @EventHandler public void onQuit(PlayerQuitEvent event){save(event.getPlayer().getUniqueId());}

    private void claim(Player player,String id){
        Mission mission=missions.get(id);if(mission==null)return;State state=state(player.getUniqueId());
        if(state.claimed.getOrDefault(id,false)){sendPrefixed(player,"<red>Mission sudah diklaim.");return;}
        if(state.progress.getOrDefault(id,0)<mission.target){sendPrefixed(player,"<red>Mission belum selesai.");return;}
        for(String reward:mission.rewards)if(!canResolveReward(reward)){sendPrefixed(player,"<red>Reward belum tersedia. Pastikan MMOItems/item hook yang dibutuhkan aktif.");return;}
        for(String reward:mission.rewards)giveReward(player,reward);
        state.claimed.put(id,true);save(player.getUniqueId());sendPrefixed(player,"<green>Reward Daily Mission diterima: <white>"+mission.name);
    }
    private boolean canResolveReward(String raw){
        if(raw==null||raw.isBlank())return true;String value=raw.trim();if(value.regionMatches(true,0,"COMMAND:",0,8))return true;
        String[] parts=value.split(":");
        if(parts.length>=3&&parts[0].equalsIgnoreCase("VANILLA"))return plugin.items().resolve("VANILLA:"+parts[1])!=null;
        if(parts.length>=4&&parts[0].equalsIgnoreCase("MMOITEMS"))return plugin.items().resolve("MMOITEMS:"+parts[1]+":"+parts[2])!=null;
        return false;
    }
    private void giveReward(Player player,String raw){
        if(raw==null||raw.isBlank())return;String value=raw.trim();
        if(value.regionMatches(true,0,"COMMAND:",0,8)){Bukkit.dispatchCommand(Bukkit.getConsoleSender(),value.substring(8).replace("{player}",player.getName()));return;}
        String[] parts=value.split(":");if(parts.length<3)return;int amount=parseAmount(parts[parts.length-1]);
        String ref=parts[0].equalsIgnoreCase("VANILLA")?"VANILLA:"+parts[1]:parts[0].equalsIgnoreCase("MMOITEMS")&&parts.length>=4?"MMOITEMS:"+parts[1]+":"+parts[2]:"";
        if(ref.isBlank())return;ItemStack base=plugin.items().resolve(ref);if(base==null)return;
        int left=amount,max=Math.max(1,base.getMaxStackSize());while(left>0){int count=Math.min(left,max);ItemStack copy=base.clone();copy.setAmount(count);plugin.items().giveOrDrop(player,copy);left-=count;}
    }
    public Map<String,Object> snapshot(UUID id){State state=state(id);Map<String,Object> out=new LinkedHashMap<>();out.put("date",state.date);out.put("playtime",state.playtime);out.put("progress",new LinkedHashMap<>(state.progress));out.put("claimed",new LinkedHashMap<>(state.claimed));return out;}
}
