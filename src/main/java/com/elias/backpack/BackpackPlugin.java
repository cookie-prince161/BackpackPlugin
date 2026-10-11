package com.elias.backpack;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
import org.bukkit.profile.PlayerProfile;
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
    private static final String CHEST_HEAD_URL = "http://textures.minecraft.net/texture/68682935de3869213e2f49213a0a6534571d98522e8312e0325d7629532824";

    private NamespacedKey backpackItemKey;
    private NamespacedKey placedChestKey;

    private File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, Inventory> openBackpacks = new HashMap<>();

    @Override
    public void onEnable() {
        backpackItemKey = new NamespacedKey(this, "is_backpack_item");
        placedChestKey = new NamespacedKey(this, "is_backpack_chest");

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

        getLogger().info("BackpackPlugin v1.0.0 erfolgreich aktiviert!");
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

    public ItemStack createBackpackItem() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
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
        lore.add(ChatColor.GRAY + "Rechtsklick: " + ChatColor.YELLOW + "Rucksack öffnen");
        lore.add(ChatColor.GRAY + "Shift + Rechtsklick: " + ChatColor.YELLOW + "Als Kiste platzieren");
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackItemKey, PersistentDataType.BYTE, (byte) 1);

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

    public boolean hasBackpackInInventory(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isBackpackItem(item)) return true;
        }
        return false;
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

        if (cmd.equals("backpack")) {
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

                p.getInventory().remove(createBackpackItem());
                for (int i = 0; i < p.getInventory().getSize(); i++) {
                    ItemStack item = p.getInventory().getItem(i);
                    if (isBackpackItem(item)) {
                        p.getInventory().setItem(i, null);
                    }
                }

                dataConfig.set("hidden." + p.getUniqueId(), true);
                saveDataConfig();

                p.sendMessage(ChatColor.GREEN + "Rucksack-Item versteckt. Du kannst /backpack weiterhin nutzen.");
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
                    p.sendMessage(ChatColor.RED + "Dein Inventar ist voll!");
                    return true;
                }

                dataConfig.set("hidden." + p.getUniqueId(), false);
                saveDataConfig();

                p.getInventory().addItem(createBackpackItem());
                p.sendMessage(ChatColor.GREEN + "Rucksack-Item wieder in dein Inventar gelegt!");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                return true;
            }

            if (action.equals("recover")) {
                if (hasBackpackInInventory(p)) {
                    p.sendMessage(ChatColor.RED + "Du hast das Rucksack-Item bereits in deinem Inventar!");
                    return true;
                }

                boolean isHidden = dataConfig.getBoolean("hidden." + p.getUniqueId(), false);
                if (isHidden) {
                    dataConfig.set("hidden." + p.getUniqueId(), false);
                    saveDataConfig();
                }

                if (p.getInventory().firstEmpty() == -1) {
                    p.sendMessage(ChatColor.RED + "Dein Inventar ist voll!");
                    return true;
                }

                p.getInventory().addItem(createBackpackItem());
                p.sendMessage(ChatColor.GREEN + "Dein Rucksack-Item wurde wiederhergestellt! Der Inhalt bleibt erhalten.");
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isBackpackItem(item)) return;

        // Normaler Rechtsklick (Klick in die Luft oder ohne Shift auf Block) -> GUI öffnen & Platzieren verhindern
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

        // Nur platzieren erlauben, wenn geduckt (Shift) wird
        if (!p.isSneaking()) {
            event.setCancelled(true);
            return;
        }

        // Kiste als Spezial-Kiste taggen & Backpack-Inhalt übertragen
        Block block = event.getBlockPlaced();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();

            // Tag im Block setzen
            PersistentDataContainer pdc = chest.getPersistentDataContainer();
            pdc.set(placedChestKey, PersistentDataType.STRING, p.getUniqueId().toString());
            chest.update();

            // Inhalt kopieren
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
                if (ownerUuidStr == null) return;

                UUID ownerUuid = UUID.fromString(ownerUuidStr);
                Inventory backpackInv = getBackpackInventory(ownerUuid);
                Inventory chestInv = chest.getBlockInventory();

                // Aktuellen Kisteninhalt im Backpack abspeichern
                for (int i = 0; i < Math.min(chestInv.getSize(), backpackInv.getSize()); i++) {
                    backpackInv.setItem(i, chestInv.getItem(i));
                }
                saveBackpack(ownerUuid, backpackInv);

                // Kisteninhalt leeren, damit normale Items nicht gedroppt werden
                chestInv.clear();

                // Block manuell ohne Drops entfernen
                event.setCancelled(true);
                block.setType(Material.AIR);

                // Stattdessen den Rucksack-Kopf am Ort des Blocks droppen
                Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
                block.getWorld().dropItemNaturally(dropLoc, createBackpackItem());

                Player p = event.getPlayer();
                p.playSound(dropLoc, Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
            }
        }
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
