package codes.biscuit.sellchest.utils;

import codes.biscuit.sellchest.SellChest;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Pattern;

public class Utils {

    private SellChest main;
    private int sellTimerID;
    private int saveTimerID;
    private int messageTimerID;
    private Map<Location, UUID> chestLocations = new HashMap<>();
    private Set<OfflinePlayer> bypassPlayers = new HashSet<>();

    public Utils(SellChest main) {
        this.main = main;
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void sendMessage(CommandSender p, ConfigValues.Message message, Object... variables) {
        if (!main.getConfigValues().getMessage(message, variables).equals("")) {
            p.sendMessage(main.getConfigValues().getMessage(message, variables));
        }
    }

    public boolean isVoidChest(ItemStack item) {
        return (item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().equals(main.getConfigValues().getChestName()) && item.getItemMeta().hasLore() && item.getItemMeta().getLore().equals(main.getConfigValues().getChestLore()));
    }

    public ItemStack getSellChestItemStack(int amount) {
        ItemStack sellChest = main.getConfigValues().getItem();
        sellChest.setAmount(amount);
        ItemMeta sellChestMeta = sellChest.getItemMeta();
        sellChestMeta.setDisplayName(main.getConfigValues().getChestName());
        sellChestMeta.setLore(main.getConfigValues().getChestLore());
        sellChest.setItemMeta(sellChestMeta);
        if (main.getConfigValues().chestShouldGlow()) addGlow(sellChest);
        return sellChest;
    }

    public void runSellTimer() {
        sellTimerID = Bukkit.getScheduler().scheduleSyncRepeatingTask(main, () -> {
            Iterator<Map.Entry<Location, UUID>> entryIterator = chestLocations.entrySet().iterator();
            Set<UUID> offlineUuids = new HashSet<>();
            int numSold = 0;
            int total = chestLocations.entrySet().size();
            System.out.println("Sellchest selling");
            System.out.println("Sellchest : There are " + total + " chests to check");
            while (entryIterator.hasNext()) {
                Map.Entry<Location, UUID> locationEntry = entryIterator.next();
                Player player = offlineUuids.contains(locationEntry.getValue()) ? null : main.getServer().getPlayer(locationEntry.getValue());
                if (player == null) {
                    offlineUuids.add(locationEntry.getValue());
                    continue;
                }
                numSold++;
                Location loc = locationEntry.getKey();
                if (loc == null) {
                    entryIterator.remove();
                    continue;
                }
                Block block = loc.getBlock();
                if (block == null) {
                    continue;
                }
                
                if (block.getType() == Material.CHEST) {
                    Chest voidChest = (Chest)block.getState();
                    Inventory voidChestInventory = voidChest.getInventory();
                    for (ItemStack item : voidChestInventory) {
                        if (item != null) {
                            double sellPrice;
                            if (offlineP.isOnline()) {
                                Player p = offlineP.getPlayer();
                                sellPrice = main.getHookUtils().getValue(item, p);
                            } else {
                                sellPrice = main.getHookUtils().getValue(item, null);
                            }
                            if (sellPrice > 0) {
                                main.getHookUtils().giveMoney(offlineP, sellPrice, loc);
                                voidChestInventory.setItem(voidChestInventory.first(item), new ItemStack(Material.AIR));
                                block.getState().update(); // If you don't update the chest after removing, double chests will sell double
                            }
                        }
                    }
                    if (main.getConfigValues().removeUnsellableItems()) {
                        voidChest.getBlockInventory().clear();
                    }
                } else {
                    entryIterator.remove();
                }
            }
            System.out.println("Sellchest : Finished checking. " + numSold + " chests have been checked. Others have been skipped.");
        }, main.getConfigValues().getSellInterval(), main.getConfigValues().getSellInterval());
    }

    public int getSellTimerID() {
        return sellTimerID;
    }

    public void runSaveTimer() {
        saveTimerID = Bukkit.getScheduler().scheduleSyncRepeatingTask(main, this::saveChestLocations, main.getConfigValues().getSaveInterval(), main.getConfigValues().getSaveInterval());
    }

    public void saveChestLocations() {
        try {
            for (Map.Entry<Location, UUID> entry : chestLocations.entrySet()) {
                Location loc = entry.getKey();
                UUID uuid = entry.getValue();
                String serializedLoc = loc.getWorld().getName() + "|" + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
                String uuidString = uuid.toString();
                List<String> locationList = main.getConfigValues().getLocationsConfig().getStringList("locations." + uuidString);
                locationList.add(serializedLoc);
                main.getConfigValues().getLocationsConfig().set("locations." + uuidString, locationList);
            }
            main.getConfigValues().getLocationsConfig().save(main.getConfigValues().getLocationsFile());
            main.getConfigValues().getLocationsConfig().set("locations", null);
        } catch (IOException ex) {
            ex.printStackTrace();
            main.getLogger().info("Unable to save sellchest locations!");
        }
    }

    public int getMessageTimerID() {
        return messageTimerID;
    }

    public void runMessageTimer() {
        if (main.getConfigValues().getRecentEarningsInterval() > 0) {
            messageTimerID = Bukkit.getScheduler().scheduleSyncRepeatingTask(main, () -> {
                for (Map.Entry<OfflinePlayer, Double> entry : main.getHookUtils().getMoneyHistory().entrySet()) {
                    if (entry.getKey().isOnline()) {
                        Player p = entry.getKey().getPlayer();
                        main.getUtils().sendMessage(p, ConfigValues.Message.RECENTLY_EARNED, round(entry.getValue()));
                    }
                }
                main.getHookUtils().getMoneyHistory().clear();
            }, main.getConfigValues().getRecentEarningsInterval(), main.getConfigValues().getRecentEarningsInterval());
        }
    }

    public int getSaveTimerID() {
        return saveTimerID;
    }

    private void addGlow(ItemStack item) {
        item.addUnsafeEnchantment(Enchantment.LUCK, 1);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    public Map<Location, UUID> getChestLocations() {
        return this.chestLocations;
    }

    public BlockFace getOppositeDirection(Player player) {
        double rotation = (player.getLocation().getYaw() - 90) % 360;
        if (rotation < 0) {
            rotation += 360.0;
        }
        if (rotation >= 45 && rotation < 135) {
            return BlockFace.SOUTH;
        } else if (rotation >= 135 && rotation < 225) {
            return BlockFace.WEST;
        } else if (rotation >= 225 && rotation < 315) {
            return BlockFace.NORTH;
        } else if (rotation >= 315 || rotation < 45) {
            return BlockFace.EAST;
        } else {
            return null;
        }
    }

    public Set<OfflinePlayer> getBypassPlayers() {
        return bypassPlayers;
    }

    public void updateConfig(SellChest main) {
        if (main.getConfigValues().getConfigVersion() < 1.4) {
            Map<String, Object> oldValues = new HashMap<>();
            for (String oldKey : main.getConfig().getKeys(true)) {
                oldValues.put(oldKey, main.getConfig().get(oldKey));
            }
            main.saveResource("config.yml", true);
            main.reloadConfig();
            for (String newKey : main.getConfig().getKeys(true)) {
                if (oldValues.containsKey(newKey) && !oldValues.get(newKey).equals(main.getConfig().get(newKey))) {
                    main.getConfig().set(newKey, oldValues.get(newKey));
                }
            }
            main.getConfig().set("config-version", 1.4);
            main.saveConfig();
        }
    }

    public void checkUpdates(Player p) {
        try {
            URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=65352");
            URLConnection connection = url.openConnection();
            connection.setReadTimeout(5000);
            connection.addRequestProperty("User-Agent", "SellChest update checker");
            connection.setDoOutput(true);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String newestVersion = reader.readLine();
            reader.close();
            List<Integer> newestVersionNumbers = new ArrayList<>();
            List<Integer> thisVersionNumbers = new ArrayList<>();
            try {
                for (String s : newestVersion.split(Pattern.quote("."))) {
                    newestVersionNumbers.add(Integer.parseInt(s));
                }
                for (String s : main.getDescription().getVersion().split(Pattern.quote("."))) {
                    thisVersionNumbers.add(Integer.parseInt(s));
                }
            } catch (Exception ex) {
                return;
            }
            for (int i = 0; i < 3; i++) {
                if (newestVersionNumbers.get(i) != null && thisVersionNumbers.get(i) != null) {
                    if (newestVersionNumbers.get(i) > thisVersionNumbers.get(i)) {
                        TextComponent newVersion = new TextComponent("A new version of SellChest, " + newestVersion + " is available. Download it by clicking here.");
                        newVersion.setColor(ChatColor.RED);
                        newVersion.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/sellchest-1-8-1-13-automatically-sell-items-in-chests-voidchests.65352/"));
                        p.spigot().sendMessage(newVersion);
                        break;
                    } else if (thisVersionNumbers.get(i) > newestVersionNumbers.get(i)) {
                        p.sendMessage(ChatColor.RED + "You are running a development version of SellChest, " + main.getDescription().getVersion() + ". The latest online version is " + newestVersion + ".");
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    List<String> colorLore(List<String> lore) {
        for (int i = 0; i < lore.size(); i++) {
            lore.set(i, color(lore.get(i)));
        }
        return lore; // For convenience
    }

    ItemStack itemFromString(String rawItem) {
        Material material;
        String[] rawSplit;
        if (rawItem.contains(":")) {
            rawSplit = rawItem.split(":");
        } else {
            rawSplit = new String[] {rawItem};
        }
        try {
            material = Material.valueOf(rawSplit[0].toUpperCase());
        } catch (IllegalArgumentException ex) {
            material = Material.DIRT;
        }
        short damage = 1;
        if (rawSplit.length > 1) {
            try {
                damage = Short.valueOf(rawSplit[1]);
            } catch (IllegalArgumentException ignored) {}
        }
        return new ItemStack(material, 1, damage);
    }

    public boolean reachedLimit(Player p) {
        Map<String, Integer> limits = main.getConfigValues().getChestLimits();
        int maxChests = limits.get("default");
        if (maxChests == 0) {
            return false;
        }
        for (Map.Entry<String, Integer> limit : limits.entrySet()) {
            if (p.hasPermission("sellchest.limit."+limit.getKey())) {
                if (limit.getValue() == 0) {
                    return false;
                }
                if (limit.getValue() > maxChests) {
                    maxChests = limit.getValue();
                }
            }
        }
        int chestCount = 0;
        for (UUID uuid : chestLocations.values()) {
            if (uuid.equals(p.getUniqueId())) {
                chestCount++;
            }
        }
        if (chestCount >= maxChests) {
            main.getUtils().sendMessage(p, ConfigValues.Message.REACHED_LIMIT, maxChests);
            return true;
        }
        return false;
    }

    private double round(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
