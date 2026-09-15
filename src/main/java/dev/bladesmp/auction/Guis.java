package dev.bladesmp.auction;

import dev.bladesmp.auction.Listings.Listing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All the menus: the auction house, the buy-confirm screen,
 * "your listings", and the /sell menu.
 */
public class Guis implements Listener {

    private final AuctionPlugin plugin;

    public Guis(AuctionPlugin plugin) {
        this.plugin = plugin;
    }

    // ===== holders: how we recognise our own menus when someone clicks =====

    private static class AhHolder implements InventoryHolder {
        int page; int sort;
        String search;        // /ah <item> search, or null
        Material filter;      // quick-buy item filter, or null
        Inventory inv;
        Map<Integer, UUID> slots = new HashMap<Integer, UUID>();
        public Inventory getInventory() { return inv; }
    }

    private static class ConfirmHolder implements InventoryHolder {
        UUID listingId; Inventory inv;
        // so cancel / after buying brings you back to the same view
        int page; int sort; String search; Material filter;
        public Inventory getInventory() { return inv; }
    }

    private static class QuickHolder implements InventoryHolder {
        Inventory inv;
        Map<Integer, Material> slots = new HashMap<Integer, Material>();
        public Inventory getInventory() { return inv; }
    }

    private static class MineHolder implements InventoryHolder {
        Inventory inv;
        Map<Integer, UUID> cancelSlots = new HashMap<Integer, UUID>();
        Map<Integer, Integer> claimSlots = new HashMap<Integer, Integer>();
        public Inventory getInventory() { return inv; }
    }

    private static class SellHolder implements InventoryHolder {
        Inventory inv;
        public Inventory getInventory() { return inv; }
    }

    // ===== the auction house =====

    public void openAh(Player player, int page, int sort) {
        openAh(player, page, sort, null, null);
    }

    public void openAh(Player player, int page, int sort, String search, Material filter) {
        List<Listing> all = visible(sort, search, filter);
        int pages = Math.max(1, (all.size() + 44) / 45);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        AhHolder holder = new AhHolder();
        holder.page = page;
        holder.sort = sort;
        holder.search = search;
        holder.filter = filter;
        String title = plugin.color(plugin.getConfig()
                .getString("gui.ah-title", "&8Auction House"));
        if (filter != null) title = plugin.color("&8Quick Buy: &0") + prettyName(filter);
        else if (search != null) title = plugin.color("&8Search: &0") + search;
        if (pages > 1) title = title + " (" + (page + 1) + "/" + pages + ")";
        Inventory inv = Bukkit.createInventory(holder, 54, trim(title));
        holder.inv = inv;

        int start = page * 45;
        for (int i = 0; i < 45 && start + i < all.size(); i++) {
            Listing l = all.get(start + i);
            inv.setItem(i, displayItem(l));
            holder.slots.put(i, l.id);
        }
        if (all.isEmpty()) {
            if (search != null || filter != null) {
                inv.setItem(22, item(Material.BARRIER, 0, "&cNothing found!",
                        "&7Nobody is selling that right now.",
                        "&7Click the sign to go back."));
            } else {
                inv.setItem(22, item(Material.BARRIER, 0, "&cNothing for sale yet!",
                        "&7Hold an item and type", "&e/ah sell <price>"));
            }
        }

        // ===== bottom bar (DonutSMP layout) =====
        // 45,46 spaces | 47 hopper sort | 48 ender chest quick buy
        // 49 anvil refresh | 50 sign search | 51 chest your stuff
        // 52 space/back arrow | 53 next page arrow
        ItemStack pane = item(Material.STAINED_GLASS_PANE, 7, "&7");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(47, item(Material.HOPPER, 0, "&bSort: &f" + sortName(sort),
                "&7Click to change the order"));
        inv.setItem(48, item(Material.ENDER_CHEST, 0, "&d&lQuick Buy",
                "&7Save your favorite items on",
                "&7the panes — then one click",
                "&7buys the cheapest &finstantly&7!",
                "", "&eClick &7to open"));
        inv.setItem(49, item(Material.ANVIL, 0, "&6Refresh",
                "&7Click to load the newest auctions"));
        if (search != null || filter != null) {
            inv.setItem(50, item(Material.SIGN, 0, "&eSearching: &f"
                            + (filter != null ? prettyName(filter) : search),
                    "&cClick to clear and see everything"));
        } else {
            inv.setItem(50, item(Material.SIGN, 0, "&eSearch",
                    "&7Close this menu and type:", "&f/ah <item name>",
                    "", "&7Example: &f/ah diamond"));
        }
        int claimCount = plugin.getListings().claimsFor(player.getUniqueId()).size();
        inv.setItem(51, item(Material.CHEST, 0, "&eYour Items",
                "&7For sale: &f" + plugin.getListings().countFor(player.getUniqueId()),
                "&7To claim: &f" + claimCount,
                "", "&eClick &7to manage or list items",
                "&7(list with &f/ah sell <price>&7)"));
        if (page > 0) inv.setItem(52, item(Material.ARROW, 0, "&e◀ Previous Page",
                "&7Page " + page + " of " + pages));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, 0, "&eNext Page ▶",
                "&7Page " + (page + 2) + " of " + pages));

        player.openInventory(inv);
    }

    /** The listings that match the current search or quick-buy filter. */
    private List<Listing> visible(int sort, String search, Material filter) {
        List<Listing> all = plugin.getListings().sorted(sort);
        if (search == null && filter == null) return all;
        List<Listing> out = new ArrayList<Listing>();
        for (Listing l : all) {
            if (filter != null && l.item.getType() != filter) continue;
            if (search != null) {
                String hay = (plugin.itemName(l.item) + " "
                        + l.item.getType().name().replace('_', ' ')).toLowerCase();
                if (!hay.contains(search.toLowerCase())) continue;
            }
            out.add(l);
        }
        return out;
    }

    private String prettyName(Material material) {
        String raw = material.name().toLowerCase().replace('_', ' ');
        StringBuilder pretty = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (word.isEmpty()) continue;
            pretty.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(' ');
        }
        return pretty.toString().trim();
    }

    // ===== quick buy: your own grid of saved items =====
    // Gray panes = empty slots. Pick up an item from your inventory and
    // click a pane to save it there. Clicking a saved item instantly
    // buys the cheapest auction of that item.

    public void openQuick(Player player) {
        QuickHolder holder = new QuickHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.color(
                plugin.getConfig().getString("gui.quick-title", "&8Quick Buy")));
        holder.inv = inv;

        Map<Integer, Material> saved = plugin.getListings().quickSlots(player.getUniqueId());
        for (int slot = 0; slot < 45; slot++) {
            Material type = saved.get(slot);
            if (type == null) {
                inv.setItem(slot, emptyQuickSlot());
            } else {
                inv.setItem(slot, quickIcon(type));
                holder.slots.put(slot, type);
            }
        }

        ItemStack pane = item(Material.STAINED_GLASS_PANE, 7, "&7");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(48, item(Material.BOOK, 0, "&6How Quick Buy works",
                "&71. Pick up an item from your",
                "&7   inventory (just click it)",
                "&72. Click a gray pane to save it",
                "&73. From then on, clicking it",
                "&7   buys the cheapest one &finstantly",
                "",
                "&cRight-click &7a saved item to remove it"));
        inv.setItem(49, item(Material.ARROW, 0, "&e◀ Back to Auction House"));

        player.openInventory(inv);
    }

    private ItemStack emptyQuickSlot() {
        return item(Material.STAINED_GLASS_PANE, 7, "&7Empty Quick Buy Slot",
                "&7Pick up an item from your",
                "&7inventory and click here",
                "&7to save it in this slot.");
    }

    private ItemStack quickIcon(Material type) {
        List<Listing> matches = visible(Listings.SORT_CHEAP, null, type);
        ItemStack icon = new ItemStack(type, 1);
        ItemMeta meta = icon.getItemMeta();
        meta.setDisplayName(plugin.color("&e" + prettyName(type)));
        List<String> lore = new ArrayList<String>();
        if (matches.isEmpty()) {
            lore.add(plugin.color("&cNone for sale right now"));
        } else {
            lore.add(plugin.color("&7Cheapest: &a"
                    + plugin.getEconomy().format(matches.get(0).price)));
            lore.add(plugin.color("&7For sale: &f" + matches.size()));
        }
        lore.add("");
        lore.add(plugin.color("&aClick &7= buy the cheapest &finstantly"));
        lore.add(plugin.color("&cRight-Click &7= remove from Quick Buy"));
        meta.setLore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private String sortName(int sort) {
        if (sort == Listings.SORT_CHEAP) return "Cheapest first";
        if (sort == Listings.SORT_PRICEY) return "Priciest first";
        return "Newest first";
    }

    // The item as shown in the auction house (extra info added to its lore)
    private ItemStack displayItem(Listing l) {
        ItemStack show = l.item.clone();
        ItemMeta meta = show.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore())
                                           : new ArrayList<String>();
        lore.add("");
        lore.add(plugin.color("&7Price: &a" + plugin.getEconomy().format(l.price)));
        lore.add(plugin.color("&7Seller: &f" + l.sellerName));
        lore.add(plugin.color("&7Ends in: &f" + timeLeft(l.expires)));
        lore.add("");
        lore.add(plugin.color("&eClick &7to buy"));
        lore.add(plugin.color("&6Shift-Click &7= buy instantly"));
        meta.setLore(lore);
        show.setItemMeta(meta);
        return show;
    }

    // ===== confirm screen =====

    public void openConfirm(Player player, Listing l, AhHolder from) {
        ConfirmHolder holder = new ConfirmHolder();
        holder.listingId = l.id;
        if (from != null) {
            holder.page = from.page;
            holder.sort = from.sort;
            holder.search = from.search;
            holder.filter = from.filter;
        }
        Inventory inv = Bukkit.createInventory(holder, 27, plugin.color(
                plugin.getConfig().getString("gui.confirm-title", "&8Confirm Purchase")));
        holder.inv = inv;

        ItemStack pane = item(Material.STAINED_GLASS_PANE, 7, "&7");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(11, item(Material.WOOL, 5, "&a&lCONFIRM",
                "&7Buy for &a" + plugin.getEconomy().format(l.price)));
        inv.setItem(13, displayItem(l));
        inv.setItem(15, item(Material.WOOL, 14, "&c&lCANCEL", "&7Back to the auction house"));

        player.openInventory(inv);
    }

    // ===== your listings + claim box =====

    public void openMine(Player player) {
        MineHolder holder = new MineHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.color(
                plugin.getConfig().getString("gui.mine-title", "&8Your Listings")));
        holder.inv = inv;

        int slot = 0;
        for (Listing l : plugin.getListings().allFor(player.getUniqueId())) {
            if (slot >= 45) break;
            ItemStack show = l.item.clone();
            ItemMeta meta = show.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore())
                                               : new ArrayList<String>();
            lore.add("");
            lore.add(plugin.color("&7Price: &a" + plugin.getEconomy().format(l.price)));
            lore.add(plugin.color("&7Ends in: &f" + timeLeft(l.expires)));
            lore.add("");
            lore.add(plugin.color("&cClick to take it off sale"));
            meta.setLore(lore);
            show.setItemMeta(meta);
            inv.setItem(slot, show);
            holder.cancelSlots.put(slot, l.id);
            slot++;
        }
        List<ItemStack> claims = plugin.getListings().claimsFor(player.getUniqueId());
        for (int i = 0; i < claims.size() && slot < 45; i++) {
            ItemStack show = claims.get(i).clone();
            ItemMeta meta = show.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore())
                                               : new ArrayList<String>();
            lore.add("");
            lore.add(plugin.color("&6Expired or cancelled"));
            lore.add(plugin.color("&eClick to put it back in your inventory"));
            meta.setLore(lore);
            show.setItemMeta(meta);
            inv.setItem(slot, show);
            holder.claimSlots.put(slot, i);
            slot++;
        }
        if (slot == 0) {
            inv.setItem(22, item(Material.BARRIER, 0, "&cYou have nothing here",
                    "&7Hold an item and type", "&e/ah sell <price>"));
        }

        ItemStack pane = item(Material.STAINED_GLASS_PANE, 7, "&7");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(49, item(Material.ARROW, 0, "&e◀ Back to Auction House"));

        player.openInventory(inv);
    }

    // ===== /sell menu =====

    public void openSell(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.color(
                plugin.getConfig().getString("gui.sell-title", "&8Sell Items")));
        holder.inv = inv;

        ItemStack pane = item(Material.STAINED_GLASS_PANE, 7, "&7");
        for (int i = 45; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(49, item(Material.BOOK, 0, "&6How it works",
                "&7Put items in the empty slots,",
                "&7then press the &agreen pane&7.",
                "",
                "&7Renamed or enchanted items",
                "&7are never sold by accident.",
                "&7Type &e/worth &7while holding an",
                "&7item to see what it pays."));
        inv.setItem(53, sellButton(0));

        player.openInventory(inv);
    }

    // The green pane in the bottom-right corner. Hovering it shows the total.
    private ItemStack sellButton(double total) {
        return item(Material.STAINED_GLASS_PANE, 5, "&a&lSELL EVERYTHING",
                "&7You will get: &a" + plugin.getEconomy().format(total),
                "", "&eClick to confirm and sell");
    }

    /** What one item stack pays in the /sell menu (0 = can't be sold). */
    public double sellValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;
        // never sell special items by accident
        if (item.hasItemMeta() && (item.getItemMeta().hasDisplayName()
                || item.getItemMeta().hasEnchants())) return 0;
        double each = plugin.getConfig().getDouble("sell-prices." + item.getType().name(), 0);
        return each * item.getAmount();
    }

    private double sellTotal(Inventory inv) {
        double total = 0;
        for (int i = 0; i < 45; i++) {
            total += sellValue(inv.getItem(i));
        }
        return total;
    }

    // ===== clicks =====

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof AhHolder) {
            event.setCancelled(true);
            AhHolder ah = (AhHolder) holder;
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;
            if (slot < 45) {
                UUID id = ah.slots.get(slot);
                if (id == null) return;
                Listing l = plugin.getListings().get(id);
                if (l == null) { // someone else bought it first
                    player.sendMessage(plugin.msg("already-sold"));
                    openAh(player, ah.page, ah.sort, ah.search, ah.filter);
                    return;
                }
                if (event.isShiftClick()) {
                    buy(player, id, ah.page, ah.sort, ah.search, ah.filter, false); // instant!
                } else {
                    openConfirm(player, l, ah);
                }
            } else if (slot == 47) { // hopper: sort
                openAh(player, 0, (ah.sort + 1) % 3, ah.search, ah.filter);
            } else if (slot == 48) { // ender chest: quick buy
                openQuick(player);
            } else if (slot == 49) { // anvil: refresh
                openAh(player, ah.page, ah.sort, ah.search, ah.filter);
            } else if (slot == 50) { // sign: search
                if (ah.search != null || ah.filter != null) {
                    openAh(player, 0, ah.sort, null, null); // clear the search
                } else {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("search-hint"));
                }
            } else if (slot == 51) { // chest: your items
                openMine(player);
            } else if (slot == 52 && event.getCurrentItem() != null
                    && event.getCurrentItem().getType() == Material.ARROW) {
                openAh(player, ah.page - 1, ah.sort, ah.search, ah.filter);
            } else if (slot == 53 && event.getCurrentItem() != null
                    && event.getCurrentItem().getType() == Material.ARROW) {
                openAh(player, ah.page + 1, ah.sort, ah.search, ah.filter);
            }
            return;
        }

        if (holder instanceof QuickHolder) {
            QuickHolder quick = (QuickHolder) holder;
            int slot = event.getRawSlot();

            // clicks in the player's own inventory are allowed, so they can
            // pick an item up onto the cursor (but no shift-moving into here)
            if (slot >= 54) {
                if (event.isShiftClick()) event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            if (slot < 0) return;

            if (slot == 49) {
                openAh(player, 0, Listings.SORT_NEWEST);
                return;
            }
            if (slot >= 45) return; // the info book / bottom panes

            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                // holding an item -> save its type in this slot (item is kept!)
                Material type = cursor.getType();
                plugin.getListings().setQuickSlot(player.getUniqueId(), slot, type);
                quick.slots.put(slot, type);
                event.getInventory().setItem(slot, quickIcon(type));
                player.sendMessage(plugin.msg("quick-saved")
                        .replace("%item%", prettyName(type)));
                plugin.sound(player, Sound.NOTE_PLING);
                return;
            }

            Material type = quick.slots.get(slot);
            if (type == null) return; // empty pane, empty hand

            if (event.isRightClick()) {
                // remove the saved item from this slot
                plugin.getListings().setQuickSlot(player.getUniqueId(), slot, null);
                quick.slots.remove(slot);
                event.getInventory().setItem(slot, emptyQuickSlot());
                plugin.sound(player, Sound.CLICK);
                return;
            }

            // buy the cheapest one of this item, instantly
            List<Listing> matches = visible(Listings.SORT_CHEAP, null, type);
            if (matches.isEmpty()) {
                player.sendMessage(plugin.msg("quick-none")
                        .replace("%item%", prettyName(type)));
                plugin.sound(player, Sound.VILLAGER_NO);
                event.getInventory().setItem(slot, quickIcon(type));
                return;
            }
            buy(player, matches.get(0).id, 0, Listings.SORT_NEWEST, null, null, true);
            return;
        }

        if (holder instanceof ConfirmHolder) {
            event.setCancelled(true);
            ConfirmHolder confirm = (ConfirmHolder) holder;
            int slot = event.getRawSlot();
            if (slot == 11) {
                buy(player, confirm.listingId, confirm.page, confirm.sort,
                        confirm.search, confirm.filter, false);
            } else if (slot == 15) {
                openAh(player, confirm.page, confirm.sort, confirm.search, confirm.filter);
            }
            return;
        }

        if (holder instanceof MineHolder) {
            event.setCancelled(true);
            MineHolder mine = (MineHolder) holder;
            int slot = event.getRawSlot();
            if (slot == 49) {
                openAh(player, 0, Listings.SORT_NEWEST);
                return;
            }
            if (mine.cancelSlots.containsKey(slot)) {
                UUID id = mine.cancelSlots.get(slot);
                Listing l = plugin.getListings().get(id);
                if (l != null) {
                    plugin.getListings().remove(id);
                    plugin.getListings().addClaim(player.getUniqueId(), l.item);
                    plugin.getListings().save();
                    player.sendMessage(plugin.msg("cancelled")
                            .replace("%item%", plugin.itemName(l.item)));
                }
                openMine(player);
                return;
            }
            if (mine.claimSlots.containsKey(slot)) {
                int index = mine.claimSlots.get(slot);
                ItemStack item = plugin.getListings().takeClaim(player.getUniqueId(), index);
                if (item != null) {
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    for (ItemStack rest : leftover.values()) {
                        // inventory full: keep it safe in the claim box
                        plugin.getListings().addClaim(player.getUniqueId(), rest);
                        player.sendMessage(plugin.msg("inventory-full"));
                    }
                    plugin.getListings().save();
                    plugin.sound(player, Sound.ITEM_PICKUP);
                }
                openMine(player);
            }
            return;
        }

        if (holder instanceof SellHolder) {
            int slot = event.getRawSlot();
            if (slot >= 45 && slot < 54) {
                event.setCancelled(true);
                if (slot == 53) {
                    sellContents(player, event.getInventory());
                }
                return;
            }
            // placing/taking items in the top grid or own inventory is fine —
            // just refresh the total on the green pane a moment later
            final Inventory inv = event.getInventory();
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                public void run() {
                    inv.setItem(53, sellButton(sellTotal(inv)));
                }
            });
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        boolean ours = holder instanceof AhHolder || holder instanceof ConfirmHolder
                || holder instanceof MineHolder || holder instanceof QuickHolder;
        boolean touchesTop = false;
        for (int raw : event.getRawSlots()) {
            if (raw < event.getInventory().getSize()) touchesTop = true;
        }
        if (ours && touchesTop) {
            event.setCancelled(true);
            return;
        }
        if (holder instanceof SellHolder) {
            for (int raw : event.getRawSlots()) {
                if (raw >= 45 && raw < 54) { // never onto the buttons
                    event.setCancelled(true);
                    return;
                }
            }
            final Inventory inv = event.getInventory();
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                public void run() {
                    inv.setItem(53, sellButton(sellTotal(inv)));
                }
            });
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        // give back anything still in the sell menu
        for (int i = 0; i < 45; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            event.getInventory().setItem(i, null);
        }
    }

    private void sellContents(Player player, Inventory inv) {
        double total = 0;
        int stacks = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            double value = sellValue(item);
            if (value > 0) {
                total += value;
                stacks++;
                inv.setItem(i, null);
            }
        }
        if (total <= 0) {
            player.sendMessage(plugin.msg("nothing-sellable"));
            plugin.sound(player, Sound.VILLAGER_NO);
            return;
        }
        plugin.getEconomy().add(player, total);
        player.sendMessage(plugin.msg("sold")
                .replace("%amount%", plugin.getEconomy().format(total))
                .replace("%stacks%", String.valueOf(stacks)));
        plugin.sound(player, Sound.LEVEL_UP);
        player.closeInventory(); // returns whatever could not be sold
    }

    /** The actual purchase — used by instant buy, Quick Buy, and confirm. */
    private void buy(Player buyer, UUID listingId, int page, int sort,
                     String search, Material filter, boolean fromQuick) {
        Listing l = plugin.getListings().get(listingId);
        if (l == null) {
            buyer.sendMessage(plugin.msg("already-sold"));
            if (fromQuick) openQuick(buyer);
            else openAh(buyer, page, sort, search, filter);
            return;
        }
        if (l.seller.equals(buyer.getUniqueId())) {
            buyer.sendMessage(plugin.msg("own-listing"));
            return;
        }
        if (plugin.getEconomy().get(buyer) < l.price) {
            buyer.sendMessage(plugin.msg("not-enough"));
            plugin.sound(buyer, Sound.VILLAGER_NO);
            return;
        }
        plugin.getEconomy().take(buyer, l.price);
        plugin.getEconomy().add(Bukkit.getOfflinePlayer(l.seller), l.price);
        plugin.getListings().remove(listingId);
        plugin.getListings().save();

        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(l.item);
        for (ItemStack rest : leftover.values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), rest);
        }
        buyer.sendMessage(plugin.msg("bought")
                .replace("%item%", plugin.itemName(l.item))
                .replace("%amount%", plugin.getEconomy().format(l.price)));
        plugin.sound(buyer, Sound.LEVEL_UP);

        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) {
            seller.sendMessage(plugin.msg("your-item-sold")
                    .replace("%item%", plugin.itemName(l.item))
                    .replace("%amount%", plugin.getEconomy().format(l.price))
                    .replace("%player%", buyer.getName()));
            plugin.sound(seller, Sound.ORB_PICKUP);
        }
        // back to where they were: the Quick Buy grid, or the same AH view
        if (fromQuick) openQuick(buyer);
        else openAh(buyer, page, sort, search, filter);
    }

    // ===== little helpers =====

    private ItemStack item(Material material, int data, String name, String... lore) {
        ItemStack stack = new ItemStack(material, 1, (short) data);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(plugin.color(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<String>();
            for (String line : lore) lines.add(plugin.color(line));
            meta.setLore(lines);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private String timeLeft(long expires) {
        long ms = expires - System.currentTimeMillis();
        if (ms <= 0) return "expired";
        long minutes = ms / 60000;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    private String trim(String s) {
        return s.length() <= 32 ? s : s.substring(0, 32);
    }
}
