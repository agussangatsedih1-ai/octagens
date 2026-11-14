package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SuperiorSkyblock2 integration through the published SuperiorSkyblockAPI only.
 * Reflection keeps the runtime optional while the API dependency remains compileOnly/provided.
 */
public final class SuperiorSkyblockHook {
    private final OctaGensPlugin plugin;
    private Class<?> api;
    private boolean ready;
    private int apiVersion=-1;
    private boolean warnedPermissionLookup;

    public SuperiorSkyblockHook(OctaGensPlugin plugin){this.plugin=plugin;}

    public boolean init(){
        if(!Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2"))return false;
        try{
            api=Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
            Method version=api.getMethod("getAPIVersion");apiVersion=((Number)version.invoke(null)).intValue();
            if(apiVersion<1)throw new IllegalStateException("Invalid SuperiorSkyblock API version");
            ready=true;plugin.getLogger().info("Hook SuperiorSkyblock2 aktif melalui SuperiorSkyblockAPI (API v"+apiVersion+").");return true;
        }catch(Throwable ex){plugin.getLogger().warning("SuperiorSkyblock2 terdeteksi, tetapi API tidak dapat diakses: "+ex.getMessage());return false;}
    }
    public boolean isReady(){return ready;}
    public int apiVersion(){return apiVersion;}


    /** Checks SuperiorSkyblock island privileges through the public API. Locations outside islands are allowed. */
    public boolean canBuild(Player player, Location location) {
        return hasIslandPrivilege(player, location, "BUILD");
    }

    /** Checks SuperiorSkyblock island privileges through the public API. Locations outside islands are allowed. */
    public boolean canBreak(Player player, Location location) {
        return hasIslandPrivilege(player, location, "BREAK");
    }

    private boolean hasIslandPrivilege(Player player, Location location, String privilegeName) {
        if (!ready || player == null || location == null) return true;
        try {
            Object island;
            try {
                island = invokeStatic(api, "getIslandAt", new Class<?>[]{Location.class}, location);
            } catch (NoSuchMethodException ex) {
                Object grid = invokeStatic(api, "getGrid", new Class<?>[]{});
                island = grid == null ? null : invoke(grid, "getIslandAt", new Class<?>[]{Location.class}, location);
            }
            if (island == null) return true;

            Class<?> privilegeType = Class.forName("com.bgsoftware.superiorskyblock.api.island.IslandPrivilege");
            Object privilege = privilegeType.getMethod("getByName", String.class).invoke(null, privilegeName);
            if (privilege == null) return true;
            Class<?> islandType = Class.forName("com.bgsoftware.superiorskyblock.api.island.Island");
            Class<?> senderType = Class.forName("org.bukkit.command.CommandSender");
            Object result = islandType.getMethod("hasPermission", senderType, privilegeType).invoke(island, player, privilege);
            return !(result instanceof Boolean allowed) || allowed;
        } catch (Throwable ex) {
            if (!warnedPermissionLookup) {
                warnedPermissionLookup = true;
                plugin.getLogger().warning("SuperiorSkyblock island permission check failed; OctaGens placement/break is denied inside the affected island: " + ex.getMessage());
            }
            return false;
        }
    }

    /** Returns stable placeholder values derived from public API island/player objects. */
    public Map<String,String> placeholders(Player player){
        Map<String,String> out=new LinkedHashMap<>();out.put("island_name","-");out.put("island_owner","-");out.put("island_members","0");out.put("island_role","-");
        if(!ready||player==null)return out;
        try{
            Object superiorPlayer=invokeStatic(api,"getPlayer",new Class<?>[]{java.util.UUID.class},player.getUniqueId());
            Class<?> superiorPlayerType=Class.forName("com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer");
            Class<?> islandType=Class.forName("com.bgsoftware.superiorskyblock.api.island.Island");
            Object island=superiorPlayer==null?null:superiorPlayerType.getMethod("getIsland").invoke(superiorPlayer);
            if(island==null)island=invokeStatic(api,"getIslandAt",new Class<?>[]{Location.class},player.getLocation());
            if(island==null)return out;
            Object name=islandType.getMethod("getName").invoke(island);if(name!=null)out.put("island_name",String.valueOf(name));
            Object owner=islandType.getMethod("getOwner").invoke(island);
            if(owner!=null){Object ownerName=superiorPlayerType.getMethod("getName").invoke(owner);out.put("island_owner",String.valueOf(ownerName==null?owner:ownerName));}
            Object members=islandType.getMethod("getIslandMembers", boolean.class).invoke(island, true);if(members instanceof java.util.Collection<?> c)out.put("island_members",String.valueOf(c.size()));
            Object role=superiorPlayer==null?null:superiorPlayerType.getMethod("getPlayerRole").invoke(superiorPlayer);
            if(role!=null)out.put("island_role",String.valueOf(role));
        }catch(Throwable ex){plugin.getLogger().fine("SuperiorSkyblock API placeholder lookup failed: "+ex.getMessage());}
        return out;
    }
    private Object invokeStatic(Class<?> c,String name,Class<?>[] types,Object...args)throws Exception{Method m=c.getMethod(name,types);if(!Modifier.isStatic(m.getModifiers()))return null;return m.invoke(null,args);}
    private Object invoke(Object target,String name){try{return invoke(target,name,new Class<?>[]{});}catch(Exception ignored){return null;}}
    private Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getMethod(name,types);return m.invoke(target,args);}
}
