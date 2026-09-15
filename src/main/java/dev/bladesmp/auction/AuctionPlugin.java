package dev.bladesmp.auction;

import dev.bladesmp.money.Economy;
import dev.bladesmp.money.MoneyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AuctionPlugin extends JavaPlugin {

    private Economy economy;
    private Listings listings;
    private Guis guis;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Money comes from SMPmoneyplugin (it must be installed too)
        MoneyPlugin money = (MoneyPlugin) Bukkit.getPluginManager().getPlugin("SMPmoneyplugin");
        if (money == null) {
            getLogger().severe("SMPmoneyplugin is missing! Install it first.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        economy = money.getEconomy();

        listings = new Listings(this);
        guis = new Guis(this);
        Bukkit.getPluginManager().registerEvents(guis, this);

        Commands commands = new Commands(this);
        getCommand("ah").setExecutor(commands);
        getCommand("sell").setExecutor(commands);
        getCommand("worth").setExecutor(commands);

        // Every minute: move expired listings to their seller's claim box
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            public void run() {
                listings.expireSweep();
            }
        }, 20L * 60, 20L * 60);

        // Every 5 minutes: save to disk just in case
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            public void run() {
                listings.save();
            }
        }, 20L * 300, 20L * 300);

        getLogger().info("Auction house is open for business!");
    }

    @Override
    public void onDisable() {
        if (listings != null) listings.save();
    }

    public Economy getEconomy() {
        return economy;
    }

    public Listings getListings() {
        return listings;
    }

    public Guis getGuis() {
        return guis;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "&6&lAH &8» &f");
        return color(prefix + getConfig().getString("messages." + key, key));
    }

    /** A friendly name for an item, like "Diamond Sword". */
    public String itemName(org.bukkit.inventory.ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String raw = item.getType().name().toLowerCase().replace('_', ' ');
        StringBuilder pretty = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (word.isEmpty()) continue;
            pretty.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(' ');
        }
        return pretty.toString().trim();
    }

    public void sound(Player player, org.bukkit.Sound sound) {
        player.playSound(player.getLocation(), sound, 1f, 1f);
    }
}
