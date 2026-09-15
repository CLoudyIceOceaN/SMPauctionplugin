package dev.bladesmp.auction;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Commands implements CommandExecutor {

    private final AuctionPlugin plugin;

    public Commands(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }
        Player player = (Player) sender;
        String name = command.getName().toLowerCase();

        if (name.equals("sell")) {
            plugin.getGuis().openSell(player);
            return true;
        }

        if (name.equals("worth")) {
            ItemStack hand = player.getItemInHand();
            if (hand == null || hand.getType() == Material.AIR) {
                player.sendMessage(plugin.msg("hold-something"));
                return true;
            }
            double each = plugin.getConfig().getDouble("sell-prices." + hand.getType().name(), 0);
            if (each <= 0) {
                player.sendMessage(plugin.msg("worth-nothing")
                        .replace("%item%", plugin.itemName(hand)));
            } else {
                player.sendMessage(plugin.msg("worth")
                        .replace("%item%", plugin.itemName(hand))
                        .replace("%each%", plugin.getEconomy().format(each))
                        .replace("%stack%", plugin.getEconomy().format(each * hand.getAmount())));
            }
            return true;
        }

        if (name.equals("ah")) {
            if (args.length == 0) {
                plugin.getGuis().openAh(player, 0, Listings.SORT_NEWEST);
                return true;
            }
            if (args[0].equalsIgnoreCase("sell")) {
                if (args.length < 2) {
                    player.sendMessage(plugin.msg("ah-sell-usage"));
                    return true;
                }
                listItem(player, args[1]);
                return true;
            }
            // /ah <item> — search the auction house, cheapest first
            StringBuilder search = new StringBuilder();
            for (String arg : args) {
                if (search.length() > 0) search.append(' ');
                search.append(arg);
            }
            plugin.getGuis().openAh(player, 0, Listings.SORT_CHEAP,
                    search.toString(), null);
            return true;
        }
        return true;
    }

    private void listItem(Player player, String priceText) {
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(plugin.msg("hold-something"));
            return;
        }
        double price = parsePrice(priceText);
        double min = plugin.getConfig().getDouble("listing.min-price", 1);
        double max = plugin.getConfig().getDouble("listing.max-price", 1000000000000d);
        if (price < min || price > max) {
            player.sendMessage(plugin.msg("bad-price"));
            return;
        }
        int maxListings = plugin.getConfig().getInt("listing.max-per-player", 10);
        if (plugin.getListings().countFor(player.getUniqueId()) >= maxListings) {
            player.sendMessage(plugin.msg("too-many-listings")
                    .replace("%max%", String.valueOf(maxListings)));
            return;
        }
        double feePercent = plugin.getConfig().getDouble("listing.fee-percent", 0);
        double fee = Math.floor(price * feePercent / 100.0);
        if (fee > 0 && !plugin.getEconomy().take(player, fee)) {
            player.sendMessage(plugin.msg("cant-pay-fee")
                    .replace("%fee%", plugin.getEconomy().format(fee)));
            return;
        }

        plugin.getListings().add(player, hand, price);
        player.setItemInHand(null);
        String done = plugin.msg("listed")
                .replace("%item%", plugin.itemName(hand))
                .replace("%amount%", plugin.getEconomy().format(price));
        if (fee > 0) done += plugin.color(" &7(fee: " + plugin.getEconomy().format(fee) + ")");
        player.sendMessage(done);
        plugin.sound(player, org.bukkit.Sound.NOTE_PLING);
    }

    // Accepts 100, 2k, 1.5m, 1b ...
    private double parsePrice(String text) {
        try {
            String t = text.toLowerCase().replace(",", "");
            double multiplier = 1;
            if (t.endsWith("k")) { multiplier = 1_000d; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("m")) { multiplier = 1_000_000d; t = t.substring(0, t.length() - 1); }
            else if (t.endsWith("b")) { multiplier = 1_000_000_000d; t = t.substring(0, t.length() - 1); }
            double value = Double.parseDouble(t) * multiplier;
            if (Double.isNaN(value) || Double.isInfinite(value)) return -1;
            return Math.floor(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
