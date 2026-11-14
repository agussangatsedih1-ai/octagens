package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.manager.SlotManager;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** PlaceholderAPI expansion, identifier: octagens. */
public final class OctaPlaceholders extends PlaceholderExpansion {
    private final OctaGensPlugin plugin;

    public OctaPlaceholders(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "octagens"; }
    @Override public String getAuthor() { return "OctaGens"; }
    @Override @SuppressWarnings("deprecation") public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    private String limit(int v) {
        return v == SlotManager.UNLIMITED ? Text.strip(plugin.messages().get("placeholder-unlimited")) : String.valueOf(v);
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        switch (p) {
            case "weekend_active":
                return Text.strip(plugin.messages().get(plugin.weekend().isActive() ? "placeholder-yes" : "placeholder-no"));
            case "weekend_multiplier":
                return plugin.weekend().isActive() || plugin.weekend().isManualActive() ? plugin.weekend().displayMultiplier() : "1x";
            case "weekend_remaining":
                return plugin.weekend().isActive() ? Fmt.duration(plugin.weekend().remainingSeconds()) : "-";
            default:
                break;
        }
        if (player == null) return "";
        UUID id = player.getUniqueId();
        Player online = player.getPlayer();
        switch (p) {
            case "magicule_energy_me":
                return Fmt.number(plugin.energy().totalMcu(id));
            case "generators_placed":
                return String.valueOf(plugin.generators().count(id));
            case "generators_max":
                return limit(online != null ? plugin.slots().generatorLimit(online) : plugin.slots().generatorLimit(id));
            case "energy_placed":
                return String.valueOf(plugin.energy().count(id));
            case "energy_max":
                return limit(online != null ? plugin.slots().energyLimit(online) : plugin.slots().energyLimit(id));
            case "magicule_regen_me":
                return Fmt.number(plugin.energy().regenRate(id));
            case "magicule_current_me":
                return Fmt.number(Math.floor(plugin.energy().pool(id).current));
            case "magicule_max_me":
                return Fmt.number(plugin.energy().pool(id).max);
            case "magicule_percent_me": {
                com.octagens.plugin.model.EnergyPool pool = plugin.energy().pool(id);
                return Fmt.percent(pool.max <= 0 ? 0 : pool.current / pool.max);
            }
            case "magicule_level_me":
                return String.valueOf(plugin.energy().level(plugin.energy().pool(id)));
            case "magicule_upgrade_price_me": {
                com.octagens.plugin.model.EnergyPool pool = plugin.energy().pool(id);
                if (plugin.energy().remainingLevels(pool) <= 0) return Text.strip(plugin.messages().get("upgrade-maxed"));
                return Fmt.money(plugin.energy().upgradeCost(plugin.energy().level(pool), 1));
            }
            case "efficiency_me":
                return Fmt.percent(plugin.energy().averageEfficiency(id));
            default:
                return null;
        }
    }
}
