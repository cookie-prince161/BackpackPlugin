package com.elias.backpack;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
    private static final String ADMIN_BP_PREFIX = ChatColor.DARK_RED + "Rucksack: ";
    private static final String ADMIN_INV_PREFIX = ChatColor.DARK_RED + "Inventar: ";
    private static final String SHOP_TITLE = ChatColor.DARK_BLUE + "Rucksack Upgrades";
    private static final String CHEST_HEAD_URL = "http://textures.minecraft.net/texture/d5c6dc2bbf51c36cfc7714585a6a5683ef2b14d47d8ff714654a893f5da622";

    private NamespacedKey backpackItemKey;
    private NamespacedKey placedChestKey;
    private NamespacedKey backpackOwnerKey;

    private File dataFile;
    private FileConfiguration dataConfig;

    private final Map<UUID, Inventory> openBackpacks = new HashMap<>();
    private final Map<UUID, Long> recoverCooldowns = new HashMap<>();
    private final List<UUID> pendingConfirmations = new ArrayList<>();

    @Override
    public void onEnable() {
        backpackItemKey = new NamespacedKey(this, "is_backpack_item");
        placedChestKey = new NamespacedKey(this, "is_backpack_chest");
        backpackOwnerKey = new NamespacedKey(this, "backpack_owner");

        saveDefaultConfig();
        loadDataConfig();

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("backpack") != null) {
            getCommand("backpack").setExecutor(this);
            getCommand("backpack").setTabCompleter(this);
        }
        if (getCommand("invsee") != null) {
            getCommand("invsee").setExecutor(this);
            getCommand("invsee").setTabCompleter(this);
        }
        if (getCommand("enderchest") != null) {
            getCommand("enderchest").setExecutor(this);
            getCommand("enderchest").setTabCompleter(this);
        }
        if (getCommand("bpadmin") != null) {
            getCommand("bpadmin").setExecutor(this);
            getCommand("bpadmin").setTabCompleter(this);
        }

        getLogger().info("BackpackPlugin v1.2.3 erfolgreich aktiviert!");
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

    // ===== UPGRADES & DATA HELPERS =====

    public int getUpgradeLevel(UUID uuid) {
        return dataConfig.getInt("upgrades." + uuid.toString(), 0);
    }

    public void setUpgradeLevel(UUID uuid, int level) {
        dataConfig.set("upgrades." + uuid.toString(), level);
        saveDataConfig();
    }

    public boolean isUpgradeDisabled(UUID uuid, int upgrade) {
        return dataConfig.getBoolean("disabled_upgrades." + uuid.toString() + "." + upgrade, false);
    }

    public void toggleUpgradeDisabled(UUID uuid, int upgrade) {
        boolean curr = isUpgradeDisabled(uuid, upgrade);
        dataConfig.set("disabled_upgrades." + uuid.toString() + "." + upgrade, !curr);
        saveDataConfig();
    }

    public int getEffectiveUpgradeLevel(UUID uuid) {
        int lvl = getUpgradeLevel(uuid);
        if (lvl == 2 && isUpgradeDisabled(uuid, 2)) {
            lvl = 1;
        }
        if (lvl == 1 && isUpgradeDisabled(uuid, 1)) {
            lvl = 0;
        }
        return lvl;
    }

    public boolean isLostOnDeath(UUID uuid) {
        return dataConfig.getBoolean("lost_on_death." + uuid.toString(), false);
    }

    public void setLostOnDeath(UUID uuid, boolean lost) {
        dataConfig.set("lost_on_death." + uuid.toString(), lost);
        saveDataConfig();
    }

    // ===== ITEM CREATION & LIVE UPDATES =====

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

        int upgrade = getEffectiveUpgradeLevel(ownerUuid);
        String subTitle = upgrade == 2 ? ChatColor.LIGHT_PURPLE + " [Unverlierbar]" : (upgrade == 1 ? ChatColor.AQUA + " [Unzerstörbar]" : "");

        meta.setDisplayName(ChatColor.GOLD + "Rucksack" + subTitle);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Linksklick im Inv: " + ChatColor.YELLOW + "Item bewegen");
        lore.add(ChatColor.GRAY + "Rechtsklick / Q im Inv: " + ChatColor.YELLOW + "Rucksack öffnen / Schließen");
        lore.add(ChatColor.GRAY + "Shift + Rechtsklick: " + ChatColor.YELLOW + "Als Kiste platzieren");
        lore.add(ChatColor.GRAY + "Linksklick auf Kiste: " + ChatColor.YELLOW + "Sofort abbauen");
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(backpackItemKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(backpackOwnerKey, PersistentDataType.STRING, ownerUuid.toString());

        head.setItemMeta(meta);
        return head;
    }

    public void updateBackpackItemsInInventory(Player p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack item = p.getInventory().getItem(i);
            if (isBackpackItem(item)) {
                p.getInventory().setItem(i, createBackpackItem(p.getUniqueId()));
            }
        }
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

    private void clearDroppedBackpackItems(UUID ownerUuid) {
        String targetUuid = ownerUuid.toString();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Item.class)) {
                Item itemEntity = (Item) entity;
                ItemStack stack = itemEntity.getItemStack();
                if (isBackpackItem(stack)) {
                    String owner = getBackpackOwner(stack);
                    if (owner == null || owner.equals(targetUuid)) {
                        itemEntity.remove();
                    }
                }
            }
        }
    }

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

    public void clearBackpackContents(UUID ownerUuid) {
        Inventory inv = getBackpackInventory(ownerUuid);
        inv.clear();
        saveBackpack(ownerUuid, inv);

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.RED + "Dein Rucksack wurde zerstört! Da du kein Upgrade 1 hast, wurden alle Inhalte darin GELÖSCHT.");
            owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
    }

    // ===== CUSTOM INVSEE GUI (DOPPELKISTE) =====

    public Inventory createCustomInvseeGUI(Player target) {
        Inventory gui = Bukkit.createInventory(null, 54, ADMIN_INV_PREFIX + target.getName());
        updateInvseeGUIContents(gui, target);
        return gui;
    }

    private void updateInvseeGUIContents(Inventory gui, Player target) {
        ItemStack[] contents = target.getInventory().getContents();

        for (int i = 0; i < 36 && i < contents.length; i++) {
            gui.setItem(i, contents[i] != null ? contents[i].clone() : null);
        }

        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(ChatColor.BLACK + " ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 36; i < 54; i++) {
            gui.setItem(i, glass);
        }

        ItemStack[] armor = target.getInventory().getArmorContents();
        gui.setItem(36, armor[3] != null ? armor[3].clone() : null); // Helm
        gui.setItem(37, armor[2] != null ? armor[2].clone() : null); // Brustplatte
        gui.setItem(38, armor[1] != null ? armor[1].clone() : null); // Hose
        gui.setItem(39, armor[0] != null ? armor[0].clone() : null); // Schuhe

        ItemStack offhand = target.getInventory().getItemInOffHand();
        gui.setItem(44, (offhand != null && offhand.getType() != Material.AIR) ? offhand.clone() : null);
    }

    private void syncInvseeToPlayer(Inventory gui, Player target) {
        for (int i = 0; i < 36; i++) {
            target.getInventory().setItem(i, gui.getItem(i));
        }

        ItemStack[] armor = new ItemStack[4];
        armor[3] = gui.getItem(36); // Helm
        armor[2] = gui.getItem(37); // Chestplate
        armor[1] = gui.getItem(38); // Hose
        armor[0] = gui.getItem(39); // Schuhe
        target.getInventory().setArmorContents(armor);

        target.getInventory().setItemInOffHand(gui.getItem(44));
        target.updateInventory();
    }

    private boolean isValidArmorForSlot(int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        String type = item.getType().name();

        switch (slot) {
            case 36: // Helm
                return type.endsWith("_HELMET") || type.endsWith("_HEAD") || type.endsWith("_SKULL") || type.equals("TURTLE_HELMET");
            case 37: // Brustplatte
                return type.endsWith("_CHESTPLATE") || type.equals("ELYTRA");
            case 38: // Hose
                return type.endsWith("_LEGGINGS");
            case 39: // Schuhe
                return type.endsWith("_BOOTS");
            default:
                return true;
        }
    }

    // ===== SHOP GUI =====

    public void openShopGUI(Player p) {
        Inventory shop = Bukkit.createInventory(null, 27, SHOP_TITLE);
        int level = getUpgradeLevel(p.getUniqueId());

        int cost1 = getConfig().getInt("upgrade1_cost_diamonds", 128);
        int cost2 = getConfig().getInt("upgrade2_cost_diamonds", 256);

        boolean dis1 = isUpgradeDisabled(p.getUniqueId(), 1);
        boolean dis2 = isUpgradeDisabled(p.getUniqueId(), 2);

        ItemStack up1 = new ItemStack(level >= 1 ? (dis1 ? Material.RED_DYE : Material.LIME_DYE) : Material.ANVIL);
        ItemMeta meta1 = up1.getItemMeta();
        if (meta1 != null) {
            meta1.setDisplayName(ChatColor.AQUA + "Upgrade 1: Unzerstörbar");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Schützt dein Backpack vor Lava, Feuer,");
            lore.add(ChatColor.GRAY + "Explosionen und Despawning!");
            lore.add("");
            lore.add(ChatColor.GRAY + "Kosten: " + ChatColor.GOLD + cost1 + " Diamanten");
            if (level >= 1) {
                lore.add(dis1 ? ChatColor.RED + "✖ Deaktiviert (Klicken zum Aktivieren)" : ChatColor.GREEN + "✔ Aktiv (Klicken zum Deaktivieren)");
            } else {
                lore.add(ChatColor.YELLOW + "Klicke zum Freischalten");
            }
            meta1.setLore(lore);
            up1.setItemMeta(meta1);
        }
        shop.setItem(11, up1);

        ItemStack up2 = new ItemStack(level >= 2 ? (dis2 ? Material.RED_DYE : Material.NETHER_STAR) : Material.BARRIER);
        ItemMeta meta2 = up2.getItemMeta();
        if (meta2 != null) {
            meta2.setDisplayName(ChatColor.LIGHT_PURPLE + "Upgrade 2: Unverlierbar");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Das Backpack droppt nicht mehr beim Tod,");
            lore.add(ChatColor.GRAY + "sondern bleibt sicher in deinen Händen!");
            lore.add("");
            lore.add(ChatColor.GRAY + "Kosten: " + ChatColor.GOLD + cost2 + " Diamanten");
            if (level >= 2) {
                lore.add(dis2 ? ChatColor.RED + "✖ Deaktiviert (Klicken zum Aktivieren)" : ChatColor.GREEN + "✔ Aktiv (Klicken zum Deaktivieren)");
            } else {
                lore.add(level < 1 ? ChatColor.RED + "🔒 Benötigt Upgrade 1" : ChatColor.YELLOW + "Klicke zum Freischalten");
            }
            meta2.setLore(lore);
            up2.setItemMeta(meta2);
        }
        shop.setItem(15, up2);

        p.openInventory(shop);
    }

    private boolean removeDiamonds(Player p, int amount) {
        int count = 0;
        for (ItemStack is : p.getInventory().getContents()) {
            if (is != null && is.getType() == Material.DIAMOND) {
                count += is.getAmount();
            }
        }
        if (count < amount) return false;

        int left = amount;
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack is = p.getInventory().getItem(i);
            if (is != null && is.getType() == Material.DIAMOND) {
                if (is.getAmount() <= left) {
                    left -= is.getAmount();
                    p.getInventory().setItem(i, null);
                } else {
                    is.setAmount(is.getAmount() - left);
                    left = 0;
                }
                if (left <= 0) break;
            }
        }
        return true;
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

    // ===== COMMANDS =====

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur für Spieler.");
            return true;
        }

        Player p = (Player) sender;
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("backpack") || cmd.equals("bp") || cmd.equals("bpsee")) {
            if (args.length == 0) {
                if (isLostOnDeath(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "Dein Rucksack ist am Todesort verloren gegangen! Du musst ihn erst aufsammeln.");
                    return true;
                }

                boolean isHidden = dataConfig.getBoolean("hidden." + p.getUniqueId(), false);
                boolean inInv = hasBackpackInInventory(p);

                if (!inInv && !isHidden) {
                    p.sendMessage(ChatColor.RED + "Du hast deinen Rucksack weder im Inventar noch versteckt! (/bp show)");
                    return true;
                }

                Inventory inv = getBackpackInventory(p.getUniqueId());
                p.openInventory(inv);
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                return true;
            }

            String action = args[0].toLowerCase();

            if (action.equals("menu")) {
                openShopGUI(p);
                return true;
            }

            if (action.equals("hide")) {
                if (isLostOnDeath(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "Dein Rucksack ist verloren! Sammle ihn erst an deinem Todesort auf.");
                    return true;
                }
                if (!hasBackpackInInventory(p)) {
                    p.sendMessage(ChatColor.RED + "Du musst dein Backpack-Item im Inventar haben!");
                    return true;
                }

                removeAllBackpackItems(p);
                dataConfig.set("hidden." + p.getUniqueId(), true);
                saveDataConfig();

                p.sendMessage(ChatColor.GREEN + "Rucksack-Item versteckt. Du kannst /bp weiterhin nutzen.");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
                return true;
            }

            if (action.equals("show")) {
                if (isLostOnDeath(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "Dein Rucksack ist verloren! Sammle ihn erst an deinem Todesort auf.");
                    return true;
                }

                if (hasBackpackInInventory(p)) {
                    p.sendMessage(ChatColor.RED + "Dein Rucksack ist bereits in deinem Inventar!");
                    return true;
                }

                if (p.getInventory().firstEmpty() == -1) {
                    p.sendMessage(ChatColor.YELLOW + "Dein Inventar ist voll!");
                    return true;
                }

                dataConfig.set("hidden." + p.getUniqueId(), false);
                saveDataConfig();

                p.getInventory().addItem(createBackpackItem(p.getUniqueId()));
                p.sendMessage(ChatColor.GREEN + "Rucksack-Item wieder ins Inventar gelegt!");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                return true;
            }

            if (action.equals("confirmrecover")) {
                if (!pendingConfirmations.contains(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "Du hast keine ausstehende Recover-Bestätigung.");
                    return true;
                }

                pendingConfirmations.remove(p.getUniqueId());
                clearDroppedBackpackItems(p.getUniqueId());
                destroyPlacedBackpackBlocks(p.getUniqueId());
                removeAllBackpackItems(p);

                clearBackpackContents(p.getUniqueId());

                setLostOnDeath(p.getUniqueId(), false);
                dataConfig.set("hidden." + p.getUniqueId(), true);
                saveDataConfig();

                p.sendMessage(ChatColor.YELLOW + "Dein Rucksack wurde wiederhergestellt & versteckt. Der Inhalt wurde GELÖSCHT.");
                p.sendMessage(ChatColor.GREEN + "Nutze /bp zum Öffnen oder /bp show, um das Item ins Inventar zu holen.");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
                return true;
            }

            if (action.equals("recover")) {
                if (isLostOnDeath(p.getUniqueId())) {
                    pendingConfirmations.add(p.getUniqueId());

                    p.sendMessage(ChatColor.RED + "========================================");
                    p.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "WARNUNG:");
                    p.sendMessage(ChatColor.RED + "Dein Rucksack liegt noch am Todesort.");
                    p.sendMessage(ChatColor.RED + "Wenn du ihn jetzt recoverst, werden ALLE INHALTE darin GELÖSCHT!");

                    TextComponent confirmButton = new TextComponent("[Ja, ich bestätige]");
                    confirmButton.setColor(ChatColor.RED);
                    confirmButton.setBold(true);
                    confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bp confirmrecover"));
                    confirmButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Klicke hier, um den Inhalt zu löschen & Rucksack zu recovern").create()));

                    p.spigot().sendMessage(confirmButton);
                    p.sendMessage(ChatColor.RED + "========================================");
                    return true;
                }

                long cooldownSec = getConfig().getLong("recover_cooldown_seconds", 300);
                long lastUsed = recoverCooldowns.getOrDefault(p.getUniqueId(), 0L);
                long now = System.currentTimeMillis();

                if ((now - lastUsed) < (cooldownSec * 1000)) {
                    long remaining = (cooldownSec * 1000 - (now - lastUsed)) / 1000;
                    p.sendMessage(ChatColor.RED + "Du musst noch " + remaining + " Sekunden warten, bevor du wieder recovern kannst!");
                    return true;
                }

                recoverCooldowns.put(p.getUniqueId(), now);

                clearDroppedBackpackItems(p.getUniqueId());
                destroyPlacedBackpackBlocks(p.getUniqueId());
                removeAllBackpackItems(p);

                dataConfig.set("hidden." + p.getUniqueId(), true);
                saveDataConfig();

                p.sendMessage(ChatColor.GREEN + "Dein Rucksack wurde im versteckten Zustand wiederhergestellt! Nutze /bp show.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                return true;
            }

            if (p.hasPermission("backpack.admin")) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                if (target.hasPlayedBefore() || target.isOnline()) {
                    Inventory targetBp = getBackpackInventory(target.getUniqueId());
                    p.openInventory(targetBp);
                    p.sendMessage(ChatColor.GOLD + "Öffne den Rucksack von " + target.getName() + "...");
                    return true;
                }
            }

            p.sendMessage(ChatColor.RED + "Benutzung: /bp <menu|show|hide|recover|spieler>");
            return true;
        }

        if (cmd.equals("invsee")) {
            if (!p.hasPermission("backpack.admin")) {
                p.sendMessage(ChatColor.RED + "Keine Rechte.");
                return true;
            }
            if (args.length == 0) {
                p.sendMessage(ChatColor.RED + "Benutzung: /invsee <spieler>");
                return true;
            }

            Player targetOnline = Bukkit.getPlayer(args[0]);
            if (targetOnline != null) {
                p.openInventory(createCustomInvseeGUI(targetOnline));
                p.sendMessage(ChatColor.GREEN + "Öffne Inventar von " + targetOnline.getName());
            } else {
                p.sendMessage(ChatColor.RED + "Spieler ist nicht online.");
            }
            return true;
        }

        if (cmd.equals("enderchest") || cmd.equals("ec")) {
            if (args.length == 0) {
                p.openInventory(p.getEnderChest());
                return true;
            }

            if (!p.hasPermission("backpack.admin")) {
                p.sendMessage(ChatColor.RED + "Du hast keine Rechte, die Enderchest anderer Spieler zu sehen.");
                return true;
            }

            Player targetOnline = Bukkit.getPlayer(args[0]);
            if (targetOnline != null) {
                p.openInventory(targetOnline.getEnderChest());
                p.sendMessage(ChatColor.GREEN + "Öffne Enderchest von " + targetOnline.getName());
            } else {
                p.sendMessage(ChatColor.RED + "Spieler ist nicht online.");
            }
            return true;
        }

        if (cmd.equals("bpadmin")) {
            if (!p.hasPermission("backpack.admin")) {
                p.sendMessage(ChatColor.RED + "Keine Rechte.");
                return true;
            }

            if (args.length < 2) {
                p.sendMessage(ChatColor.RED + "Benutzung: /bpadmin <setcooldown|setupgrade1cost|setupgrade2cost> <wert>");
                return true;
            }

            try {
                int val = Integer.parseInt(args[1]);
                String sub = args[0].toLowerCase();

                if (sub.equals("setcooldown")) {
                    getConfig().set("recover_cooldown_seconds", val);
                    saveConfig();
                    p.sendMessage(ChatColor.GREEN + "Recover-Cooldown auf " + val + " Sekunden gesetzt!");
                } else if (sub.equals("setupgrade1cost")) {
                    getConfig().set("upgrade1_cost_diamonds", val);
                    saveConfig();
                    p.sendMessage(ChatColor.GREEN + "Kosten für Upgrade 1 auf " + val + " Diamanten gesetzt!");
                } else if (sub.equals("setupgrade2cost")) {
                    getConfig().set("upgrade2_cost_diamonds", val);
                    saveConfig();
                    p.sendMessage(ChatColor.GREEN + "Kosten für Upgrade 2 auf " + val + " Diamanten gesetzt!");
                }
            } catch (NumberFormatException e) {
                p.sendMessage(ChatColor.RED + "Bitte gib eine gültige Zahl ein.");
            }
            return true;
        }

        return true;
    }

    // ===== EVENTS =====

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item)) return;
        Item itemEntity = (Item) event.getEntity();
        ItemStack stack = itemEntity.getItemStack();

        if (isBackpackItem(stack)) {
            String owner = getBackpackOwner(stack);
            if (owner != null) {
                UUID ownerUuid = UUID.fromString(owner);
                if (getEffectiveUpgradeLevel(ownerUuid) >= 1) {
                    event.setCancelled(true);
                } else {
                    clearBackpackContents(ownerUuid);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDespawn(ItemDespawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();
        if (isBackpackItem(stack)) {
            String owner = getBackpackOwner(stack);
            if (owner != null) {
                UUID ownerUuid = UUID.fromString(owner);
                if (getEffectiveUpgradeLevel(ownerUuid) >= 1) {
                    event.setCancelled(true);
                } else {
                    clearBackpackContents(ownerUuid);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        int upgradeLevel = getEffectiveUpgradeLevel(p.getUniqueId());

        if (upgradeLevel >= 2) {
            event.getDrops().removeIf(this::isBackpackItem);
            dataConfig.set("hidden." + p.getUniqueId(), true);
            saveDataConfig();
            p.sendMessage(ChatColor.LIGHT_PURPLE + "Dein Rucksack ist dank Upgrade 2 sicher bei dir geblieben!");
            return;
        }

        boolean hadBackpack = hasBackpackInInventory(p) || dataConfig.getBoolean("hidden." + p.getUniqueId(), false);

        if (hadBackpack) {
            dataConfig.set("hidden." + p.getUniqueId(), false);
            setLostOnDeath(p.getUniqueId(), true);

            if (!hasBackpackInInventory(p)) {
                event.getDrops().add(createBackpackItem(p.getUniqueId()));
            }

            p.sendMessage(ChatColor.RED + "Dein Rucksack ist an deinem Todesort gedroppt!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        ItemStack stack = event.getItem().getItemStack();

        if (isBackpackItem(stack)) {
            String owner = getBackpackOwner(stack);
            if (owner != null && owner.equals(p.getUniqueId().toString())) {
                if (isLostOnDeath(p.getUniqueId())) {
                    setLostOnDeath(p.getUniqueId(), false);
                    p.sendMessage(ChatColor.GREEN + "Du hast deinen Rucksack wieder aufgesammelt! Alle Funktionen sind freigeschaltet.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        // 1. RÜSTUNGSSLOT-PRÜFUNG IM CUSTOM INVSEE
        if (event.getView().getTitle().startsWith(ADMIN_INV_PREFIX)) {
            int slot = event.getRawSlot();

            // Blockiert Klicks auf Glas-Panes
            if (slot >= 36 && slot < 54 && slot != 44 && slot > 39) {
                event.setCancelled(true);
                return;
            }

            // Prüfe Rüstungsslots 36-39
            if (slot >= 36 && slot <= 39) {
                ItemStack cursor = event.getCursor();
                if (!isValidArmorForSlot(slot, cursor)) {
                    event.setCancelled(true);
                    p.sendMessage(ChatColor.RED + "Dieser Gegenstand passt nicht in diesen Rüstungsslot!");
                    return;
                }
            }

            String targetName = event.getView().getTitle().replace(ADMIN_INV_PREFIX, "");
            Player target = Bukkit.getPlayer(targetName);

            if (target != null) {
                Bukkit.getScheduler().runTask(this, () -> {
                    syncInvseeToPlayer(event.getInventory(), target);
                });
            }
            return;
        }

        // 2. SHOP GUI HANDLER
        if (event.getView().getTitle().equals(SHOP_TITLE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            int level = getUpgradeLevel(p.getUniqueId());

            int cost1 = getConfig().getInt("upgrade1_cost_diamonds", 128);
            int cost2 = getConfig().getInt("upgrade2_cost_diamonds", 256);

            if (slot == 11) {
                if (level >= 1) {
                    toggleUpgradeDisabled(p.getUniqueId(), 1);
                    boolean dis = isUpgradeDisabled(p.getUniqueId(), 1);
                    p.sendMessage(dis ? ChatColor.YELLOW + "Upgrade 1 wurde DEAKTIVIERT." : ChatColor.GREEN + "Upgrade 1 wurde AKTIVIERT.");
                    updateBackpackItemsInInventory(p);
                    openShopGUI(p);
                    return;
                }
                if (removeDiamonds(p, cost1)) {
                    setUpgradeLevel(p.getUniqueId(), 1);
                    p.sendMessage(ChatColor.GREEN + "Glückwunsch! Du hast Upgrade 1 (Unzerstörbar) gekauft!");
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    updateBackpackItemsInInventory(p);
                    openShopGUI(p);
                } else {
                    p.sendMessage(ChatColor.RED + "Du benötigst " + cost1 + " Diamanten in deinem Inventar!");
                }
            } else if (slot == 15) {
                if (level >= 2) {
                    toggleUpgradeDisabled(p.getUniqueId(), 2);
                    boolean dis = isUpgradeDisabled(p.getUniqueId(), 2);
                    p.sendMessage(dis ? ChatColor.YELLOW + "Upgrade 2 wurde DEAKTIVIERT." : ChatColor.LIGHT_PURPLE + "Upgrade 2 wurde AKTIVIERT.");
                    updateBackpackItemsInInventory(p);
                    openShopGUI(p);
                    return;
                }
                if (level < 1) {
                    p.sendMessage(ChatColor.RED + "Du musst zuerst Upgrade 1 freischalten!");
                    return;
                }
                if (removeDiamonds(p, cost2)) {
                    setUpgradeLevel(p.getUniqueId(), 2);
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "Glückwunsch! Du hast Upgrade 2 (Unverlierbar) gekauft!");
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    updateBackpackItemsInInventory(p);
                    openShopGUI(p);
                } else {
                    p.sendMessage(ChatColor.RED + "Du benötigst " + cost2 + " Diamanten in deinem Inventar!");
                }
            }
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (event.getSlotType() == InventoryType.SlotType.ARMOR && event.getRawSlot() == 5) {
            if (isBackpackItem(cursor)) {
                event.setCancelled(true);
                p.sendMessage(ChatColor.RED + "Du kannst den Rucksack nicht auf dem Kopf tragen!");
                return;
            }
        }

        if (isBackpackItem(current)) {
            ClickType click = event.getClick();

            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }

            if (click == ClickType.LEFT) {
                return; 
            }

            if (click == ClickType.RIGHT || click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                event.setCancelled(true);

                // TOGGLE-LOGIK: Wenn Backpack-GUI bereits offen ist -> Schließen
                if (event.getView().getTitle().equals(GUI_TITLE)) {
                    p.closeInventory();
                    p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 0.8f);
                    return;
                }

                if (isLostOnDeath(p.getUniqueId())) {
                    p.sendMessage(ChatColor.RED + "Dein Rucksack ist gesperrt!");
                    return;
                }

                Bukkit.getScheduler().runTask(this, () -> {
                    p.closeInventory();
                    Inventory inv = getBackpackInventory(p.getUniqueId());
                    p.openInventory(inv);
                    p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

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

        if (event.getAction() == Action.RIGHT_CLICK_AIR || (event.getAction() == Action.RIGHT_CLICK_BLOCK && !p.isSneaking())) {
            event.setCancelled(true);
            if (isLostOnDeath(p.getUniqueId())) {
                p.sendMessage(ChatColor.RED + "Dein Rucksack ist gesperrt, bis du ihn am Todesort aufgesammelt hast!");
                return;
            }
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

        if (isLostOnDeath(p.getUniqueId())) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.RED + "Dein Rucksack ist gesperrt!");
            return;
        }

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
        } else if (event.getView().getTitle().startsWith(ADMIN_BP_PREFIX)) {
            String targetName = event.getView().getTitle().replace(ADMIN_BP_PREFIX, "");
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target.hasPlayedBefore() || target.isOnline()) {
                saveBackpack(target.getUniqueId(), event.getInventory());
                ((Player) event.getPlayer()).sendMessage(ChatColor.GREEN + "Änderungen am Rucksack von " + targetName + " gespeichert!");
            }
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
        if (command.getName().equalsIgnoreCase("backpack") || command.getName().equalsIgnoreCase("bp")) {
            if (args.length == 1) {
                list.add("menu");
                list.add("show");
                list.add("hide");
                list.add("recover");
                if (sender.hasPermission("backpack.admin")) {
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        list.add(target.getName());
                    }
                }
            }
        } else if (command.getName().equalsIgnoreCase("invsee") || command.getName().equalsIgnoreCase("enderchest") || command.getName().equalsIgnoreCase("ec")) {
            if (args.length == 1) {
                for (Player target : Bukkit.getOnlinePlayers()) {
                    list.add(target.getName());
                }
            }
        } else if (command.getName().equalsIgnoreCase("bpadmin") && args.length == 1) {
            list.add("setcooldown");
            list.add("setupgrade1cost");
            list.add("setupgrade2cost");
        }
        return list;
    }
}
