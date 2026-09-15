package dev.bladesmp.auction;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All the auction house listings, saved in
 * plugins/SMPauctionplugin/listings.yml
 */
public class Listings {

    /** One item that is for sale. */
    public static class Listing {
        public final UUID id;
        public final UUID seller;
        public final String sellerName;
        public final double price;
        public final long created;
        public final long expires;
        public final ItemStack item;

        public Listing(UUID id, UUID seller, String sellerName, double price,
                       long created, long expires, ItemStack item) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.price = price;
            this.created = created;
            this.expires = expires;
            this.item = item;
        }
    }

    public static final int SORT_NEWEST = 0;
    public static final int SORT_CHEAP = 1;
    public static final int SORT_PRICEY = 2;

    private final AuctionPlugin plugin;
    private final File file;
    private final Map<UUID, Listing> active = new LinkedHashMap<UUID, Listing>();
    // Items waiting to go back to a player (expired or cancelled listings)
    private final Map<UUID, List<ItemStack>> claims = new HashMap<UUID, List<ItemStack>>();

    public Listings(AuctionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "listings.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection listSec = yaml.getConfigurationSection("listings");
        if (listSec != null) {
            for (String key : listSec.getKeys(false)) {
                try {
                    ConfigurationSection s = listSec.getConfigurationSection(key);
                    Listing listing = new Listing(
                            UUID.fromString(key),
                            UUID.fromString(s.getString("seller")),
                            s.getString("seller-name", "?"),
                            s.getDouble("price"),
                            s.getLong("created"),
                            s.getLong("expires"),
                            s.getItemStack("item"));
                    if (listing.item != null) active.put(listing.id, listing);
                } catch (Exception e) {
                    plugin.getLogger().warning("Skipped a broken listing: " + key);
                }
            }
        }
        ConfigurationSection claimSec = yaml.getConfigurationSection("claims");
        if (claimSec != null) {
            for (String key : claimSec.getKeys(false)) {
                try {
                    List<ItemStack> items = new ArrayList<ItemStack>();
                    for (Object o : claimSec.getList(key, new ArrayList<Object>())) {
                        if (o instanceof ItemStack) items.add((ItemStack) o);
                    }
                    if (!items.isEmpty()) claims.put(UUID.fromString(key), items);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Listing l : active.values()) {
            String base = "listings." + l.id + ".";
            yaml.set(base + "seller", l.seller.toString());
            yaml.set(base + "seller-name", l.sellerName);
            yaml.set(base + "price", l.price);
            yaml.set(base + "created", l.created);
            yaml.set(base + "expires", l.expires);
            yaml.set(base + "item", l.item);
        }
        for (Map.Entry<UUID, List<ItemStack>> entry : claims.entrySet()) {
            yaml.set("claims." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save listings: " + e.getMessage());
        }
    }

    public Listing add(Player seller, ItemStack item, double price) {
        long now = System.currentTimeMillis();
        long hours = plugin.getConfig().getLong("listing.duration-hours", 48);
        Listing listing = new Listing(UUID.randomUUID(), seller.getUniqueId(),
                seller.getName(), price, now, now + hours * 3600000L, item.clone());
        active.put(listing.id, listing);
        save();
        return listing;
    }

    public Listing get(UUID id) {
        return active.get(id);
    }

    public void remove(UUID id) {
        active.remove(id);
    }

    public int countFor(UUID seller) {
        int count = 0;
        for (Listing l : active.values()) {
            if (l.seller.equals(seller)) count++;
        }
        return count;
    }

    public List<Listing> allFor(UUID seller) {
        List<Listing> list = new ArrayList<Listing>();
        for (Listing l : active.values()) {
            if (l.seller.equals(seller)) list.add(l);
        }
        return list;
    }

    /** Everything for sale, in the order the player picked. */
    public List<Listing> sorted(int sort) {
        List<Listing> list = new ArrayList<Listing>(active.values());
        if (sort == SORT_CHEAP) {
            Collections.sort(list, new Comparator<Listing>() {
                public int compare(Listing a, Listing b) { return Double.compare(a.price, b.price); }
            });
        } else if (sort == SORT_PRICEY) {
            Collections.sort(list, new Comparator<Listing>() {
                public int compare(Listing a, Listing b) { return Double.compare(b.price, a.price); }
            });
        } else {
            Collections.sort(list, new Comparator<Listing>() {
                public int compare(Listing a, Listing b) { return Long.compare(b.created, a.created); }
            });
        }
        return list;
    }

    // ----- claim box (expired or cancelled items) -----

    public List<ItemStack> claimsFor(UUID player) {
        List<ItemStack> list = claims.get(player);
        return list == null ? new ArrayList<ItemStack>() : list;
    }

    public void addClaim(UUID player, ItemStack item) {
        List<ItemStack> list = claims.get(player);
        if (list == null) {
            list = new ArrayList<ItemStack>();
            claims.put(player, list);
        }
        list.add(item);
    }

    public ItemStack takeClaim(UUID player, int index) {
        List<ItemStack> list = claims.get(player);
        if (list == null || index < 0 || index >= list.size()) return null;
        ItemStack item = list.remove(index);
        if (list.isEmpty()) claims.remove(player);
        return item;
    }

    /** Moves anything past its end time into the seller's claim box. */
    public void expireSweep() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<UUID>();
        for (Listing l : active.values()) {
            if (l.expires <= now) expired.add(l.id);
        }
        if (expired.isEmpty()) return;
        for (UUID id : expired) {
            Listing l = active.remove(id);
            addClaim(l.seller, l.item);
            Player seller = Bukkit.getPlayer(l.seller);
            if (seller != null) {
                seller.sendMessage(plugin.msg("expired")
                        .replace("%item%", plugin.itemName(l.item)));
            }
        }
        save();
    }
}
