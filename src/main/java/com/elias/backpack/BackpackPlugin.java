package com.elias.backpack;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BackpackPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "Dein Rucksack";
    private static final String CHEST_HEAD_URL = "http://textures.minecraft.net/texture/d5c6dc2bbf51c36cfc7714585a6a5683ef2b14d47d8ff714654a893f5da622";

    private NamespacedKey backpackItemKey;
    private NamespacedKey placedChestKey;
    private NamespacedKey backpackOwnerKey;

    private File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, Inventory> openBackpacks = new HashMap<>();

    @Override
    public void onEnable() {
        backpackItemKey = new NamespacedKey(this, "is_backpack_item");
        placedChestKey = new NamespacedKey(this, "is_backpack_chest");
        backpackOwnerKey = new NamespacedKey(this, "backpack_owner");

        loadDataConfig();

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("backpack") != null) {
            getCommand("backpack").setExecutor(this);
            getCommand("backpack").setTabCompleter(this);
        }
        if (getCommand("bpitem") != null) {
            getCommand("bpitem").setExecutor(this);
            getCommand("bpitem").setTabCompleter(this);
        }

        getLogger().info("BackpackPlugin v1.0.2 erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        for (Map.Entry<UUID, Inventory> entry : openBackpacks.entrySet()) {
            saveBackpack(entry.getKey(), entry.getValue());
        }
        openBackpacks.clear();
    }

    private void loadDataConfig() {
        dataFile = new File(getDataFolder(), "backpacks.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Konnte backpacks.yml nicht erstellen!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveDataConfig() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Konnte backpacks.yml nicht speichern!");
        }
    }

    // ===== BACKPACK ITEM CREATION =====

    public ItemStack createBackpackItem(UUID ownerUuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(new URL(CHEST_HEAD_URL));
            profile.setTextures(textures);
            meta.setPlayerProfile(profile);
        } catch (MalformedURLException e) {
            getLogger().warning("Ungültige Head-Texture URL!");
        }

        meta.setDisplayName(ChatColor.GOLD + "Rucksack " + ChatColor.GRAY + "(Quick Access)");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Klick / Q im Inv: " + ChatColor.YELLOW + "Rucksack öffnen");
        lore.add(ChatColor.GRAY + "Rechtsklick: " + ChatColor.YELLOW + "Rucksack öffnen");
        lore.add(ChatColor.GRAY + "Shift + Rechtsklick: " + ChatColor.YELLOW + "Als Kiste platzieren");
        lore.add(ChatColor.GRAY + "Linksklick auf Kiste: " + ChatColor.YELLOW + "Sofort abbauen");
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackItemKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackOwnerKey, PersistentDataType.STRING, ownerUuid.toString());

        head.setItemMeta(meta);
        return head;
    }

    public boolean isBackpackItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(backpackItemKey, PersistentDataType.BYTE);
    }

    public String getBackpackOwner(ItemStack item) {
        if (!isBackpackItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(backpackOwnerKey, PersistentDataType.STRING);
    }

    public boolean hasBackpackInInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isBackpackItem(item)) return true;
        }
        return false;
    }

    private void removeAllBackpackItems(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (isBackpackItem(item)) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    // Entfernt platziertes Rucksack-Kisten-Block in allen Welten
    private void destroyPlacedBackpackBlocks(UUID ownerUuid) {
        String targetUuid = ownerUuid.toString();

        if (dataConfig.contains("placed_chests." + targetUuid)) {
            String worldName = dataConfig.getString("placed_chests." + targetUuid + ".world");
            int x = dataConfig.getInt("placed_chests." + targetUuid + ".x");
            int y = dataConfig.getInt("placed_chests." + targetUuid + ".y");
            int z = dataConfig.getInt("placed_chests." + targetUuid + ".z");

            if (worldName != null) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.CHEST && block.getState() instanceof Chest) {
                        Chest chest = (Chest) block.getState();
                        chest.getBlockInventory().clear();
                        block.setType(Material.AIR);
                    }
                }
            }
            dataConfig.set("placed_chests." + targetUuid, null);
            saveDataConfig();
        }
    }

    // ===== BACKPACK DATA HANDLING =====

    public Inventory getBackpackInventory(UUID uuid) {
        if (openBackpacks.containsKey(uuid)) {
            return openBackpacks.get(uuid);
        }

        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);
        String path = "backpacks." + uuid.toString();

        if (dataConfig.contains(path)) {
            List<?> list = dataConfig.getList(path);
            if (list != null) {
                for (int i = 0; i < Math.min(list.size(), 27); i++) {
                    Object obj = list.get(i);
                    if (obj instanceof ItemStack) {
                        inv.setItem(i, (ItemStack) obj);
                    }
                }
            }
        }

        openBackpacks.put(uuid, inv);
        return inv;
    }

    public void saveBackpack(UUID uuid, Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            items.add(inv.getItem(i));
        }
        dataConfig.set("backpacks." + uuid.toString(), items);
        saveDataConfig();
    }

    // ===== BEFEHLE =====

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl ist nur für Spieler.");
            return true;
        }

        Player p = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("backpack") || cmd.equals("bp")) {
            boolean isHidden = dataConfig.getBoolean("hidden." + p.getUniqueId(), false);

            if (!hasBackpackInInventory(p) && !isHidden) {
                p.sendMessage(ChatColor.RED + "Du hast deinen Rucksack weder im Inventar noch versteckt! (/bpitem show)");
                return true;
            }

            Inventory inv = getBackpackInventory(p.getUniqueId());
            p.openInventory(inv);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
            return true;
        }

        if (cmd.equals("bpitem")) {
            if (args.length == 0) {
                p.sendMessage(ChatColor.RED + "Benutzung: /bpitem <show|hide|recover>");
                return true;
            }

            String action = args[0].toLowerCase();

            if (action.equals("hide")) {
                if (!hasBackpackInInventory(p)) {
                    p.sendMessage(ChatColor.RED + "Du musst das Rucksack-Item in deinem Inventar haben, um es zu verstecken!");
                    return true;
                }

                removeAllBackpackItems(p);

                dataConfig.set("hidden." + p.getUniqueId(), true);
                saveDataConfig();

                p.sendMessage(ChatColor.GREEN + "Rucksack-Item versteckt. Du kannst /bp oder /backpack weiterhin nutzen.");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
                return true;
            }

            if (action.equals("show")) {
                boolean isHidden = dataConfig.getBoolean("hidden." + p.getUniqueId(), false);

                if (!isHidden) {
                    if (hasBackpackInInventory(p)) {
                        p.sendMessage(ChatColor.RED + "Dein Rucksack-Item ist bereits in deinem Inventar!");
                    } else {
                        p.sendMessage(ChatColor.RED + "Dein Rucksack ist derzeit nicht versteckt. Falls er verloren ging, nutze /bpitem recover!");
                    }
                    return true;
                }

                if (p.getInventory().firstEmpty() == -1) {
                    p.sendMessage(ChatColor.YELLOW + "Dein Inventar ist voll! Der Rucksack bleibt im Hidden-Modus. Nutze /bp zum Öffnen.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return true;
                }

                dataConfig.set("hidden." + p.getUniqueId(), false);
                saveDataConfig();

                p.getInventory().addItem(createBackpackItem(p.getUniqueId()));
                p.sendMessage(ChatColor.GREEN + "Rucksack-Item wieder in dein Inventar gelegt!");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                return true;
            }

            if (action.equals("recover")) {
                // 1. Zerstöre alle platzierten Rucksack-Kisten in der Welt
                destroyPlacedBackpackBlocks(p.getUniqueId());

                // 2. Entferne Rucksack-Items aus dem Inventar des Spielers
                removeAllBackpackItems(p);

                // 3. Wenn Inventar voll -> Verstecken & Hinweis geben
                if (p.getInventory().firstEmpty() == -1) {
                    dataConfig.set("hidden." + p.getUniqueId(), true);
                    saveDataConfig();

                    p.sendMessage(ChatColor.YELLOW + "Dein Inventar ist voll! Der Rucksack wurde im Hintergrund wiederhergestellt & versteckt.");
                    p.sendMessage(ChatColor.YELLOW + "Nutze /bp zum Öffnen oder /bpitem show, sobald du wieder Platz im Inventar hast.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
                    return true;
                }

                // 4. Normal ins Inventar geben
                dataConfig.set("hidden." + p.getUniqueId(), false);
                saveDataConfig();

                p.getInventory().addItem(createBackpackItem(p.getUniqueId()));
                p.sendMessage(ChatColor.GREEN + "Dein Rucksack wurde wiederhergestellt und platziere Kisten gelöscht!");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                return true;
            }

            p.sendMessage(ChatColor.RED + "Benutzung: /bpitem <show|hide|recover>");
            return true;
        }

        return true;
    }

    // ===== EVENTS =====

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();

        // 1. Quick-Access im Inventar: Linksklick, Rechtsklick oder 'Q' (Drop) öffnet Rucksack
        if (isBackpackItem(current)) {
            ClickType click = event.getClick();
            if (click == ClickType.LEFT || click == ClickType.RIGHT || click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                event.setCancelled(true);
                p.closeInventory();
                Inventory inv = getBackpackInventory(p.getUniqueId());
                p.openInventory(inv);
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                return;
            }
        }

        // 2. Blockiert das Ausrüsten des Kopfes auf den Helm-Slot
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && event.getRawSlot() == 5) {
            ItemStack cursor = event.getCursor();
            if (isBackpackItem(cursor)) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Du kannst den Rucksack nicht auf dem Kopf tragen!");
            }
        }

        // 3. Blockiert Shift-Klick ins Rüstungsinventar
        if (event.isShiftClick() && isBackpackItem(current)) {
            if (event.getInventory().getType() == InventoryType.CRAFTING || event.getInventory().getType() == InventoryType.PLAYER) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        // Instant-Break per Linksklick auf eine platzierte Rucksack-Kiste
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.CHEST && block.getState() instanceof Chest) {
                Chest chest = (Chest) block.getState();
                PersistentDataContainer pdc = chest.getPersistentDataContainer();

                if (pdc.has(placedChestKey, PersistentDataType.STRING)) {
                    String ownerUuidStr = pdc.get(placedChestKey, PersistentDataType.STRING);

                    if (ownerUuidStr != null && ownerUuidStr.equals(p.getUniqueId().toString())) {
                        event.setCancelled(true);
                        breakBackpackChest(block, chest, p);
                        return;
                    } else {
                        event.setCancelled(true);
                        p.sendMessage(ChatColor.RED + "Du kannst den Rucksack anderer Spieler nicht abbauen!");
                        return;
                    }
                }
            }
        }

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isBackpackItem(item)) return;

        // Normaler Rechtsklick -> GUI öffnen
        if (event.getAction() == Action.RIGHT_CLICK_AIR || (event.getAction() == Action.RIGHT_CLICK_BLOCK && !p.isSneaking())) {
            event.setCancelled(true);
            Inventory inv = getBackpackInventory(p.getUniqueId());
            p.openInventory(inv);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (!isBackpackItem(item)) return;

        if (!p.isSneaking()) {
            event.setCancelled(true);
            return;
        }

        Block block = event.getBlockPlaced();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();

            PersistentDataContainer pdc = chest.getPersistentDataContainer();
            pdc.set(placedChestKey, PersistentDataType.STRING, p.getUniqueId().toString());
            chest.update();

            // Speichere die Position der platzierten Kiste in backpacks.yml für /bpitem recover
            String targetUuid = p.getUniqueId().toString();
            dataConfig.set("placed_chests." + targetUuid + ".world", block.getWorld().getName());
            dataConfig.set("placed_chests." + targetUuid + ".x", block.getX());
            dataConfig.set("placed_chests." + targetUuid + ".y", block.getY());
            dataConfig.set("placed_chests." + targetUuid + ".z", block.getZ());
            saveDataConfig();

            Inventory backpackInv = getBackpackInventory(p.getUniqueId());
            Inventory chestInv = chest.getBlockInventory();

            for (int i = 0; i < Math.min(backpackInv.getSize(), chestInv.getSize()); i++) {
                chestInv.setItem(i, backpackInv.getItem(i));
            }

            p.sendMessage(ChatColor.GREEN + "Rucksack als Kiste platziert.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;

        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            PersistentDataContainer pdc = chest.getPersistentDataContainer();

            if (pdc.has(placedChestKey, PersistentDataType.STRING)) {
                String ownerUuidStr = pdc.get(placedChestKey, PersistentDataType.STRING);
                Player p = event.getPlayer();

                if (ownerUuidStr != null && ownerUuidStr.equals(p.getUniqueId().toString())) {
                    event.setCancelled(true);
                    breakBackpackChest(block, chest, p);
                } else {
                    event.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "Du kannst den Rucksack anderer Spieler nicht abbauen!");
                }
            }
        }
    }

    private void breakBackpackChest(Block block, Chest chest, Player p) {
        UUID ownerUuid = p.getUniqueId();
        Inventory backpackInv = getBackpackInventory(ownerUuid);
        Inventory chestInv = chest.getBlockInventory();

        for (int i = 0; i < Math.min(chestInv.getSize(), backpackInv.getSize()); i++) {
            backpackInv.setItem(i, chestInv.getItem(i));
        }
        saveBackpack(ownerUuid, backpackInv);

        chestInv.clear();
        block.setType(Material.AIR);

        dataConfig.set("placed_chests." + ownerUuid.toString(), null);
        saveDataConfig();

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(dropLoc, createBackpackItem(ownerUuid));

        p.playSound(dropLoc, Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            Player p = (Player) event.getPlayer();
            saveBackpack(p.getUniqueId(), event.getInventory());
            openBackpacks.remove(p.getUniqueId());
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 0.8f);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (openBackpacks.containsKey(uuid)) {
            saveBackpack(uuid, openBackpacks.get(uuid));
            openBackpacks.remove(uuid);
        }
    }

    // ===== TAB COMPLETER =====

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("bpitem") && args.length == 1) {
            list.add("show");
            list.add("hide");
            list.add("recover");
        }
        return list;
    }
}
