package com.octagens.plugin.command;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.PlacedEnergy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /mpc - Magicule Power Collector shop and management shortcuts. */
public final class MpcCommand implements CommandExecutor, TabCompleter {
    private final OctaGensPlugin plugin;
    public MpcCommand(OctaGensPlugin plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player p)){plugin.messages().send(sender,"player-only");return true;}
        if(args.length==0){plugin.guis().openEnergyShop(p,1);return true;}
        switch(args[0].toLowerCase(Locale.ROOT)){
            case "upgrade" -> plugin.guis().openMpcUpgrade(p);
            case "takeall" -> {
                List<PlacedEnergy> copy=new ArrayList<>(plugin.energy().list(p.getUniqueId()));
                int picked=0;for(PlacedEnergy e:copy)if(plugin.energy().pickup(p,e))picked++;
                plugin.messages().send(p,"mpc-takeall","count",String.valueOf(picked));
            }
            default -> plugin.messages().send(p,"mpc-help");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==1){List<String> out=new ArrayList<>();for(String s:List.of("upgrade","takeall"))if(s.startsWith(args[0].toLowerCase(Locale.ROOT)))out.add(s);return out;}return List.of();
    }
}
