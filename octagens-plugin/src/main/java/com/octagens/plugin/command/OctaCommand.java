package com.octagens.plugin.command;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.EnergyType;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedEnergy;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.model.SellwandType;
import com.octagens.plugin.manager.SlotManager;
import com.octagens.plugin.util.Fmt;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /octagens (aliases /ogens, /og). */
public final class OctaCommand implements CommandExecutor, TabCompleter {
    private final OctaGensPlugin plugin;

    public OctaCommand(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    private void sendPrefixed(CommandSender sender, String message) {
        sender.sendMessage(plugin.messages().get("prefix") + com.octagens.plugin.util.Text.color(message));
    }

    private boolean player(CommandSender s) {
        if (s instanceof Player) return true;
        plugin.messages().send(s, "player-only");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) plugin.guis().openMain(p);
            else help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender);
            case "shop" -> {
                if (player(sender)) plugin.guis().openShop((Player) sender, 1);
            }
            case "magiculeshop" -> {
                if (player(sender)) plugin.guis().openEnergyShop((Player) sender, 1);
            }
            case "upgrade" -> upgrade(sender, args);
            case "reset" -> reset(sender, args);
            case "set" -> set(sender, args);
            case "giveaway" -> giveaway(sender, args);
            case "sellwand" -> sellwand(sender, args);
            case "settexture" -> setTexture(sender, args);
            case "trade" -> trade(sender, args);
            case "ver", "version" -> version(sender);
            case "sell" -> sell(sender, args);
            case "list" -> {
                if (player(sender)) list((Player) sender);
            }
            case "give" -> give(sender, args);
            case "remove" -> remove(sender, args);
            case "setbonus" -> setBonus(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("octagens.admin.reload")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                long ms = plugin.reloadAll();
                plugin.messages().send(sender, "reloaded", "ms", String.valueOf(ms));
            }
            default -> plugin.messages().send(sender, "unknown-command");
        }
        return true;
    }

    private void help(CommandSender s) {
        plugin.messages().send(s, "help");
        if (s.hasPermission("octagens.admin.give") || s.hasPermission("octagens.admin.reload")) plugin.messages().send(s, "help-admin");
    }

    private void sell(CommandSender sender, String[] args) {
        if (!player(sender)) return;
        Player p = (Player) sender;
        boolean hand = args.length > 1 && args[1].equalsIgnoreCase("hand");
        if (!hand && plugin.settings().sellGui) plugin.guis().openSell(p);
        else plugin.sell().sell(p, hand);
    }

    private void list(Player p) {
        UUID id = p.getUniqueId();
        Map<String, Integer> gens = new LinkedHashMap<>();
        for (PlacedGenerator g : plugin.generators().list(id)) gens.merge(g.typeId, 1, Integer::sum);
        Map<String, Integer> energies = new LinkedHashMap<>();
        for (PlacedEnergy e : plugin.energy().list(id)) energies.merge(e.typeId, 1, Integer::sum);
        int gl = plugin.slots().generatorLimit(p);
        int el = plugin.slots().energyLimit(p);
        plugin.messages().send(p, "list-header",
                "generators", String.valueOf(plugin.generators().count(id)), "generators_max", limit(gl),
                "energies", String.valueOf(plugin.energy().count(id)), "energy_max", limit(el),
                "mcu", Fmt.number(plugin.energy().totalMcu(id)),
                "pool", Fmt.number(Math.floor(plugin.energy().pool(id).current)), "pool_max", Fmt.number(plugin.energy().pool(id).max));
        if (gens.isEmpty() && energies.isEmpty()) {
            plugin.messages().send(p, "list-empty");
            return;
        }
        for (Map.Entry<String, Integer> en : gens.entrySet()) {
            GeneratorType t = plugin.registry().generator(en.getKey());
            plugin.messages().send(p, "list-generator-line", "name", t == null ? en.getKey() : t.displayName, "count", String.valueOf(en.getValue()));
        }
        for (Map.Entry<String, Integer> en : energies.entrySet()) {
            EnergyType t = plugin.registry().energy(en.getKey());
            plugin.messages().send(p, "list-energy-line", "name", t == null ? en.getKey() : t.displayName, "count", String.valueOf(en.getValue()));
        }
    }

    private String limit(int v) {
        return v == SlotManager.UNLIMITED ? plugin.messages().get("placeholder-unlimited") : String.valueOf(v);
    }

    private void trade(CommandSender sender, String[] args) {
        if (!player(sender)) return;
        Player p = (Player) sender;
        if (!p.hasPermission("octagens.trade")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (plugin.registry().tradeCategories().isEmpty()) {
            plugin.messages().send(p, "trade-no-categories");
            return;
        }
        if (args.length > 1) {
            var cat = plugin.registry().tradeCategory(args[1]);
            if (cat == null) {
                plugin.messages().send(p, "trade-unknown-category", "category", args[1]);
                return;
            }
            if (!plugin.trade().categoryAllowed(p, cat)) {
                plugin.messages().send(p, "trade-locked");
                return;
            }
            plugin.guis().openTradeCategory(p, cat.id, 1);
            return;
        }
        plugin.guis().openTrade(p, 1);
    }

    private void setTexture(CommandSender sender, String[] args) {
        if (!sender.hasPermission("octagens.admin.give")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-settexture");
            return;
        }
        EnergyType t = plugin.registry().energy(args[1]);
        if (t == null) {
            plugin.messages().send(sender, "invalid-id", "id", args[1]);
            return;
        }
        String value = null;
        if (args.length > 2) {
            value = args[2];
            String url = com.octagens.plugin.item.ItemService.textureUrl(value);
            if (url != null && value.startsWith("http")) {
                value = java.util.Base64.getEncoder().encodeToString(
                        ("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } else if (sender instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm && sm.getOwnerProfile() != null
                    && sm.getOwnerProfile().getTextures().getSkin() != null) {
                String url = sm.getOwnerProfile().getTextures().getSkin().toString();
                value = java.util.Base64.getEncoder().encodeToString(
                        ("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                plugin.messages().send(sender, "texture-no-head");
                return;
            }
        } else {
            plugin.messages().send(sender, "usage-settexture");
            return;
        }
        if (!com.octagens.plugin.item.ItemService.validTexture(value)) {
            plugin.messages().send(sender, "texture-invalid");
            return;
        }
        plugin.registry().setTexture(t.id, value);
        plugin.messages().send(sender, "texture-set", "name", t.displayName, "id", t.id);
    }

    private void version(CommandSender sender) {
        @SuppressWarnings("deprecation")
        String v = plugin.getDescription().getVersion();
        plugin.messages().send(sender, "ver-header", "version", v);
        plugin.messages().send(sender, "ver-server", "server", Bukkit.getVersion());
        plugin.messages().send(sender, "ver-lang", "lang", plugin.messages().code(), "db", plugin.settings().dbType);
        plugin.messages().send(sender, "ver-content", "generators", String.valueOf(plugin.registry().generators().size()),
                "collectors", String.valueOf(plugin.registry().energies().size()),
                "placed", String.valueOf(plugin.generators().total()));
        plugin.messages().send(sender, "ver-hooks", "hooks", plugin.hooks().summary());
    }

    private void sellwand(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("shop")) {
            if (player(sender)) plugin.sellwandShop().open((Player) sender);
            return;
        }
        if (!sender.hasPermission("octagens.admin.give")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            plugin.messages().send(sender, "usage-sellwand");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "invalid-player", "player", args[2]);
            return;
        }
        SellwandType t = plugin.registry().wand(args[3]);
        if (t == null) {
            plugin.messages().send(sender, "invalid-id", "id", args[3]);
            return;
        }
        int amount = 1;
        int uses = t.uses;
        double mult = t.multiplier;
        String bad = null;
        try {
            if (args.length > 4) {
                bad = args[4];
                amount = Integer.parseInt(args[4]);
            }
            if (args.length > 5) {
                bad = args[5];
                int u = Integer.parseInt(args[5]);
                uses = u < 1 ? -1 : u;
            }
            if (args.length > 6) {
                bad = args[6];
                mult = Double.parseDouble(args[6]); // no upper limit, decimals allowed
                if (!Double.isFinite(mult) || mult < 0) throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "invalid-number", "value", String.valueOf(bad));
            return;
        }
        if (amount < 1 || amount > 64) {
            plugin.messages().send(sender, "invalid-number", "value", String.valueOf(amount));
            return;
        }
        for (int i = 0; i < amount; i++) {
            ItemStack item = plugin.items().wandItem(t, uses, mult, 1, target);
            if (item == null) {
                plugin.messages().send(sender, "generator-item-unavailable");
                return;
            }
            plugin.items().giveOrDrop(target, item);
        }
        plugin.messages().send(sender, "sellwand-given", "amount", String.valueOf(amount), "id", t.id,
                "player", target.getName(), "multiplier", Fmt.number(mult));
        if (sender != target) plugin.messages().send(target, "sellwand-received", "amount", String.valueOf(amount), "id", t.id, "multiplier", Fmt.number(mult));
    }

    private void reset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("octagens.admin.energy")) { plugin.messages().send(sender, "no-permission"); return; }
        if (args.length != 3 || !args[1].equalsIgnoreCase("energy")) {
            sendPrefixed(sender,"<yellow>/og reset energy <player>"); return;
        }
        UUID id=find(args[2]);
        if(id==null){plugin.messages().send(sender,"invalid-player","player",args[2]);return;}
        plugin.energy().resetCurrent(id);
        sendPrefixed(sender,"<green>Magicule player "+args[2]+" telah di-reset ke default: "+Fmt.number(plugin.settings().mcuMaxDefault)+" MCu.");
        Player online=Bukkit.getPlayer(id);if(online!=null)sendPrefixed(online,"<yellow>Magicule Power kamu telah di-reset oleh administrator.");
    }

    private void set(CommandSender sender, String[] args) {
        if (!sender.hasPermission("octagens.admin.energy")) { plugin.messages().send(sender, "no-permission"); return; }
        if(args.length!=4 || !args[1].equalsIgnoreCase("energylevel")) {
            sendPrefixed(sender,"<yellow>/og set energylevel <player> <level>");return;
        }
        UUID id=find(args[2]);if(id==null){plugin.messages().send(sender,"invalid-player","player",args[2]);return;}
        int level;try{level=Integer.parseInt(args[3]);}catch(NumberFormatException ex){plugin.messages().send(sender,"invalid-number","value",args[3]);return;}
        if(level<0 || !plugin.energy().setLevel(id,level)) {sendPrefixed(sender,"<red>Level tidak valid atau melebihi batas Max Magicule di config.");return;}
        sendPrefixed(sender,"<green>Max Magicule player "+args[2]+" sekarang level "+level+" ("+Fmt.number(plugin.energy().pool(id).max)+" MCu).");
        Player online=Bukkit.getPlayer(id);if(online!=null)sendPrefixed(online,"<yellow>Max Magicule kamu diatur ke level "+level+" oleh administrator.");
    }

    private void giveaway(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { plugin.messages().send(sender,"player-only"); return; }
        if(args.length>1 && !List.of("drop-booster","mcu-booster","sell-booster").contains(args[1].toLowerCase(Locale.ROOT))) {
            sendPrefixed(sender,"<yellow>/og giveaway <drop-booster|mcu-booster|sell-booster>");return;
        }
        if(args.length>1){
            var type=switch(args[1].toLowerCase(Locale.ROOT)){case "drop-booster"->com.octagens.plugin.manager.BoosterManager.Type.DROP;case "sell-booster"->com.octagens.plugin.manager.BoosterManager.Type.SELL;default->com.octagens.plugin.manager.BoosterManager.Type.MCU;};
            plugin.boosters().openMenu(p,type);
        } else plugin.boosters().openMenu(p);
    }

    private void upgrade(CommandSender sender, String[] args) {
        if (!player(sender)) return;
        Player p = (Player) sender;
        int n = 1;
        if (args.length > 1) {
            if (args[1].equalsIgnoreCase("max")) {
                n = -1;
            } else {
                try {
                    n = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    plugin.messages().send(p, "invalid-number", "value", args[1]);
                    return;
                }
                if (n < 1 || n > 100000) {
                    plugin.messages().send(p, "invalid-number", "value", args[1]);
                    return;
                }
            }
        }
        plugin.energy().upgrade(p, n);
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("octagens.admin.give")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            plugin.messages().send(sender, "usage-give");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "invalid-player", "player", args[1]);
            return;
        }
        int amount = 1;
        if (args.length > 4) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                plugin.messages().send(sender, "invalid-number", "value", args[4]);
                return;
            }
            if (amount < 1 || amount > 2304) {
                plugin.messages().send(sender, "invalid-number", "value", args[4]);
                return;
            }
        }
        String kind = args[2].toLowerCase(Locale.ROOT);
        ItemStack item;
        String name;
        if (kind.equals("generator")) {
            GeneratorType t = plugin.registry().generator(args[3]);
            if (t == null) {
                plugin.messages().send(sender, "invalid-id", "id", args[3]);
                return;
            }
            item = plugin.items().generatorItem(t, 1);
            name = t.displayName;
        } else if (kind.equals("energy")) {
            EnergyType t = plugin.registry().energy(args[3]);
            if (t == null) {
                plugin.messages().send(sender, "invalid-id", "id", args[3]);
                return;
            }
            item = plugin.items().energyItem(t, 1);
            name = t.displayName;
        } else {
            plugin.messages().send(sender, "usage-give");
            return;
        }
        if (item == null) {
            plugin.messages().send(sender, "generator-item-unavailable");
            return;
        }
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, Math.max(1, item.getMaxStackSize()));
            ItemStack c = item.clone();
            c.setAmount(n);
            plugin.items().giveOrDrop(target, c);
            left -= n;
        }
        plugin.messages().send(sender, "give-success", "amount", String.valueOf(amount), "name", name, "player", target.getName());
        if (sender != target) plugin.messages().send(target, "give-received", "amount", String.valueOf(amount), "name", name);
    }

    private UUID find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op.getUniqueId();
        }
        return null;
    }

    private void remove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("octagens.admin.remove")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "usage-remove");
            return;
        }
        UUID id = find(args[1]);
        if (id == null) {
            plugin.messages().send(sender, "invalid-player", "player", args[1]);
            return;
        }
        String scope = args[2].toLowerCase(Locale.ROOT);
        String filter = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : null;
        boolean gens = scope.equals("generators") || scope.equals("all");
        boolean energies = scope.equals("energy") || scope.equals("all");
        if (!gens && !energies) {
            plugin.messages().send(sender, "usage-remove");
            return;
        }
        String actor = "#octagens-" + sender.getName();
        int g = 0;
        int e = 0;
        if (gens) {
            for (PlacedGenerator pg : plugin.generators().list(id)) {
                if (filter != null && !pg.typeId.equals(filter)) continue;
                plugin.generators().removeSilently(pg, actor);
                g++;
            }
        }
        if (energies) {
            for (PlacedEnergy pe : plugin.energy().list(id)) {
                if (filter != null && !pe.typeId.equals(filter)) continue;
                plugin.energy().removeSilently(pe, actor);
                e++;
            }
        }
        plugin.messages().send(sender, "remove-success", "player", args[1], "generators", String.valueOf(g), "energies", String.valueOf(e));
    }

    private void setBonus(CommandSender sender, String[] args) {
        if (!sender.hasPermission("octagens.admin.bonus")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage-setbonus");
            return;
        }
        String m = args[1].toLowerCase(Locale.ROOT);
        if (m.equals("off") || m.equals("clear") || m.equals("0")) {
            plugin.weekend().clearManual();
            plugin.messages().send(sender, "bonus-cleared");
            return;
        }
        double mult;
        try {
            mult = Double.parseDouble(m);
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "invalid-number", "value", args[1]);
            return;
        }
        if (mult < 1.0 || mult > 1000) {
            plugin.messages().send(sender, "invalid-number", "value", args[1]);
            return;
        }
        long seconds = plugin.weekend().manualDefaultSeconds();
        if (args.length > 2) {
            seconds = Fmt.parseDuration(args[2]);
            if (seconds <= 0) {
                plugin.messages().send(sender, "invalid-duration", "value", args[2]);
                return;
            }
        }
        plugin.weekend().setManual(mult, seconds);
        plugin.messages().send(sender, "bonus-set", "multiplier", Fmt.number(mult), "duration", Fmt.duration(seconds));
    }

    // ------------------------------------------------------------------ tab completion

    private static List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) out.add(o);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "shop", "magiculeshop", "upgrade", "sell", "list", "trade", "version", "giveaway"));
            if (sender.hasPermission("octagens.admin.give")) {
                subs.add("give");
                subs.add("sellwand");
                subs.add("settexture");
            }
            if (sender.hasPermission("octagens.admin.remove")) subs.add("remove");
            if (sender.hasPermission("octagens.admin.energy")) { subs.add("reset"); subs.add("set"); }
            if (sender.hasPermission("octagens.admin.bonus")) subs.add("setbonus");
            if (sender.hasPermission("octagens.admin.reload")) {
                subs.add("reload");
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("giveaway") && args.length == 2) return filter(List.of("drop-booster", "mcu-booster", "sell-booster"), args[1]);
        if (sub.equals("reset") && args.length == 2) return filter(List.of("energy"), args[1]);
        if (sub.equals("reset") && args.length == 3) return onlineNames(args[2]);
        if (sub.equals("set") && args.length == 2) return filter(List.of("energylevel"), args[1]);
        if (sub.equals("set") && args.length == 3) return onlineNames(args[2]);
        if (sub.equals("set") && args.length == 4) return filter(List.of("0", "1", "5", "10", "50", "100"), args[3]);
        if (sub.equals("sell") && args.length == 2) return filter(List.of("hand"), args[1]);
        if (sub.equals("sellwand") && sender.hasPermission("octagens.admin.give")) {
            if (args.length == 2) return filter(List.of("shop", "give"), args[1]);
            if (args.length == 3) return onlineNames(args[2]);
            if (args.length == 4) {
                List<String> ids = new ArrayList<>();
                for (SellwandType t : plugin.registry().wands()) ids.add(t.id);
                return filter(ids, args[3]);
            }
            if (args.length == 5) return filter(List.of("1", "5"), args[4]);
            if (args.length == 6) return filter(List.of("100", "1000", "-1"), args[5]);
            if (args.length == 7) return filter(List.of("1", "1.5", "2.25", "10"), args[6]);
        }
        if (sub.equals("settexture") && sender.hasPermission("octagens.admin.give") && args.length == 2) {
            List<String> ids = new ArrayList<>();
            for (EnergyType t : plugin.registry().energies()) ids.add(t.id);
            return filter(ids, args[1]);
        }
        if (sub.equals("trade") && args.length == 2) {
            List<String> ids = new ArrayList<>();
            for (var c : plugin.registry().tradeCategories()) ids.add(c.id);
            return filter(ids, args[1]);
        }
        if (sub.equals("upgrade") && args.length == 2) return filter(List.of("1", "10", "100", "max"), args[1]);
        if (sub.equals("give") && sender.hasPermission("octagens.admin.give")) {
            if (args.length == 2) return onlineNames(args[1]);
            if (args.length == 3) return filter(List.of("generator", "energy"), args[2]);
            if (args.length == 4) {
                List<String> ids = new ArrayList<>();
                if (args[2].equalsIgnoreCase("generator")) for (GeneratorType t : plugin.registry().generators()) ids.add(t.id);
                else for (EnergyType t : plugin.registry().energies()) ids.add(t.id);
                return filter(ids, args[3]);
            }
            if (args.length == 5) return filter(List.of("1", "16", "64"), args[4]);
        }
        if (sub.equals("remove") && sender.hasPermission("octagens.admin.remove")) {
            if (args.length == 2) return onlineNames(args[1]);
            if (args.length == 3) return filter(List.of("generators", "energy", "all"), args[2]);
            if (args.length == 4) {
                List<String> ids = new ArrayList<>();
                if (args[2].equalsIgnoreCase("generators")) for (GeneratorType t : plugin.registry().generators()) ids.add(t.id);
                else if (args[2].equalsIgnoreCase("energy")) for (EnergyType t : plugin.registry().energies()) ids.add(t.id);
                return filter(ids, args[3]);
            }
        }
        if (sub.equals("setbonus") && sender.hasPermission("octagens.admin.bonus")) {
            if (args.length == 2) return filter(List.of("2", "3", "4", "off"), args[1]);
            if (args.length == 3) return filter(List.of("30m", "1h", "6h", "1d"), args[2]);
        }
        return List.of();
    }

    private List<String> onlineNames(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return filter(names, prefix);
    }
}
